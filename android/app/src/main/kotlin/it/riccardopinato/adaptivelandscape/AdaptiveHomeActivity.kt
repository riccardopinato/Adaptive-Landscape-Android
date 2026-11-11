package it.riccardopinato.adaptivelandscape

import android.app.Activity
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
    private val maxSlots = columns * rows

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        loadLaunchableApps()
        restoreSavedLayout()
        canvas.post { renderHome() }
    }

    override fun onPause() {
        handler.removeCallbacks(publishPending)
        if (isSelectedHome()) publishHomePositions()
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
            launcherApps.profiles.flatMap { user ->
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
        val col = cell % columns
        val row = cell / columns
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
        val byId = available.associateBy { it.componentName.flattenToString() }
        for (item in placed) {
            val info = byId[item.id] ?: continue
            val tile = makeTile(info)
            canvas.addView(tile, FrameLayout.LayoutParams(iconWidth(), iconHeight()))
            tile.x = leftFor(item.cell)
            tile.y = topFor(item.cell)
            views[item.id] = tile
            attachTouch(tile, item.id, info)
        }
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
                    } else if (!cancelledByGesture) {
                        // Supports TalkBack and keyboard activation through click semantics.
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
        val dest = row * columns + col
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
        val top = chromeButton(getString(R.string.auto_home_caption)) {
            startActivity(Intent(this, MainActivity::class.java))
        }
        canvas.addView(top, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, (40 * dp).roundToInt(),
            Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                topMargin = (40 * dp).roundToInt()
            })
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
                val used = placed.map { it.cell }.toSet()
                val free = (0 until maxSlots).firstOrNull { it !in used }
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
