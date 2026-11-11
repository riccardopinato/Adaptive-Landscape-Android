package it.riccardopinato.adaptivelandscape

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.app.AlertDialog
import android.app.WallpaperManager
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.EditText
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.SearchView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.ceil
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import it.riccardopinato.adaptive_landscape.R
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * User-chosen native Android Home, not a background icon scanner.
 *
 * Android only exposes precise icon positions to the launcher that owns those
 * views. This Activity owns its displayed icons and publishes their positions
 * to the existing WallpaperService via a separate, volatile scene contract.
 *
 * Limitations: no automatic migration from other launcher layouts or OEM
 * shortcuts. Widget hosting, user-curated folders and dock are locally owned;
 * work/private profile behavior still requires per-device verification.
 * NEVER replace the default Home without explicit Android system consent.
 */
class AdaptiveHomeActivity : Activity() {
    private data class PlacedIcon(val id: String, var cell: Int)
    private data class PlacedFolder(
        val id: String, var name: String, var cell: Int,
        val members: MutableList<String>
    )
    private data class PlacedWidget(
        val widgetId: Int, var cell: Int, var spanX: Int, var spanY: Int
    )
    private companion object {
        const val APP_WIDGET_HOST_ID = 1453
        const val REQUEST_BIND_WIDGET = 1454
        const val REQUEST_CONFIGURE_WIDGET = 1455
    }


    private val prefs by lazy {
        getSharedPreferences("adaptive_landscape", Context.MODE_PRIVATE)
    }
    private val launcherApps by lazy {
        getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    }
    private val handler = Handler(Looper.getMainLooper())
    private val dp by lazy { resources.displayMetrics.density }
    private val placed = ArrayList<PlacedIcon>()
    private val folders = ArrayList<PlacedFolder>()
    // Dock order is independent of every page and never auto-seeded.
    private val dock = ArrayList<String>()
    private val views = LinkedHashMap<String, View>()
    private val widgetViews = LinkedHashMap<Int, View>()
    private val placedWidgets = ArrayList<PlacedWidget>()
    private val widgetManager by lazy { AppWidgetManager.getInstance(this) }
    private val widgetHost by lazy { AppWidgetHost(this, APP_WIDGET_HOST_ID) }
    private var pendingWidget: PlacedWidget? = null
    private var pendingInfo: AppWidgetProviderInfo? = null
    private var available: List<LauncherActivityInfo> = emptyList()
    private lateinit var canvas: FrameLayout
    private var draggingId: String? = null
    private var lastPublishedAt = 0L
    private val publishPending = Runnable { publishHomePositions() }
    private var lastWidth = 0
    private var lastHeight = 0
    // Never modify FrameLayout children while Android is measuring or
    // dispatching the touch that ended an icon drag.
    private fun renderHomeNextFrame() {
        if (!::canvas.isInitialized) return
        canvas.postOnAnimation {
            if (!isFinishing && !isDestroyed) renderHome()
        }
    }
    private var callbackRegistered = false
    private val packageCallback = object : LauncherApps.Callback() {
        override fun onPackageAdded(packageName: String, user: UserHandle) = refreshPackages()
        override fun onPackageRemoved(packageName: String, user: UserHandle) = refreshPackages()
        override fun onPackageChanged(packageName: String, user: UserHandle) = refreshPackages()
        override fun onPackagesAvailable(names: Array<out String>, user: UserHandle,
                                         replacing: Boolean) = refreshPackages()
        override fun onPackagesUnavailable(names: Array<out String>, user: UserHandle,
                                           replacing: Boolean) = refreshPackages()
    }

    private fun refreshPackages() {
        handler.post {
            if (!isFinishing && !isDestroyed) {
                loadLaunchableApps()
                restoreSavedLayout()
                renderHome()
            }
        }
    }

    private val columns = 5
    private val rows = 8
    // Reserve the bottom band for an actual user-curated dock and controls.
    // Cell numbers remain 5x8 for non-destructive v1 layout migration.
    private val topFraction = 0.085
    private val spanFraction = 0.605
    private val dockSlots = 4
    private val pageSlots = columns * rows
    private val maxPages = 3
    private val maxSlots = pageSlots * maxPages
    private var currentPage = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentPage = prefs.getInt("auto_home_page_v1", 0).coerceIn(0, maxPages - 1)
        restoreWidgets()
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        canvas = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            clipChildren = false
            clipToPadding = false
        }
        setContentView(canvas)
        var swipeStartX = 0f
        canvas.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    swipeStartX = event.x
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val change = event.x - swipeStartX
                    if (abs(change) > 72 * dp) {
                        switchPage(currentPage + if (change < 0) 1 else -1)
                    }
                    true
                }
                else -> true
            }
        }
        // Subscribe to launcher package changes; do not poll installed apps.
        try {
            launcherApps.registerCallback(packageCallback, handler)
            callbackRegistered = true
        } catch (_: RuntimeException) {
            // A refresh on resume still supports a functioning Home.
        }
        canvas.viewTreeObserver.addOnGlobalLayoutListener {
            if (canvas.width > 0 && canvas.height > 0 &&
                (canvas.width != lastWidth || canvas.height != lastHeight)) {
                lastWidth = canvas.width
                lastHeight = canvas.height
                renderHomeNextFrame()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!isSelectedHome()) {
            // When the system selects another Home, this Activity must no
            // longer be shown as an obsolete launcher task.
            finish()
            return
        }
        try { widgetHost.startListening() } catch (_: RuntimeException) { }
        restoreWidgets()
        loadLaunchableApps()
        restoreSavedLayout()
        canvas.post { renderHome() }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!isSelectedHome()) {
            finish()
            return
        }
        renderHomeNextFrame()
    }

    override fun onPause() {
        handler.removeCallbacks(publishPending)
        if (isSelectedHome()) publishHomePositions()
        try { widgetHost.stopListening() } catch (_: RuntimeException) { }
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacks(publishPending)
        if (callbackRegistered) launcherApps.unregisterCallback(packageCallback)
        super.onDestroy()
    }

    private fun isSelectedHome(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roles = getSystemService(Context.ROLE_SERVICE) as? RoleManager
            return roles?.isRoleHeld(RoleManager.ROLE_HOME) == true
        }
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return packageManager.resolveActivity(home, 0)?.activityInfo?.packageName == packageName
    }

    private fun loadLaunchableApps() {
        available = try {
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                launcherApps.profiles else listOf(Process.myUserHandle())).flatMap { user ->
                try { launcherApps.getActivityList(null, user) }
                catch (_: SecurityException) { emptyList() }
            }
                .filter { it.componentName.packageName != packageName }
                .distinctBy { it.componentName.flattenToString() }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label.toString() })
        } catch (_: SecurityException) {
            emptyList()
        } catch (_: RuntimeException) {
            emptyList()
        }
    }

    private fun restoreSavedLayout() {
        val known = available.associateBy { it.componentName.flattenToString() }
        placed.clear()
        folders.clear()
        dock.clear()
        val usedApps = HashSet<String>()
        val usedCells = HashSet<Int>()
        // Existing auto_home_icons_v1 is read unchanged. Older users never
        // lose the layout previously saved on their device.
        try {
            val saved = JSONArray(prefs.getString("auto_home_icons_v1", "[]") ?: "[]")
            for (i in 0 until minOf(saved.length(), maxSlots)) {
                val item = saved.optJSONObject(i) ?: continue
                val id = item.optString("id")
                val cell = item.optInt("cell", -1)
                if (known.containsKey(id) && cell in 0 until maxSlots &&
                    cell !in usedCells && placedWidgets.none { cell in widgetCells(it) } &&
                    usedApps.add(id)) {
                    usedCells.add(cell)
                    placed.add(PlacedIcon(id, cell))
                }
            }
        } catch (_: Exception) { /* A malformed legacy layout stays local. */ }
        try {
            val raw = JSONArray(prefs.getString("auto_home_folders_v1", "[]") ?: "[]")
            for (i in 0 until minOf(raw.length(), 60)) {
                val o = raw.optJSONObject(i) ?: continue
                val id = o.optString("id").takeIf { it.startsWith("folder:") } ?: continue
                val cell = o.optInt("cell", -1)
                if (cell !in 0 until maxSlots || !usedCells.add(cell) ||
                    placedWidgets.any { cell in widgetCells(it) } ||
                    folders.any { it.id == id }) continue
                val members = mutableListOf<String>()
                val items = o.optJSONArray("members") ?: JSONArray()
                for (j in 0 until minOf(items.length(), 24)) {
                    val member = items.optString(j)
                    if (known.containsKey(member) && usedApps.add(member)) members.add(member)
                }
                if (members.isNotEmpty()) {
                    folders.add(PlacedFolder(id, o.optString("name").take(48)
                        .ifBlank { getString(R.string.auto_home_folder) }, cell, members))
                } else usedCells.remove(cell)
            }
        } catch (_: Exception) { /* Never discard other valid sections. */ }
        try {
            val raw = JSONArray(prefs.getString("auto_home_dock_v1", "[]") ?: "[]")
            for (i in 0 until minOf(raw.length(), dockSlots)) {
                val id = raw.optString(i)
                if (known.containsKey(id) && usedApps.add(id)) dock.add(id)
            }
        } catch (_: Exception) { /* Dock is optional. */ }
    }

    private fun saveLayout() {
        val icons = JSONArray()
        for (item in placed) icons.put(JSONObject()
            .put("id", item.id).put("cell", item.cell))
        val groups = JSONArray()
        for (folder in folders) groups.put(JSONObject()
            .put("id", folder.id).put("name", folder.name)
            .put("cell", folder.cell).put("members", JSONArray(folder.members)))
        // Save every Home section together to avoid partial drag/folder state.
        prefs.edit()
            .putString("auto_home_icons_v1", icons.toString())
            .putString("auto_home_folders_v1", groups.toString())
            .putString("auto_home_dock_v1", JSONArray(dock).toString())
            .apply()
    }

    private fun cellCenter(cell: Int): Pair<Float, Float> {
        val localCell = cell % pageSlots
        val col = localCell % columns
        val row = localCell / columns
        return Pair((col + 0.5f) / columns,
            (topFraction + (row + 0.5) * spanFraction / rows).toFloat())
    }

    private fun iconWidth(): Int = (canvas.width / columns.toFloat() * 0.87f).roundToInt()
    private fun iconHeight(): Int =
        (canvas.height * spanFraction / rows * 0.88f).roundToInt()

    private fun leftFor(cell: Int): Float =
        cellCenter(cell).first * canvas.width - iconWidth() * 0.5f

    private fun topFor(cell: Int): Float =
        cellCenter(cell).second * canvas.height - iconHeight() * 0.5f

    private fun renderHome() {
        if (!::canvas.isInitialized || canvas.width < 100 || canvas.height < 100) return
        canvas.removeAllViews()
        views.clear()
        widgetViews.clear()
        val byId = available.associateBy { it.componentName.flattenToString() }
        for (item in placed) {
            if (item.cell / pageSlots != currentPage) continue
            val info = byId[item.id] ?: continue
            val tile = makeTile(info)
            addHomeTile(tile, item.id, leftFor(item.cell), topFor(item.cell)) {
                startApp(info)
            }
        }
        for (folder in folders) {
            if (folder.cell / pageSlots != currentPage) continue
            val tile = makeFolderTile(folder, byId)
            addHomeTile(tile, folder.id, leftFor(folder.cell), topFor(folder.cell)) {
                openFolder(folder)
            }
        }
        renderWidgets()
        for ((index, id) in dock.withIndex()) {
            val info = byId[id] ?: continue
            val tile = makeTile(info)
            val x = (index + 0.5f) * canvas.width / dockSlots - iconWidth() / 2f
            val y = canvas.height - (140 * dp)
            addHomeTile(tile, id, x, y) { startApp(info) }
        }
        renderChrome()
        requestPublish()
    }

    private fun addHomeTile(tile: View, id: String, x: Float, y: Float,
                            onLaunch: () -> Unit) {
        canvas.addView(tile, FrameLayout.LayoutParams(iconWidth(), iconHeight()))
        tile.x = x
        tile.y = y
        views[id] = tile
        tile.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            if (v.parent === canvas) requestPublish()
        }
        attachTouch(tile, id, onLaunch)
    }

    private fun makeFolderTile(folder: PlacedFolder,
                               known: Map<String, LauncherActivityInfo>): View {
        val vertical = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        val preview = GridLayout(this).apply {
            rowCount = 2
            columnCount = 2
            background = GradientDrawable().apply {
                cornerRadius = 13 * dp
                setColor(Color.argb(140, 235, 245, 246))
            }
        }
        val mini = (21 * dp).roundToInt()
        for (member in folder.members.take(4)) {
            val icon = ImageView(this).apply {
                setImageDrawable(known[member]?.getIcon(0))
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            preview.addView(icon, GridLayout.LayoutParams().apply {
                width = mini
                height = mini
                setMargins(1, 1, 1, 1)
            })
        }
        vertical.addView(preview)
        val label = TextView(this).apply {
            text = folder.name
            gravity = Gravity.CENTER
            textSize = 11f
            maxLines = 1
            setTextColor(Color.WHITE)
            setShadowLayer(3.5f, 0f, 1f, Color.BLACK)
        }
        vertical.addView(label, LinearLayout.LayoutParams(-1, -2))
        vertical.contentDescription = "${folder.name}, ${getString(R.string.auto_home_folder)}"
        vertical.isClickable = true
        vertical.isFocusable = true
        vertical.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        return vertical
    }

    private fun makeTile(info: LauncherActivityInfo): View {
        val vertical = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding((3 * dp).roundToInt(), 0, (3 * dp).roundToInt(), 0)
        }
        val image = ImageView(this).apply {
            setImageDrawable(info.getIcon(0))
            scaleType = ImageView.ScaleType.FIT_CENTER
            isFocusable = false
        }
        val iconSize = (48 * dp).roundToInt().coerceAtMost(iconHeight() * 2 / 3)
        vertical.addView(image, LinearLayout.LayoutParams(iconSize, iconSize))
        val label = TextView(this).apply {
            text = info.label
            gravity = Gravity.CENTER
            maxLines = 1
            textSize = 11f
            setTextColor(Color.WHITE)
            setShadowLayer(3.5f, 0f, 1f, Color.BLACK)
            isFocusable = false
        }
        vertical.addView(label, LinearLayout.LayoutParams(-1, -2))
        vertical.contentDescription = info.label
        vertical.isClickable = true
        vertical.isFocusable = true
        vertical.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        return vertical
    }

    private fun attachTouch(view: View, id: String, onLaunch: () -> Unit) {
        var downRawX = 0f
        var downRawY = 0f
        var startLeft = 0f
        var startTop = 0f
        var moving = false
        var armed = false
        var cancelledByGesture = false
        val slop = ViewConfiguration.get(this).scaledTouchSlop.toFloat()
        val armDrag = Runnable {
            if (!cancelledByGesture) {
                armed = true
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
        }
        view.setOnClickListener { onLaunch() }
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startLeft = v.x
                    startTop = v.y
                    moving = false
                    armed = false
                    cancelledByGesture = false
                    handler.postDelayed(armDrag, ViewConfiguration.getLongPressTimeout().toLong())
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!armed && (abs(dx) > slop || abs(dy) > slop)) {
                        cancelledByGesture = true
                        handler.removeCallbacks(armDrag)
                    }
                    if (armed && (abs(dx) > slop || abs(dy) > slop)) moving = true
                    if (moving) {
                        draggingId = id
                        v.x = (startLeft + dx).coerceIn(0f,
                            (canvas.width - v.width).coerceAtLeast(0).toFloat())
                        v.y = (startTop + dy).coerceIn(0f,
                            (canvas.height - v.height).coerceAtLeast(0).toFloat())
                        requestPublish()
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(armDrag)
                    if (moving) {
                        snapToNearestCell(id, v)
                        draggingId = null
                        saveLayout()
                        renderHomeNextFrame()
                    } else if (!armed && !cancelledByGesture) {
                        // A stationary long press must never launch an app.
                        // Only a short, non-drag tap triggers the app click.
                        // Supports TalkBack and keyboard activation via performClick.
                        v.performClick()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(armDrag)
                    draggingId = null
                    renderHomeNextFrame()
                    true
                }
                else -> true
            }
        }
    }

    private fun snapToNearestCell(id: String, icon: View) {
        val centerX = ((icon.x + icon.width / 2f) / canvas.width).coerceIn(0f, 1f)
        val centerY = ((icon.y + icon.height / 2f) / canvas.height).coerceIn(0f, 1f)
        val source = placed.firstOrNull { it.id == id }
        val sourceFolder = folders.firstOrNull { it.id == id }
        val fromDock = dock.indexOf(id)
        if (centerY >= 1f - (170 * dp / canvas.height)) {
            // A true fixed dock: same apps remain visible across Home pages.
            if (sourceFolder != null) return  // folder in dock deferred
            if (fromDock >= 0) {
                val to = (centerX * dock.size).toInt().coerceIn(0, dock.size - 1)
                dock.removeAt(fromDock)
                dock.add(to, id)
            } else if (source != null && dock.size < dockSlots) {
                placed.remove(source)
                dock.add((centerX * (dock.size + 1)).toInt()
                    .coerceIn(0, dock.size), id)
            }
            return
        }
        val col = (centerX * columns).toInt().coerceIn(0, columns - 1)
        val row = ((centerY - topFraction) / spanFraction * rows)
            .toInt().coerceIn(0, rows - 1)
        val dest = currentPage * pageSlots + row * columns + col
        if (placedWidgets.any { dest in widgetCells(it) }) return
        if (source?.cell == dest || sourceFolder?.cell == dest) return
        val targetFolder = folders.firstOrNull { it.id != id && it.cell == dest }
        if (targetFolder != null && source != null && targetFolder.members.size < 24) {
            placed.remove(source)
            targetFolder.members.add(id)
            return
        }
        val targetIcon = placed.firstOrNull { it.id != id && it.cell == dest }
        if (targetIcon != null && source != null) {
            // Drag app on app -> real editable folder, not a simulated icon.
            placed.remove(source)
            placed.remove(targetIcon)
            folders.add(PlacedFolder("folder:" + UUID.randomUUID().toString(),
                getString(R.string.auto_home_folder), dest,
                mutableListOf(targetIcon.id, source.id)))
            return
        }
        val previous = source?.cell ?: sourceFolder?.cell
        val destinationHolder = targetIcon ?: folders.firstOrNull {
            it.id != id && it.cell == dest
        }
        if (destinationHolder != null && previous == null) return
        if (destinationHolder is PlacedIcon && previous != null) {
            destinationHolder.cell = previous
        } else if (destinationHolder is PlacedFolder && previous != null) {
            destinationHolder.cell = previous
        }
        if (source != null) source.cell = dest
        if (sourceFolder != null) sourceFolder.cell = dest
        if (fromDock >= 0 && targetIcon == null && targetFolder == null) {
            dock.removeAt(fromDock)
            placed.add(PlacedIcon(id, dest))
        }
    }

    private fun startApp(info: LauncherActivityInfo) {
        try {
            launcherApps.startMainActivity(info.componentName, info.user,
                null, null)
        } catch (_: Exception) {
            Toast.makeText(this, R.string.auto_home_cannot_open, Toast.LENGTH_SHORT).show()
        }
    }

    private fun switchPage(target: Int) {
        val next = target.coerceIn(0, maxPages - 1)
        if (next == currentPage) return
        currentPage = next
        prefs.edit().putInt("auto_home_page_v1", currentPage).apply()
        renderHome()
    }

    private fun hasAdaptiveWallpaper(): Boolean = try {
        val current = WallpaperManager.getInstance(this).wallpaperInfo
        current?.packageName == packageName &&
            current.serviceName == AdaptiveWallpaperService::class.java.name
    } catch (_: Exception) {
        false
    }

    private fun widgetCells(item: PlacedWidget): Set<Int> = buildSet {
        val page = item.cell / pageSlots
        val local = item.cell % pageSlots
        val col = local % columns
        val row = local / columns
        for (dy in 0 until item.spanY) for (dx in 0 until item.spanX) {
            if (col + dx < columns && row + dy < rows)
                add(page * pageSlots + (row + dy) * columns + (col + dx))
        }
    }

    private fun freeCell(cell: Int): Boolean =
        placed.none { it.cell == cell } &&
            folders.none { it.cell == cell } &&
            placedWidgets.none { cell in widgetCells(it) }

    private fun canPlaceWidget(
        cell: Int, spanX: Int, spanY: Int, ignoringWidgetId: Int = -1
    ): Boolean {
        if (cell !in 0 until maxSlots) return false
        val local = cell % pageSlots
        val col = local % columns
        val row = local / columns
        if (col + spanX > columns || row + spanY > rows) return false
        val proposal = PlacedWidget(ignoringWidgetId, cell, spanX, spanY)
        val occupied = widgetCells(proposal)
        return placed.none { it.cell in occupied } &&
            folders.none { it.cell in occupied } &&
            placedWidgets.none {
                it.widgetId != ignoringWidgetId && widgetCells(it).any(occupied::contains)
            }
    }

    private fun restoreWidgets() {
        placedWidgets.clear()
        val saved = prefs.getString("auto_home_widgets_v1", "[]") ?: "[]"
        try {
            val all = JSONArray(saved)
            for (i in 0 until minOf(all.length(), 24)) {
                val item = all.optJSONObject(i) ?: continue
                val id = item.optInt("widgetId", -1)
                val cell = item.optInt("cell", -1)
                val sx = item.optInt("spanX", 2).coerceIn(1, columns)
                val sy = item.optInt("spanY", 2).coerceIn(1, rows)
                if (id < 0 || cell !in 0 until maxSlots ||
                    widgetManager.getAppWidgetInfo(id) == null ||
                    placedWidgets.any { it.widgetId == id }) continue
                val local = cell % pageSlots
                if (local % columns + sx > columns ||
                    local / columns + sy > rows) continue
                placedWidgets.add(PlacedWidget(id, cell, sx, sy))
            }
        } catch (_: Exception) {
            // Malformed local widget persistence cannot crash the system Home.
        }
    }

    private fun saveWidgets() {
        val data = JSONArray()
        for (item in placedWidgets) {
            data.put(JSONObject()
                .put("widgetId", item.widgetId)
                .put("cell", item.cell)
                .put("spanX", item.spanX)
                .put("spanY", item.spanY))
        }
        prefs.edit().putString("auto_home_widgets_v1", data.toString()).apply()
    }

    private fun renderWidgets() {
        val cellWidth = canvas.width.toFloat() / columns
        val cellHeight = (canvas.height * spanFraction / rows).toFloat()
        for (item in placedWidgets) {
            if (item.cell / pageSlots != currentPage) continue
            val info = widgetManager.getAppWidgetInfo(item.widgetId) ?: continue
            try {
                val tile = widgetHost.createView(this, item.widgetId, info)
                val width = (cellWidth * item.spanX).roundToInt()
                val height = (cellHeight * item.spanY).roundToInt()
                canvas.addView(tile, FrameLayout.LayoutParams(width, height))
                val local = item.cell % pageSlots
                tile.x = local % columns * cellWidth
                tile.y = (topFraction * canvas.height + local / columns * cellHeight)
                    .toFloat()
                tile.updateAppWidgetSize(null,
                    (width / dp).roundToInt(), (height / dp).roundToInt(),
                    (width / dp).roundToInt(), (height / dp).roundToInt())
                widgetViews[item.widgetId] = tile
                tile.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                    if (v.parent === canvas) requestPublish()
                }
            } catch (_: RuntimeException) {
                // A broken provider cannot take down the whole launcher.
            }
        }
    }

    private fun showWidgetActions() {
        AlertDialog.Builder(this)
            .setTitle(R.string.auto_home_widgets)
            .setItems(arrayOf(getString(R.string.auto_home_widget_add),
                getString(R.string.auto_home_widget_manage))) { _, index ->
                if (index == 0) showWidgetPicker() else showWidgetManager()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showWidgetPicker() {
        val providers = widgetManager.installedProviders
            .filter { it.provider.packageName != packageName }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) {
                it.loadLabel(packageManager).toString()
            })
        if (providers.isEmpty()) {
            Toast.makeText(this, R.string.auto_home_widget_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.auto_home_widget_add)
            .setItems(providers.map { it.loadLabel(packageManager).toString() }
                .toTypedArray()) { _, index ->
                val info = providers[index]
                // 2×2 is a bounded starting size; users can resize afterwards.
                val sx = 2
                val sy = 2
                val free = (currentPage * pageSlots until (currentPage + 1) * pageSlots)
                    .firstOrNull { canPlaceWidget(it, sx, sy) }
                if (free == null) {
                    Toast.makeText(this, R.string.auto_home_no_space, Toast.LENGTH_SHORT).show()
                    return@setItems
                }
                try {
                    val id = widgetHost.allocateAppWidgetId()
                    pendingWidget = PlacedWidget(id, free, sx, sy)
                    pendingInfo = info
                    if (widgetManager.bindAppWidgetIdIfAllowed(id, info.provider)) {
                        continueWidgetSetup()
                    } else {
                        startActivityForResult(Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider),
                            REQUEST_BIND_WIDGET)
                    }
                } catch (_: Exception) {
                    rejectPendingWidget()
                    Toast.makeText(this, R.string.auto_home_widget_unavailable,
                        Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun continueWidgetSetup() {
        val info = pendingInfo ?: return rejectPendingWidget()
        val id = pendingWidget?.widgetId ?: return rejectPendingWidget()
        if (info.configure == null) {
            commitPendingWidget()
            return
        }
        try {
            startActivityForResult(Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                .setComponent(info.configure)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id),
                REQUEST_CONFIGURE_WIDGET)
        } catch (_: Exception) {
            rejectPendingWidget()
        }
    }

    private fun commitPendingWidget() {
        val placement = pendingWidget ?: return
        if (widgetManager.getAppWidgetInfo(placement.widgetId) == null) {
            rejectPendingWidget()
            return
        }
        placedWidgets.add(placement)
        pendingWidget = null
        pendingInfo = null
        saveWidgets()
        renderHome()
    }

    private fun rejectPendingWidget() {
        pendingWidget?.let {
            try { widgetHost.deleteAppWidgetId(it.widgetId) } catch (_: Exception) { }
        }
        pendingWidget = null
        pendingInfo = null
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_BIND_WIDGET || requestCode == REQUEST_CONFIGURE_WIDGET) {
            if (resultCode != RESULT_OK) {
                rejectPendingWidget()
            } else if (requestCode == REQUEST_BIND_WIDGET) {
                continueWidgetSetup()
            } else {
                commitPendingWidget()
            }
        }
    }

    private fun showWidgetManager() {
        val selected = placedWidgets.toList()
        if (selected.isEmpty()) {
            Toast.makeText(this, R.string.auto_home_widget_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        val labels = selected.map {
            widgetManager.getAppWidgetInfo(it.widgetId)?.loadLabel(packageManager)
                ?.toString() ?: getString(R.string.auto_home_widgets)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.auto_home_widget_manage)
            .setItems(labels.toTypedArray()) { _, index ->
                val item = selected[index]
                val choices = arrayOf(
                    getString(R.string.auto_home_widget_move),
                    getString(R.string.auto_home_widget_resize),
                    getString(R.string.auto_home_widget_remove))
                AlertDialog.Builder(this)
                    .setTitle(labels[index])
                    .setItems(choices) { _, action ->
                        when (action) {
                            0 -> {
                                val page = (item.cell / pageSlots + 1) % maxPages
                                val free = (page * pageSlots until (page + 1) * pageSlots)
                                    .firstOrNull {
                                        canPlaceWidget(it, item.spanX, item.spanY, item.widgetId)
                                    }
                                if (free != null) item.cell = free
                                else Toast.makeText(this, R.string.auto_home_no_space,
                                    Toast.LENGTH_SHORT).show()
                            }
                            1 -> {
                                val wider = if (item.spanX < columns) item.spanX + 1 else item.spanX
                                val taller = if (wider == item.spanX && item.spanY < rows)
                                    item.spanY + 1 else item.spanY
                                if (canPlaceWidget(item.cell, wider, taller, item.widgetId)) {
                                    item.spanX = wider
                                    item.spanY = taller
                                } else Toast.makeText(this, R.string.auto_home_no_space,
                                    Toast.LENGTH_SHORT).show()
                            }
                            2 -> {
                                placedWidgets.removeAll { it.widgetId == item.widgetId }
                                try { widgetHost.deleteAppWidgetId(item.widgetId) }
                                catch (_: Exception) { }
                            }
                        }
                        saveWidgets()
                        renderHome()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }


    private fun chooseInstalledApp(
        title: String, choices: List<LauncherActivityInfo>,
        onChosen: (LauncherActivityInfo) -> Unit
    ) {
        if (choices.isEmpty()) {
            Toast.makeText(this, R.string.auto_home_no_space, Toast.LENGTH_SHORT).show()
            return
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((12 * dp).roundToInt(), (6 * dp).roundToInt(),
                (12 * dp).roundToInt(), 0)
        }
        val search = SearchView(this).apply {
            queryHint = getString(R.string.auto_home_search)
            isIconified = false
        }
        val list = ListView(this)
        val matches = choices.toMutableList()
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1,
            matches.map { it.label.toString() }.toMutableList())
        list.adapter = adapter
        layout.addView(search)
        layout.addView(list, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, (360 * dp).roundToInt()))
        val dialog = AlertDialog.Builder(this).setTitle(title).setView(layout)
            .setNegativeButton(android.R.string.cancel, null).create()
        list.setOnItemClickListener { _, _, index, _ ->
            val chosen = matches.getOrNull(index) ?: return@setOnItemClickListener
            dialog.dismiss()
            onChosen(chosen)
        }
        search.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = true
            override fun onQueryTextChange(query: String?): Boolean {
                val value = query.orEmpty().trim()
                matches.clear()
                matches.addAll(choices.filter {
                    value.isEmpty() || it.label.contains(value, ignoreCase = true)
                })
                adapter.clear()
                adapter.addAll(matches.map { it.label.toString() })
                adapter.notifyDataSetChanged()
                return true
            }
        })
        dialog.show()
    }

    private fun appId(info: LauncherActivityInfo) = info.componentName.flattenToString()

    private fun availableForFolder(): List<LauncherActivityInfo> {
        val alreadyGrouped = folders.flatMap { it.members }.toSet()
        return available.filter { appId(it) !in alreadyGrouped }
    }

    private fun createFolder() {
        val candidates = availableForFolder()
        if (candidates.size < 2) {
            Toast.makeText(this, R.string.auto_home_folder_two, Toast.LENGTH_SHORT).show()
            return
        }
        val selected = LinkedHashSet<String>()
        AlertDialog.Builder(this)
            .setTitle(R.string.auto_home_folder_create)
            .setMultiChoiceItems(candidates.map { it.label.toString() }.toTypedArray(),
                BooleanArray(candidates.size)) { _, index, checked ->
                if (checked) selected.add(appId(candidates[index]))
                else selected.remove(appId(candidates[index]))
            }
            .setPositiveButton(R.string.auto_home_folder_create) { _, _ ->
                if (selected.size < 2) {
                    Toast.makeText(this, R.string.auto_home_folder_two,
                        Toast.LENGTH_SHORT).show()
                } else {
                    val cell = (currentPage * pageSlots until (currentPage + 1) * pageSlots)
                        .firstOrNull { freeCell(it) } ?: placed
                            .firstOrNull { it.cell / pageSlots == currentPage }?.cell
                    if (cell == null) {
                        Toast.makeText(this, R.string.auto_home_no_space,
                            Toast.LENGTH_SHORT).show()
                    } else {
                        // Do not overwrite an unrelated icon: choose the
                        // source cell only if that source is joining the folder.
                        val target = if (freeCell(cell)) cell else placed
                            .firstOrNull { it.cell == cell && it.id in selected }?.cell
                        if (target == null) {
                            Toast.makeText(this, R.string.auto_home_no_space,
                                Toast.LENGTH_SHORT).show()
                        } else requestFolderName(getString(R.string.auto_home_folder)) { name ->
                            placed.removeAll { it.id in selected }
                            dock.removeAll { it in selected }
                            folders.add(PlacedFolder("folder:" + UUID.randomUUID().toString(),
                                name, target, selected.toMutableList()))
                            saveLayout()
                            renderHomeNextFrame()
                        }
                    }
                }
            }.setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun requestFolderName(initial: String, onName: (String) -> Unit) {
        val input = EditText(this).apply {
            setSingleLine(true)
            setText(initial)
            setSelection(text.length)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.auto_home_folder_name)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                onName(input.text.toString().trim().take(48).ifBlank {
                    getString(R.string.auto_home_folder)
                })
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showFolderManager() {
        val options = arrayOf(getString(R.string.auto_home_folder_create),
            getString(R.string.auto_home_folder_manage))
        AlertDialog.Builder(this).setTitle(R.string.auto_home_folder)
            .setItems(options) { _, which ->
                if (which == 0) createFolder()
                else {
                    if (folders.isEmpty()) {
                        Toast.makeText(this, R.string.auto_home_folder_empty,
                            Toast.LENGTH_SHORT).show()
                    } else AlertDialog.Builder(this)
                        .setTitle(R.string.auto_home_folder_manage)
                        .setItems(folders.map { it.name }.toTypedArray()) { _, index ->
                            showFolderActions(folders[index])
                        }.setNegativeButton(android.R.string.cancel, null).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun openFolder(folder: PlacedFolder) {
        val found = available.associateBy(::appId)
        val members = folder.members.mapNotNull { found[it] }
        AlertDialog.Builder(this)
            .setTitle(folder.name)
            .setItems(members.map { it.label.toString() }.toTypedArray()) { _, index ->
                startApp(members[index])
            }
            .setPositiveButton(R.string.auto_home_folder_manage) { _, _ ->
                showFolderActions(folder)
            }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun showFolderActions(folder: PlacedFolder) {
        val actions = arrayOf(
            getString(R.string.auto_home_folder_rename),
            getString(R.string.auto_home_folder_add),
            getString(R.string.auto_home_folder_remove),
            getString(R.string.auto_home_folder_unpack)
        )
        AlertDialog.Builder(this).setTitle(folder.name)
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> requestFolderName(folder.name) { folder.name = it
                        saveLayout(); renderHomeNextFrame() }
                    1 -> {
                        val alreadyGrouped = folders.flatMap { it.members }.toSet()
                        val options = available.filter {
                            appId(it) !in alreadyGrouped && appId(it) !in folder.members
                        }
                        chooseInstalledApp(getString(R.string.auto_home_folder_add),
                            options) { info ->
                            if (folder.members.size >= 24) {
                                Toast.makeText(this, R.string.auto_home_folder_full,
                                    Toast.LENGTH_SHORT).show()
                            } else {
                                val id = appId(info)
                                placed.removeAll { it.id == id }
                                dock.remove(id)
                                folder.members.add(id)
                                saveLayout(); renderHomeNextFrame()
                            }
                        }
                    }
                    2 -> {
                        val known = available.associateBy(::appId)
                        AlertDialog.Builder(this)
                            .setTitle(R.string.auto_home_folder_remove)
                            .setItems(folder.members.map { known[it]?.label?.toString() ?: it }
                                .toTypedArray()) { _, index ->
                                folder.members.removeAt(index)
                                if (folder.members.isEmpty()) folders.remove(folder)
                                saveLayout(); renderHomeNextFrame()
                            }.setNegativeButton(android.R.string.cancel, null).show()
                    }
                    3 -> {
                        folders.remove(folder)
                        // Reclaim the folder cell first, then fill free
                        // positions. Extras remain available in the app drawer.
                        for ((index, member) in folder.members.withIndex()) {
                            val cell = if (index == 0 && freeCell(folder.cell)) folder.cell
                                else (folder.cell / pageSlots * pageSlots until
                                    (folder.cell / pageSlots + 1) * pageSlots)
                                    .firstOrNull { freeCell(it) }
                            if (cell != null) placed.add(PlacedIcon(member, cell))
                        }
                        saveLayout(); renderHomeNextFrame()
                    }
                }
            }.setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun showDockManager() {
        val actions = arrayOf(getString(R.string.auto_home_dock_add),
            getString(R.string.auto_home_dock_remove))
        AlertDialog.Builder(this).setTitle(R.string.auto_home_dock)
            .setItems(actions) { _, which ->
                if (which == 0) {
                    if (dock.size >= dockSlots) {
                        Toast.makeText(this, R.string.auto_home_dock_full,
                            Toast.LENGTH_SHORT).show()
                    } else {
                        val grouped = folders.flatMap { it.members }.toSet()
                        chooseInstalledApp(getString(R.string.auto_home_dock_add),
                            available.filter { appId(it) !in dock && appId(it) !in grouped }
                        ) { info ->
                            val id = appId(info)
                            placed.removeAll { it.id == id }
                            dock.add(id)
                            saveLayout(); renderHomeNextFrame()
                        }
                    }
                } else {
                    val known = available.associateBy(::appId)
                    AlertDialog.Builder(this).setTitle(R.string.auto_home_dock_remove)
                        .setItems(dock.map { known[it]?.label?.toString() ?: it }
                            .toTypedArray()) { _, index ->
                            dock.removeAt(index)
                            saveLayout(); renderHomeNextFrame()
                        }.setNegativeButton(android.R.string.cancel, null).show()
                }
            }.setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun resetHomeWithConfirmation() {
        AlertDialog.Builder(this).setTitle(R.string.auto_home_reset_title)
            .setMessage(R.string.auto_home_reset_warning)
            .setPositiveButton(R.string.auto_home_reset_confirm) { _, _ ->
                // Only pinned apps, folders and dock. Widgets / wallpaper /
                // manually calibrated profiles remain untouched.
                placed.clear()
                folders.clear()
                dock.clear()
                saveLayout()
                renderHomeNextFrame()
            }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun showHomeOrganizer() {
        val options = arrayOf(getString(R.string.auto_home_folder_manage),
            getString(R.string.auto_home_dock),
            getString(R.string.auto_home_reset_title))
        AlertDialog.Builder(this).setTitle(R.string.auto_home_organize)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showFolderManager()
                    1 -> showDockManager()
                    else -> resetHomeWithConfirmation()
                }
            }.setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun showAppDrawer() {
        if (available.isEmpty()) {
            Toast.makeText(this, R.string.auto_home_no_space, Toast.LENGTH_SHORT).show()
            return
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((12 * dp).roundToInt(), (8 * dp).roundToInt(),
                (12 * dp).roundToInt(), 0)
        }
        val search = SearchView(this).apply {
            queryHint = getString(R.string.auto_home_search)
            isIconified = false
        }
        val list = ListView(this)
        val matches = ArrayList(available)
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1,
            matches.map { it.label.toString() }.toMutableList())
        list.adapter = adapter
        container.addView(search)
        container.addView(list, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, (350 * dp).roundToInt()))
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.auto_home_apps)
            .setView(container)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        list.setOnItemClickListener { _, _, position, _ ->
            matches.getOrNull(position)?.let { startApp(it) }
            dialog.dismiss()
        }
        list.setOnItemLongClickListener { _, _, position, _ ->
            val info = matches.getOrNull(position) ?: return@setOnItemLongClickListener false
            dialog.dismiss()
            val actions = arrayOf(getString(R.string.auto_home_add),
                getString(R.string.auto_home_dock_add))
            AlertDialog.Builder(this).setTitle(info.label)
                .setItems(actions) { _, option ->
                    val id = appId(info)
                    if (option == 0) pinAppToCurrentPage(id)
                    else {
                        if (id !in dock && dock.size < dockSlots &&
                            folders.none { id in it.members }) {
                            placed.removeAll { it.id == id }
                            dock.add(id)
                            saveLayout(); renderHomeNextFrame()
                        } else Toast.makeText(this, R.string.auto_home_no_space,
                            Toast.LENGTH_SHORT).show()
                    }
                }.show()
            true
        }
        search.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = true
            override fun onQueryTextChange(query: String?): Boolean {
                val term = query.orEmpty().trim()
                matches.clear()
                matches.addAll(available.filter {
                    term.isEmpty() || it.label.contains(term, ignoreCase = true)
                })
                adapter.clear()
                adapter.addAll(matches.map { it.label.toString() })
                adapter.notifyDataSetChanged()
                return true
            }
        })
        dialog.show()
    }

    private fun chromeButton(title: String, action: () -> Unit): TextView =
        TextView(this).apply {
            text = title
            gravity = Gravity.CENTER
            textSize = 13f
            setTextColor(Color.WHITE)
            setShadowLayer(3.5f, 0f, 1f, Color.BLACK)
            setPadding((9 * dp).roundToInt(), (8 * dp).roundToInt(),
                (9 * dp).roundToInt(), (8 * dp).roundToInt())
            background = GradientDrawable().apply {
                cornerRadius = 12 * dp
                setColor(Color.argb(146, 13, 47, 54))
            }
            setOnClickListener { action() }
        }

    private fun renderChrome() {
        val isDefault = isSelectedHome()
        val widgetControl = chromeButton(getString(R.string.auto_home_widgets)) {
            showWidgetActions()
        }
        canvas.addView(widgetControl, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, (40 * dp).roundToInt(),
            Gravity.TOP or Gravity.START).apply {
                topMargin = (40 * dp).roundToInt()
            })
        val top = chromeButton(getString(R.string.auto_home_caption)) {
            // Settings is a separate Flutter app task. Never put the editor
            // on top of the HOME task or the Home key may resurrect settings.
            startActivity(Intent(this, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }
        canvas.addView(top, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, (40 * dp).roundToInt(),
            Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                topMargin = (40 * dp).roundToInt()
            })
        val pageButton = chromeButton(
            getString(R.string.auto_home_page, currentPage + 1, maxPages)) {
            switchPage((currentPage + 1) % maxPages)
        }
        canvas.addView(pageButton, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, (38 * dp).roundToInt(),
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                bottomMargin = (170 * dp).roundToInt()
            })
        if (isDefault && !hasAdaptiveWallpaper()) {
            val help = chromeButton(getString(R.string.auto_home_wallpaper_needed)) {
                selectWallpaper()
            }
            canvas.addView(help, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, (45 * dp).roundToInt(),
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                    bottomMargin = (220 * dp).roundToInt()
                })
        }
        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val actions = listOf<Pair<Int, () -> Unit>>(
            R.string.auto_home_apps to { showAppDrawer() },
            R.string.auto_home_add to { showAppPicker() },
            R.string.auto_home_manage to { showRemovePicker() },
            R.string.auto_home_folder to { showFolderManager() },
            R.string.auto_home_dock to { showDockManager() },
            R.string.auto_home_organize to { showHomeOrganizer() }
        )
        for ((label, action) in actions) {
            bottom.addView(chromeButton(getString(label), action).apply {
                textSize = 10f
                setPadding((2 * dp).roundToInt(), 0, (2 * dp).roundToInt(), 0)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        }
        val switchHome = chromeButton(getString(R.string.auto_home_change_home)) {
            openHomeSettings()
        }
        canvas.addView(switchHome, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, (40 * dp).roundToInt(),
            Gravity.TOP or Gravity.END).apply {
                topMargin = (40 * dp).roundToInt()
            })
        canvas.addView(bottom, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, (54 * dp).roundToInt(),
            Gravity.BOTTOM).apply { bottomMargin = (22 * dp).roundToInt() })
        if (views.isEmpty() && placedWidgets.none { it.cell / pageSlots == currentPage }) {
            val empty = chromeButton(getString(R.string.auto_home_first_run)) {
                showAppPicker()
            }.apply {
                textSize = 15f
                setPadding((14 * dp).roundToInt(), (14 * dp).roundToInt(),
                    (14 * dp).roundToInt(), (14 * dp).roundToInt())
            }
            canvas.addView(empty, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER))
        }
        if (!isDefault) {
            val warning = chromeButton(getString(R.string.auto_home_not_default)) {
                openHomeSettings()
            }
            canvas.addView(warning, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, (50 * dp).roundToInt(),
                Gravity.CENTER))
        }
    }

    private fun pinAppToCurrentPage(id: String) {
        if (placed.any { it.id == id }) {
            Toast.makeText(this, R.string.auto_home_already_pinned,
                Toast.LENGTH_SHORT).show()
            return
        }
        if (dock.remove(id)) {
            // Moving from dock to page, not duplicating it.
        } else if (folders.any { id in it.members }) {
            Toast.makeText(this, R.string.auto_home_already_pinned,
                Toast.LENGTH_SHORT).show()
            return
        }
        val free = (currentPage * pageSlots until (currentPage + 1) * pageSlots)
            .firstOrNull { freeCell(it) }
        if (free != null) {
            placed.add(PlacedIcon(id, free))
            saveLayout()
            renderHomeNextFrame()
        } else {
            Toast.makeText(this, R.string.auto_home_no_space,
                Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAppPicker() {
        val pinned = placed.map { it.id }.toSet() +
            dock.toSet() + folders.flatMap { it.members }.toSet()
        chooseInstalledApp(getString(R.string.auto_home_add),
            available.filter { appId(it) !in pinned }) { chosen ->
            pinAppToCurrentPage(appId(chosen))
        }
    }

    private fun showRemovePicker() {
        if (placed.isEmpty()) return
        val known = available.associateBy { it.componentName.flattenToString() }
        val options = placed.toList()
        AlertDialog.Builder(this)
            .setTitle(R.string.auto_home_manage)
            .setItems(options.map { known[it.id]?.label?.toString() ?: it.id }
                .toTypedArray()) { _, index ->
                placed.removeAll { it.id == options[index].id }
                saveLayout()
                renderHome()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun selectWallpaper() {
        try {
            val component = ComponentName(this, AdaptiveWallpaperService::class.java)
            startActivity(Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component))
        } catch (_: Exception) {
            Toast.makeText(this, R.string.auto_home_wallpaper_failed,
                Toast.LENGTH_SHORT).show()
        }
    }

    private fun openHomeSettings() {
        try {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        } catch (_: Exception) {
            Toast.makeText(this, R.string.auto_home_role_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestPublish() {
        handler.removeCallbacks(publishPending)
        val now = SystemClock.uptimeMillis()
        if (now - lastPublishedAt >= 90L) publishHomePositions()
        else handler.postDelayed(publishPending, 90L - (now - lastPublishedAt))
    }

    private fun publishHomePositions() {
        if (canvas.width <= 0 || canvas.height <= 0 || !isSelectedHome()) return
        lastPublishedAt = SystemClock.uptimeMillis()
        // Use the actual on-screen icon views, not a manually marked grid.
        // Deliberately separate from config_v2 and user profiles.
        val zones = JSONArray()
        val canvasW = canvas.width.toDouble()
        val canvasH = canvas.height.toDouble()
        for ((_, tile) in views) {
            if (tile.width <= 0 || tile.height <= 0) continue
            val x = ((tile.x + tile.width * 0.5) / canvasW).coerceIn(0.0, 1.0)
            val y = ((tile.y + tile.height * 0.5) / canvasH).coerceIn(0.0, 1.0)
            zones.put(JSONObject()
                .put("x", x).put("y", y)
                .put("width", (tile.width / canvasW * 0.60).coerceIn(0.03, 0.22))
                .put("height", (tile.height / canvasH * 0.60).coerceIn(0.03, 0.16))
                .put("kind", "icon"))
        }
        // A hosted widget owns its live on-screen rectangle, just like an app.
        // Only normalized geometry is published to the wallpaper; no content.
        for ((_, tile) in widgetViews) {
            if (tile.width <= 0 || tile.height <= 0) continue
            val x = ((tile.x + tile.width * 0.5) / canvasW).coerceIn(0.0, 1.0)
            val y = ((tile.y + tile.height * 0.5) / canvasH).coerceIn(0.0, 1.0)
            zones.put(JSONObject()
                .put("x", x).put("y", y)
                .put("width", (tile.width / canvasW).coerceIn(0.03, 0.95))
                .put("height", (tile.height / canvasH).coerceIn(0.03, 0.95))
                .put("kind", "widget"))
        }
        val base = try {
            JSONObject(prefs.getString("config_v2", null) ?: "{}")
        } catch (_: Exception) {
            JSONObject()
        }
        base.put("schema", 2)
            .put("scene", if (base.optString("scene") == "meadow") "meadow" else "beach")
            .put("occupied", JSONArray())
            .put("zones", zones)
        val next = base.toString()
        if (prefs.getString("auto_home_config_v1", null) != next ||
            !prefs.getBoolean("auto_home_active_v1", false)) {
            prefs.edit()
                .putString("auto_home_config_v1", next)
                .putBoolean("auto_home_active_v1", true)
                .putLong("auto_home_revision_v1", SystemClock.uptimeMillis())
                .apply()
        }
    }
}
