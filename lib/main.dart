import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'l10n/app_localizations.dart';
import 'core/icon_field.dart';
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
        theme: ThemeData(
          useMaterial3: true,
          colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xff287d86)),
          scaffoldBackgroundColor: const Color(0xfff6faf9),
        ),
        darkTheme: ThemeData(
          useMaterial3: true,
          brightness: Brightness.dark,
          colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xff5bd4cc), brightness: Brightness.dark),
        ),
        home: const WallpaperEditor(),
      );
}

class WallpaperEditor extends StatefulWidget {
  const WallpaperEditor({super.key});
  @override
  State<WallpaperEditor> createState() => _WallpaperEditorState();
}

class _WallpaperEditorState extends State<WallpaperEditor> with SingleTickerProviderStateMixin {
  final WallpaperChannel _channel = WallpaperChannel();
  late final AnimationController _animation;
  String _scene = 'meadow';
  Set<int> _occupied = {};
  bool _busy = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _animation = AnimationController(vsync: this, duration: const Duration(seconds: 12))..repeat();
    _load();
  }

  Future<void> _load() async {
    try {
      final config = await _channel.read();
      if (!mounted) return;
      setState(() {
        _scene = config.scene;
        _occupied = IconField.decode(config.occupied);
      });
    } catch (error) {
      if (mounted) setState(() => _error = error.toString());
    }
  }

  Future<void> _save() async {
    try {
      await _channel.save(WallpaperConfig(
        scene: _scene,
        occupied: IconField.encode(_occupied),
      ));
      if (mounted) setState(() => _error = null);
    } catch (error) {
      if (mounted) setState(() => _error = error.toString());
    }
  }

  void _toggle(int index) {
    setState(() {
      if (!_occupied.add(index)) _occupied.remove(index);
    });
    _save();
  }

  Future<void> _openPicker() async {
    setState(() => _busy = true);
    try {
      await _save();
      await _channel.openLiveWallpaperPicker();
    } catch (error) {
      if (mounted) setState(() => _error = error.toString());
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  void dispose() {
    _animation.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context)!;
    return Scaffold(
      appBar: AppBar(title: Text(l.appTitle)),
      body: SafeArea(
        child: LayoutBuilder(builder: (context, constraints) {
          final wide = constraints.maxWidth > 750;
          final editor = _preview();
          final controls = Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(l.explanation, style: Theme.of(context).textTheme.bodyMedium),
              const SizedBox(height: 20),
              Text(l.chooseTheme, style: Theme.of(context).textTheme.titleMedium),
              const SizedBox(height: 10),
              SegmentedButton<String>(
                segments: [
                  ButtonSegment(value: 'meadow', icon: const Icon(Icons.grass), label: Text(l.meadow)),
                  ButtonSegment(value: 'beach', icon: const Icon(Icons.beach_access), label: Text(l.beach)),
                ],
                selected: {_scene},
                onSelectionChanged: (value) { setState(() => _scene = value.single); _save(); },
              ),
              const SizedBox(height: 24),
              Text(l.selectIcons, style: Theme.of(context).textTheme.titleMedium),
              const SizedBox(height: 8),
              Text(l.selectIconsHint),
              const SizedBox(height: 8),
              Text('${l.occupiedCount}: ${_occupied.length}'),
              TextButton.icon(
                onPressed: () { setState(() => _occupied = {}); _save(); },
                icon: const Icon(Icons.restart_alt),
                label: Text(l.reset),
              ),
              const SizedBox(height: 12),
              FilledButton.icon(
                onPressed: _busy || !WallpaperChannel.supported ? null : _openPicker,
                icon: const Icon(Icons.wallpaper),
                label: Text(l.install),
              ),
              const SizedBox(height: 12),
              Text(l.wallpaperHint, style: Theme.of(context).textTheme.bodySmall),
              if (!WallpaperChannel.supported) ...[
                const SizedBox(height: 8),
                Text(l.unsupported, style: TextStyle(color: Theme.of(context).colorScheme.error)),
              ],
              if (_error != null) ...[
                const SizedBox(height: 8),
                SelectableText('${l.failure}: $_error', style: TextStyle(color: Theme.of(context).colorScheme.error)),
              ],
            ],
          );
          return SingleChildScrollView(
            padding: const EdgeInsets.all(22),
            child: Center(
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 900),
                child: wide
                    ? Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                        Expanded(child: editor),
                        const SizedBox(width: 32),
                        Expanded(child: controls),
                      ])
                    : Column(children: [SizedBox(height: 520, child: editor), const SizedBox(height: 22), controls]),
              ),
            ),
          );
        }),
      ),
    );
  }

  Widget _preview() => Center(
        child: AspectRatio(
          aspectRatio: 9 / 19.5,
          child: ClipRRect(
            borderRadius: BorderRadius.circular(28),
            child: LayoutBuilder(builder: (context, constraints) {
              final w = constraints.maxWidth;
              final h = constraints.maxHeight;
              return GestureDetector(
                behavior: HitTestBehavior.opaque,
                onTapDown: (details) {
                  final dx = details.localPosition.dx / w;
                  final dy = details.localPosition.dy / h;
                  final col = (dx * IconField.columns).floor().clamp(0, IconField.columns - 1).toInt();
                  final row = ((dy - 0.115) / 0.765 * IconField.rows).floor();
                  if (row < 0 || row >= IconField.rows) return;
                  _toggle(row * IconField.columns + col);
                },
                child: AnimatedBuilder(
                  animation: _animation,
                  builder: (context, child) => Stack(children: [
                    Positioned.fill(
                      child: CustomPaint(
                        painter: ScenePainter(scene: _scene, field: IconField(occupied: _occupied), phase: _animation.value * 6.28),
                      ),
                    ),
                    ...List.generate(IconField.cellCount, (index) {
                      final (x, y) = IconField.centerFor(index);
                      final selected = _occupied.contains(index);
                      return Positioned(
                        left: x * w - w * 0.075,
                        top: y * h - w * 0.075,
                        width: w * 0.15,
                        height: w * 0.15,
                        child: DecoratedBox(
                          decoration: BoxDecoration(
                            borderRadius: BorderRadius.circular(11),
                            color: selected ? Colors.white.withValues(alpha: 0.8) : Colors.white.withValues(alpha: 0.1),
                            border: Border.all(color: Colors.white.withValues(alpha: selected ? 0.9 : 0.14)),
                          ),
                          child: selected ? const Icon(Icons.apps, color: Color(0xff376c71)) : null,
                        ),
                      );
                    }),
                    Positioned(top: 12, left: 0, right: 0, child: Center(child: Text('9:41', style: TextStyle(fontSize: w * 0.08, color: const Color(0xff204d59), fontWeight: FontWeight.w300)))),
                  ]),
                ),
              );
            }),
          ),
        ),
      );
}
