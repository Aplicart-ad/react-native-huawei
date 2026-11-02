const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

function load() {
  const mods = {};
  const mocks = {};
  for (const name of ['withDangerousMod', 'withProjectBuildGradle', 'withAppBuildGradle', 'withAndroidManifest']) {
    mocks[name] = (config, callback) => { mods[name] = callback; return config; };
  }
  const fakeRequire = name => name === 'expo/config-plugins' ? mocks : name === 'fs'
    ? { ...fs, readFileSync: () => 'agp = "9.0.0"' } : require(name);
  fakeRequire.resolve = () => '/consumer/node_modules/react-native/package.json';
  const module = { exports: {} };
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../plugins/withHmsIapFix.js'), 'utf8'), { require: fakeRequire, module });
  return { plugin: module.exports, mods };
}

test('loads without resolving consumer dependencies; rejects missing configuration', () => {
  const { plugin } = load();
  assert.throws(() => plugin({}), /appId/);
  assert.throws(() => plugin({}, { appId: '123', pushMarker: '' }), /pushMarker/);
});

test('Gradle edits use consumer AGP and remain stable on repeat runs', () => {
  const { plugin, mods } = load();
  plugin({}, { appId: '123' });
  const config = { modRequest: { projectRoot: '/consumer' }, modResults: { contents: "buildscript {\n repositories {\n google()\n }\n dependencies {\n classpath('com.android.tools.build:gradle')\n }\n}\nallprojects {\n repositories {\n google()\n }\n}" } };
  mods.withProjectBuildGradle(config);
  const first = config.modResults.contents;
  assert.match(first, /gradle:9\.0\.0/);
  assert.equal(first.split('https://developer.huawei.com/repo/').length - 1, 2);
  mods.withProjectBuildGradle(config);
  assert.equal(config.modResults.contents, first);
  mods.withAppBuildGradle(config);
  const app = config.modResults.contents;
  mods.withAppBuildGradle(config);
  assert.equal(config.modResults.contents, app);
});

test('updates app ID and push marker without duplicating manifest entries', () => {
  const { plugin, mods } = load();
  plugin({}, { appId: '123', pushMarker: 'custom_push' });
  const config = { modResults: { manifest: { application: [{ 'meta-data': [{ $: { 'android:name': 'com.huawei.hms.client.appid', 'android:value': 'appid=old' } }] }] } } };
  mods.withAndroidManifest(config);
  const first = JSON.stringify(config);
  mods.withAndroidManifest(config);
  assert.equal(JSON.stringify(config), first);
  const entries = config.modResults.manifest.application[0]['meta-data'];
  assert.equal(entries[0].$['android:value'], 'appid=123');
  assert.equal(entries[1].$['android:value'], 'custom_push');
  assert.equal(config.modResults.manifest['uses-permission'][0].$['tools:node'], 'remove');
});
