import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:adaptive_landscape/core/profile_store.dart';
import 'package:adaptive_landscape/core/landscape_config.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUp(() => SharedPreferences.setMockInitialValues({}));

  test('Fresh install creates one default profile', () async {
    final store = ProfileStore();
    final profiles = await store.readProfiles();
    expect(profiles.single.name, 'Home');
    expect(profiles.single.config.scene, 'beach');
    expect(profiles.single.config.occupied, isEmpty);
    expect(profiles.single.config.zones, isEmpty);
    expect(await store.readActiveId(), 'default');
  });

  test('Multiple profiles persist with active selection', () async {
    final store = ProfileStore();
    final profiles = [
      const NamedLayout(id: 'default', name: 'Home', config: LandscapeConfig()),
      const NamedLayout(id: 'secondary', name: 'Travel', config: LandscapeConfig(scene: 'beach')),
    ];
    await store.writeProfiles(profiles, 'secondary');
    expect((await store.readProfiles()).length, 2);
    expect((await store.readProfiles()).last.config.scene, 'beach');
    expect(await store.readActiveId(), 'secondary');
  });
  test('Invalid IDs cannot silently lose the active selection', () async {
    final store = ProfileStore();
    final p = [const NamedLayout(id: '', name: 'Home', config: LandscapeConfig())];
    await expectLater(store.writeProfiles(p, ''), throwsArgumentError);
    final longId = 'a' * 65;
    await expectLater(store.writeProfiles([
      NamedLayout(id: longId, name: 'Home', config: const LandscapeConfig()),
    ], longId), throwsArgumentError);
  });

  test('Cannot remove every profile', () async {
    expect(() => ProfileStore().writeProfiles([], 'default'), throwsArgumentError);
  });
}
