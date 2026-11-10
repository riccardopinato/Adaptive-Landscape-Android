import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:adaptive_landscape/main.dart';
import 'package:adaptive_landscape/core/landscape_config.dart';
import 'package:adaptive_landscape/core/profile_store.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('Fresh web editor starts on the empty Beach, not Meadow',
      (tester) async {
    SharedPreferences.setMockInitialValues({});
    await tester.pumpWidget(const AdaptiveLandscapeApp());
    await tester.pump(const Duration(milliseconds: 400));
    await tester.pump();
    final oceanStatus = find.byKey(const Key('al-ocean-layout-status'));
    expect(oceanStatus, findsOneWidget);
    expect(find.byKey(const Key('al-only-ocean')), findsOneWidget);
    expect(find.byKey(const Key('al-renderer-status')), findsOneWidget);
    expect(find.byKey(const Key('al-large-ocean-preview')), findsOneWidget);
    expect((await ProfileStore().readProfiles()).single.config.scene, 'beach');
  });

  testWidgets('Old Meadow profile exposes Ocean-only recovery control', (tester) async {
    SharedPreferences.setMockInitialValues({
      'adaptive_landscape_profiles_v2': jsonEncode([
        const NamedLayout(id: 'default', name: 'Home',
          config: LandscapeConfig(scene: 'meadow')).toJson(),
      ]),
      'adaptive_landscape_active_v2': 'default',
    });
    await tester.pumpWidget(const AdaptiveLandscapeApp());
    await tester.pump(const Duration(milliseconds: 400));
    await tester.pump();
    final reset = find.byKey(const Key('al-only-ocean'));
    expect(reset, findsOneWidget);
    await tester.ensureVisible(reset);
    await tester.tap(reset);
    await tester.pump(const Duration(milliseconds: 400));
    await tester.pump();
    expect((await ProfileStore().readProfiles()).single.config.scene, 'beach');
  });

  testWidgets('Ocean-only action clears just selected layout, retains others',
      (tester) async {
    const selected = NamedLayout(id: 'default', name: 'Home',
      config: LandscapeConfig(scene: 'beach', occupied: {1, 3},
        zones: [LayoutZone(x: 0.5, y: 0.8, width: 0.2, height: 0.1, kind: 'dock')]));
    const retained = NamedLayout(id: 'work', name: 'Work',
      config: LandscapeConfig(scene: 'meadow', occupied: {2}));
    SharedPreferences.setMockInitialValues({
      'adaptive_landscape_profiles_v2': jsonEncode([selected.toJson(), retained.toJson()]),
      'adaptive_landscape_active_v2': 'default',
    });
    await tester.pumpWidget(const AdaptiveLandscapeApp());
    await tester.pump(const Duration(milliseconds: 400));
    await tester.pump();
    final reset = find.byKey(const Key('al-only-ocean'));
    expect(reset, findsOneWidget);
    await tester.ensureVisible(reset);
    await tester.tap(reset);
    await tester.pump(const Duration(milliseconds: 400));
    await tester.pump();
    final profiles = await ProfileStore().readProfiles();
    final a = profiles.firstWhere((p) => p.id == 'default').config;
    final b = profiles.firstWhere((p) => p.id == 'work').config;
    expect(a.scene, 'beach');
    expect(a.occupied, isEmpty);
    expect(a.zones, isEmpty);
    expect(b.scene, 'meadow');
    expect(b.occupied, {2});
  });
}
