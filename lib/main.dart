import 'dart:convert';
import 'dart:math' as math;
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'core/icon_field.dart';
import 'core/animation_timing.dart';
import 'core/landscape_config.dart';
import 'core/profile_store.dart';
import 'l10n/app_localizations.dart';
import 'platform/wallpaper_channel.dart';
import 'ui/scene_painter.dart';

void main() => runApp(const AdaptiveLandscapeApp());

class AdaptiveLandscapeApp extends StatelessWidget {
  const AdaptiveLandscapeApp({super.key});
  @override
  Widget build(BuildContext context) => MaterialApp(
    debugShowCheckedModeBanner: false,
    onGenerateTitle: (context) => AppLocalizations.of(context)!.appTitle,
    localizationsDelegates: const [
      AppLocalizations.delegate,
      GlobalMaterialLocalizations.delegate,
      GlobalWidgetsLocalizations.delegate,
      GlobalCupertinoLocalizations.delegate,
    ],
    supportedLocales: AppLocalizations.supportedLocales,
    theme: ThemeData(useMaterial3: true,
      colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xff267981)),
      scaffoldBackgroundColor: const Color(0xfff5f9f8)),
    darkTheme: ThemeData(useMaterial3: true, brightness: Brightness.dark,
      colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xff5bd4cc), brightness: Brightness.dark)),
    home: const WallpaperEditor(),
  );
}

class WallpaperEditor extends StatefulWidget {
  const WallpaperEditor({super.key});
  @override
  State<WallpaperEditor> createState() => _WallpaperEditorState();
}

class _WallpaperEditorState extends State<WallpaperEditor>
    with SingleTickerProviderStateMixin, WidgetsBindingObserver {
  final ProfileStore _store = ProfileStore();
  final WallpaperChannel _channel = WallpaperChannel();
  late final AnimationController _animation;
  LandscapeConfig _config = const LandscapeConfig();
  List<NamedLayout> _profiles = [const NamedLayout(id: 'default', name: 'Home', config: LandscapeConfig())];
  String _activeId = 'default';
  String _mode = 'grid';
  Future<void> _writeChain = Future<void>.value();
  bool _busy = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _animation = AnimationController(vsync: this, duration: const Duration(seconds: 12))..repeat();
    _load();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      _updateTicker();
    } else {
      _animation.stop();
    }
  }

  Future<void> _load() async {
    try {
      final profiles = await _store.readProfiles();
      final selected = await _store.readActiveId();
      final active = profiles.any((p) => p.id == selected) ? selected : profiles.first.id;
      var config = profiles.firstWhere((p) => p.id == active).config;
      if (WallpaperChannel.supported && active == 'default' &&
          config.occupied.isEmpty && config.zones.isEmpty) {
        final legacy = await _channel.read();
        if (legacy.configJson != null && legacy.configJson!.isNotEmpty) {
          config = LandscapeConfig.decode(legacy.configJson!);
        } else {
          config = config.copyWith(scene: legacy.scene, occupied: IconField.decode(legacy.occupied));
        }
      }
      if (!mounted) return;
      setState(() {
        _profiles = profiles; _activeId = active; _config = config;
      });
      _updateTicker();
    } catch (error) {
      if (mounted) setState(() => _error = error.toString());
    }
  }

  void _updateTicker() {
    final duration = motionLoopDuration(_config.motion);
    if (_config.reduceMotion || duration == Duration.zero) {
      _animation.stop();
    } else if (_animation.duration != duration) {
      _animation.duration = duration;
      _animation.repeat();
    } else if (!_animation.isAnimating) {
      _animation.repeat();
    }
  }

  void _apply(LandscapeConfig next, {bool save = true}) {
    setState(() => _config = next);
    _updateTicker();
    if (save) _persist();
  }

  void _persist() {
    final profiles = _profiles.map((p) => p.id == _activeId
        ? NamedLayout(id: p.id, name: p.name, config: _config) : p).toList();
    final active = _activeId;
    final snapshot = _config;
    _profiles = profiles;
    _writeChain = _writeChain.then((_) async {
      await _store.writeProfiles(profiles, active);
      await _channel.save(WallpaperConfig(
        scene: snapshot.scene,
        occupied: IconField.encode(snapshot.occupied),
        configJson: snapshot.encode(),
      ));
    }).catchError((Object error) {
      if (mounted) setState(() => _error = error.toString());
    });
  }

  void _toggleCell(int index) {
    final cells = {..._config.occupied};
    if (!cells.add(index)) cells.remove(index);
    _apply(_config.copyWith(occupied: cells));
  }

  Future<String?> _askText({required String title, String initial = '', String? hint}) async {
    final controller = TextEditingController(text: initial);
    final value = await showDialog<String>(context: context, builder: (dialogContext) => AlertDialog(
      title: Text(title),
      content: TextField(controller: controller, maxLines: hint == null ? 1 : 6,
        maxLength: hint == null ? 48 : 20000, decoration: InputDecoration(hintText: hint),
      ),
      actions: [
        TextButton(onPressed: () => Navigator.pop(dialogContext), child: Text(MaterialLocalizations.of(context).cancelButtonLabel)),
        FilledButton(onPressed: () => Navigator.pop(dialogContext, controller.text), child: const Text('OK')),
      ],
    ));
    controller.dispose();
    return value;
  }

  Future<void> _newProfile() async {
    if (_profiles.length >= 16) return;
    final l = AppLocalizations.of(context)!;
    final name = (await _askText(title: l.newProfile))?.trim();
    if (name == null || name.isEmpty || !mounted) return;
    final id = 'profile-${DateTime.now().microsecondsSinceEpoch}';
    setState(() {
      _profiles = [..._profiles, NamedLayout(id: id, name: name, config: _config)];
      _activeId = id;
    });
    _persist();
  }

  void _selectProfile(String? id) {
    if (id == null || id == _activeId) return;
    final profile = _profiles.where((p) => p.id == id).firstOrNull;
    if (profile == null) return;
    setState(() {
      _activeId = id; _config = profile.config;
    });
    _updateTicker();
    _persist();
  }

  Future<void> _deleteProfile() async {
    if (_profiles.length <= 1) return;
    final l = AppLocalizations.of(context)!;
    final yes = await showDialog<bool>(context: context, builder: (dialogContext) => AlertDialog(
      title: Text(l.deleteProfile),
      content: Text(l.deleteConfirm),
      actions: [
        TextButton(onPressed: () => Navigator.pop(dialogContext, false), child: Text(MaterialLocalizations.of(context).cancelButtonLabel)),
        FilledButton(onPressed: () => Navigator.pop(dialogContext, true), child: const Text('OK')),
      ],
    ));
    if (yes != true || !mounted) return;
    setState(() {
      _profiles = _profiles.where((p) => p.id != _activeId).toList();
      _activeId = _profiles.first.id;
      _config = _profiles.first.config;
    });
    _persist();
  }

  Future<void> _exportProfile() async {
    final l = AppLocalizations.of(context)!;
    final current = _profiles.firstWhere((p) => p.id == _activeId);
    final data = NamedLayout(id: current.id, name: current.name, config: _config).encode();
    await Clipboard.setData(ClipboardData(text: data));
    if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(l.copied)));
  }

  Future<void> _importProfile() async {
    final l = AppLocalizations.of(context)!;
    final input = await _askText(title: l.importProfile, hint: l.pasteJson);
    if (input == null || input.trim().isEmpty || !mounted) return;
    try {
      if (_profiles.length >= 16) throw const FormatException('Maximum 16 profiles');
      final parsed = jsonDecode(input);
      if (parsed is! Map || parsed['config'] is! Map ||
          (parsed['config'] as Map)['schema'] != LandscapeConfig.schemaVersion) {
        throw const FormatException('Unknown profile schema');
      }
      final profile = NamedLayout.fromJson(parsed);
      final id = 'import-${DateTime.now().microsecondsSinceEpoch}';
      setState(() {
        _profiles = [..._profiles, NamedLayout(id: id, name: profile.name, config: profile.config)];
        _activeId = id; _config = profile.config;
      });
      _updateTicker();
      _persist();
    } catch (error) {
      if (mounted) setState(() => _error = error.toString());
    }
  }

  Future<void> _openPicker() async {
    setState(() => _busy = true);
    try {
      _persist();
      await _writeChain;
      await _channel.openLiveWallpaperPicker();
    } catch (error) {
      if (mounted) setState(() => _error = error.toString());
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _animation.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context)!;
    final wide = MediaQuery.sizeOf(context).width >= 800;
    final controls = _controls(l);
    final preview = _preview(l);
    return Scaffold(
      appBar: AppBar(title: Text(l.appTitle),
        actions: [IconButton(tooltip: l.exportProfile, onPressed: _exportProfile, icon: const Icon(Icons.copy_all))]),
      body: SafeArea(child: SingleChildScrollView(
        padding: const EdgeInsets.all(20),
        child: Center(child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 1120),
          child: wide
              ? Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Expanded(flex: 5, child: preview), const SizedBox(width: 24),
                  Expanded(flex: 6, child: controls),
                ])
              : Column(children: [
                  SizedBox(height: 490, child: preview),
                  const SizedBox(height: 20), controls,
                ]),
        )),
      )),
    );
  }

  Widget _controls(AppLocalizations l) {
    final style = Theme.of(context).textTheme;
    Widget section(String label) => Padding(padding: const EdgeInsets.only(top: 22, bottom: 10),
      child: Text(label, style: style.titleMedium));
    return Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
      Text(l.explanation, style: style.bodyMedium),
      section(l.profiles),
      Wrap(spacing: 8, runSpacing: 8, crossAxisAlignment: WrapCrossAlignment.center, children: [
        DropdownButton<String>(
          value: _activeId,
          items: _profiles.map((p) => DropdownMenuItem(value: p.id, child: Text(p.name))).toList(),
          onChanged: _selectProfile,
        ),
        IconButton(tooltip: l.newProfile, onPressed: _newProfile, icon: const Icon(Icons.add_circle_outline)),
        IconButton(tooltip: l.deleteProfile, onPressed: _profiles.length > 1 ? _deleteProfile : null, icon: const Icon(Icons.delete_outline)),
        IconButton(tooltip: l.importProfile, onPressed: _importProfile, icon: const Icon(Icons.file_download_outlined)),
      ]),
      section(l.chooseTheme),
      SegmentedButton<String>(
        segments: [
          ButtonSegment(value: 'meadow', icon: const Icon(Icons.grass), label: Text(l.meadow)),
          ButtonSegment(value: 'beach', icon: const Icon(Icons.beach_access), label: Text(l.beach)),
        ],
        selected: {_config.scene},
        onSelectionChanged: (v) => _apply(_config.copyWith(scene: v.single)),
      ),
      section(l.selectIcons),
      Text(l.selectIconsHint, style: style.bodySmall),
      const SizedBox(height: 8),
      SegmentedButton<String>(
        segments: [
          ButtonSegment(value: 'grid', icon: const Icon(Icons.grid_on), label: Text(l.gridMode)),
          ButtonSegment(value: 'zone', icon: const Icon(Icons.dashboard_customize), label: Text(l.zoneMode)),
        ],
        selected: {_mode},
        onSelectionChanged: (v) => setState(() => _mode = v.single),
      ),
      const SizedBox(height: 8),
      Text(_mode == 'grid' ? l.gridHint : l.zoneHint, style: style.bodySmall),
      if (_mode == 'grid') ...[
        Row(children: [
          Expanded(child: Text('${l.columns}: ${_config.columns}')),
          Expanded(child: Slider(min: 3, max: 8, divisions: 5, value: _config.columns.toDouble(),
            label: '${_config.columns}', onChanged: (v) => _apply(_config.copyWith(columns: v.round(), occupied: {})))),
        ]),
        Row(children: [
          Expanded(child: Text('${l.rows}: ${_config.rows}')),
          Expanded(child: Slider(min: 3, max: 12, divisions: 9, value: _config.rows.toDouble(),
            label: '${_config.rows}', onChanged: (v) => _apply(_config.copyWith(rows: v.round(), occupied: {})))),
        ]),
      ],
      Row(children: [
        Text('${l.occupiedCount}: ${_config.occupied.length}'),
        const Spacer(),
        TextButton.icon(onPressed: () => _apply(_config.copyWith(occupied: {}, zones: [])),
          icon: const Icon(Icons.restart_alt), label: Text(l.reset)),
        if (_mode == 'zone') TextButton(onPressed: _config.zones.isEmpty ? null : () {
          _apply(_config.copyWith(zones: [..._config.zones]..removeLast()));
        }, child: Text(l.removeZone)),
      ]),
      section(l.appearance),
      Wrap(spacing: 8, children: [
        ChoiceChip(label: Text(l.original), selected: _config.palette == 'original',
          onSelected: (_) => _apply(_config.copyWith(palette: 'original'))),
        ChoiceChip(label: Text(l.sunset), selected: _config.palette == 'sunset',
          onSelected: (_) => _apply(_config.copyWith(palette: 'sunset'))),
        ChoiceChip(label: Text(l.mint), selected: _config.palette == 'mint',
          onSelected: (_) => _apply(_config.copyWith(palette: 'mint'))),
      ]),
      SwitchListTile(contentPadding: EdgeInsets.zero, title: Text(l.night),
        value: _config.night, onChanged: (v) => _apply(_config.copyWith(night: v))),
      SwitchListTile(contentPadding: EdgeInsets.zero, title: Text(l.reduceMotion),
        value: _config.reduceMotion, onChanged: (v) => _apply(_config.copyWith(reduceMotion: v))),
      Text('${l.motion}: ${_config.motion.toStringAsFixed(1)}×'),
      Slider(min: 0, max: 2, divisions: 20, value: _config.motion,
        onChanged: (v) => _apply(_config.copyWith(motion: v))),
      const SizedBox(height: 18),
      FilledButton.icon(onPressed: _busy || !WallpaperChannel.supported ? null : _openPicker,
        icon: const Icon(Icons.wallpaper), label: Text(l.install)),
      const SizedBox(height: 10),
      Text(WallpaperChannel.supported ? l.wallpaperHint : l.unsupported, style: style.bodySmall),
      if (_error != null) SelectableText('${l.failure}: $_error',
        style: TextStyle(color: Theme.of(context).colorScheme.error)),
    ]);
  }

  Widget _preview(AppLocalizations l) => Center(child: AspectRatio(
    aspectRatio: 9 / 19.5,
    child: ClipRRect(borderRadius: BorderRadius.circular(28),
      child: LayoutBuilder(builder: (context, constraints) {
        final w = constraints.maxWidth, h = constraints.maxHeight;
        void handleTap(TapDownDetails d) {
          final x = d.localPosition.dx / w, y = d.localPosition.dy / h;
          if (_mode == 'zone') {
            if (_config.zones.length < 80) {
              _apply(_config.copyWith(zones: [..._config.zones,
                LayoutZone(x: x, y: y, width: 0.27, height: 0.12, kind: 'widget')]));
            }
            return;
          }
          final col = (x * _config.columns).floor().clamp(0, _config.columns - 1);
          final row = ((y - _config.top) / _config.span * _config.rows).floor();
          if (row < 0 || row >= _config.rows) return;
          _toggleCell(row * _config.columns + col);
        }

        return GestureDetector(
          behavior: HitTestBehavior.opaque, onTapDown: handleTap,
          child: AnimatedBuilder(animation: _animation, builder: (context, _) => Stack(children: [
            Positioned.fill(child: CustomPaint(painter: ScenePainter(
              scene: _config.scene, field: IconField.fromConfig(_config),
              night: _config.night, palette: _config.palette,
              phase: _config.reduceMotion ? 0 :
                  ((_animation.value * 240).floor() / 240) * math.pi * 2,
            ))),
            if (_mode == 'grid') ...List.generate(_config.cellCount, (i) {
              final (x, y) = _config.cellCenter(i);
              final selected = _config.occupied.contains(i);
              return Positioned(
                left: (x - 0.42 / _config.columns) * w,
                top: (y - 0.42 * _config.span / _config.rows) * h,
                width: 0.84 * w / _config.columns,
                height: 0.84 * h * _config.span / _config.rows,
                child: IgnorePointer(child: DecoratedBox(
                  decoration: BoxDecoration(borderRadius: BorderRadius.circular(9),
                    border: Border.all(color: Colors.white.withValues(alpha: selected ? 0.9 : 0.4)),
                    color: Colors.white.withValues(alpha: selected ? 0.52 : 0.09)),
                  child: selected ? Icon(Icons.apps, color: const Color(0xff205961), size: w * 0.075) : null,
                )),
              );
            }),
            if (_mode == 'zone') ...List.generate(_config.zones.length, (i) {
              final zone = _config.zones[i];
              return Positioned(
                left: (zone.x - zone.width / 2) * w,
                top: (zone.y - zone.height / 2) * h,
                width: zone.width * w, height: zone.height * h,
                child: GestureDetector(
                  behavior: HitTestBehavior.opaque,
                  onPanUpdate: (d) {
                    final items = [..._config.zones];
                    items[i] = items[i].move(d.delta.dx / w, d.delta.dy / h);
                    _apply(_config.copyWith(zones: items), save: false);
                  },
                  onPanEnd: (_) => _persist(),
                  child: DecoratedBox(
                    decoration: BoxDecoration(border: Border.all(color: Colors.white, width: 2),
                      color: Colors.black.withValues(alpha: 0.24), borderRadius: BorderRadius.circular(10)),
                    child: Stack(children: [
                      Center(child: Text(zone.kind == 'dock' ? l.dock : l.widgetZone,
                        textAlign: TextAlign.center, style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w600))),
                      Positioned(right: 0, bottom: 0, child: GestureDetector(
                        behavior: HitTestBehavior.opaque,
                        onPanUpdate: (d) {
                          final items = [..._config.zones];
                          items[i] = items[i].resize(d.delta.dx / w, d.delta.dy / h);
                          _apply(_config.copyWith(zones: items), save: false);
                        },
                        onPanEnd: (_) => _persist(),
                        child: const Icon(Icons.open_in_full, color: Colors.white, size: 22),
                      )),
                    ]),
                  ),
                ),
              );
            }),
            Positioned(top: 12, left: 0, right: 0,
              child: IgnorePointer(child: Center(child: Text('9:41',
                style: TextStyle(fontSize: w * 0.075, color: const Color(0xff204d59),
                  fontWeight: FontWeight.w300))))),
          ])),
        );
      })),
  ));
}
