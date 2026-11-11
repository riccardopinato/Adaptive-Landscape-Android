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
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.SearchView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.ceil
import org.json.JSONArray
import org.json.JSONObject
import it.riccardopinato.adaptive_landscape.R
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * EXPERIMENTAL, user-chosen Home/launcher, not a background icon scanner.
 *
 * Android only exposes precise icon positions to the launcher that owns those
 * views. This Activity owns its displayed icons and publishes their positions
 * to the existing WallpaperService via a separate, volatile scene contract.
 *
 * Limitations: no migration from MIUI/Pixel/Samsung launcher grids, and no
 * widgets/folders/work-profile support yet. NEVER replace the default Home
 * without the user's explicit system RoleManager consent.
 */
class AdaptiveHomeActivity : Activity() {
    private data class PlacedIcon(val id: String, var cell: Int)
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
    private val topFraction = 0.115
    private val spanFraction = 0.765
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
                renderHome()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        try { widgetHost.startListening() } catch (_: RuntimeException) { }
        restoreWidgets()
        loadLaunchableApps()
        restoreSavedLayout()
        canvas.post { renderHome() }
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
        val saved = prefs.getString("auto_home_icons_v1", null)
        if (saved == null) {
            // Initial layout is automatic but cannot import another launcher's
            // personalized icon order. Users can move icons on this Home.
            for ((i, item) in available.take(12).withIndex()) {
                placed.add(PlacedIcon(item.componentName.flattenToString(), i + 5))
            }
            saveLayout()
            return
        }
        try {
            val array = JSONArray(saved)
            val used = HashSet<Int>()
            for (i in 0 until minOf(array.length(), maxSlots)) {
                val item = array.optJSONObject(i) ?: continue
                val id = item.optString("id")
                val cell = item.optInt("cell", -1)
                if (known.containsKey(id) && cell in 0 until maxSlots &&
                    used.add(cell) && placed.none { it.id == id }) {
                    placed.add(PlacedIcon(id, cell))
                }
            }
        } catch (_: Exception) {
            // Invalid local JSON must not crash the selected Home.
        }
    }

    private fun saveLayout() {
        val array = JSONArray()
        for (item in placed) {
            array.put(JSONObject().put("id", item.id).put("cell", item.cell))
        }
        prefs.edit().putString("auto_home_icons_v1", array.toString()).apply()
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
            canvas.addView(tile, FrameLayout.LayoutParams(iconWidth(), iconHeight()))
            tile.x = leftFor(item.cell)
            tile.y = topFor(item.cell)
            views[item.id] = tile
            // Child views are measured after renderHome(). Republish their live
            // geometry after layout, or a page round-trip can leave zero zones.
            tile.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                if (v.parent === canvas) requestPublish()
            }
            attachTouch(tile, item.id, info)
        }
        renderWidgets()
        renderChrome()
        requestPublish()
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

    private fun attachTouch(view: View, id: String, info: LauncherActivityInfo) {
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
        view.setOnClickListener { startApp(info) }
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
                        renderHome()
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
                    renderHome()
                    true
                }
                else -> true
            }
        }
    }

    private fun snapToNearestCell(id: String, icon: View) {
        val centerX = (icon.x + icon.width / 2f) / canvas.width
        val centerY = (icon.y + icon.height / 2f) / canvas.height
        val col = (centerX * columns).toInt().coerceIn(0, columns - 1)
        val row = ((centerY - topFraction) / spanFraction * rows)
            .toInt().coerceIn(0, rows - 1)
        val dest = currentPage * pageSlots + row * columns + col
        val moving = placed.firstOrNull { it.id == id } ?: return
        val previousCell = moving.cell
        val occupant = placed.firstOrNull { it.id != id && it.cell == dest }
        if (occupant != null) occupant.cell = previousCell
        moving.cell = dest
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
            setBackgroundColor(Color.argb(118, 13, 47, 54))
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
            startActivity(Intent(this, MainActivity::class.java))
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
                bottomMargin = (96 * dp).roundToInt()
            })
        if (isDefault && !hasAdaptiveWallpaper()) {
            val help = chromeButton(getString(R.string.auto_home_wallpaper_needed)) {
                selectWallpaper()
            }
            canvas.addView(help, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, (45 * dp).roundToInt(),
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                    bottomMargin = (140 * dp).roundToInt()
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
            R.string.auto_home_wallpaper to { selectWallpaper() }
        )
        for ((label, action) in actions) {
            bottom.addView(chromeButton(getString(label), action),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
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
            Gravity.BOTTOM).apply { bottomMargin = (36 * dp).roundToInt() })
        if (!isDefault) {
            val warning = chromeButton(getString(R.string.auto_home_not_default)) {
                openHomeSettings()
            }
            canvas.addView(warning, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, (50 * dp).roundToInt(),
                Gravity.CENTER))
        }
    }

    private fun showAppPicker() {
        val present = placed.map { it.id }.toSet()
        val options = available.filter {
            it.componentName.flattenToString() !in present
        }
        if (options.isEmpty() || placed.size >= maxSlots) {
            Toast.makeText(this, R.string.auto_home_no_space, Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.auto_home_add)
            .setItems(options.map { it.label.toString() }.toTypedArray()) { _, index ->
                val free = (currentPage * pageSlots until (currentPage + 1) * pageSlots)
                    .firstOrNull { freeCell(it) }
                if (free != null) {
                    placed.add(PlacedIcon(options[index].componentName.flattenToString(), free))
                    saveLayout()
                    renderHome()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
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
                .apply()
        }
    }
}
