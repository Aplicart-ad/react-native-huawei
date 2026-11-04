const {
  withDangerousMod,
  withProjectBuildGradle,
  withAppBuildGradle,
  withAndroidManifest,
} = require('expo/config-plugins');
const fs = require('fs');
const path = require('path');

const HUAWEI_REPO = "maven { url 'https://developer.huawei.com/repo/' }";
const AGCONNECT_CLASSPATH = "classpath('com.huawei.agconnect:agcp:1.9.6.300')";

/**
 * The agcp plugin needs AGP on the classpath with an explicit version, but the generated
 * android/build.gradle declares it versionless (React Native's gradle plugin supplies the
 * version). So the version is read from the same place React Native takes it - its version
 * catalog - rather than hardcoded. A hardcoded 8.6.1 survived until the SDK 58 bump, where it
 * would have silently downgraded a project whose toolchain had moved to AGP 9.
 */
function readReactNativeAgpVersion(projectRoot) {
  // Via package.json: react-native's `exports` map does not expose the gradle directory.
  const reactNativeRoot = path.dirname(require.resolve('react-native/package.json', { paths: [projectRoot] }));
  const catalog = fs.readFileSync(
    path.join(reactNativeRoot, 'gradle', 'libs.versions.toml'),
    'utf8'
  );
  const match = catalog.match(/^agp\s*=\s*"([^"]+)"/m);
  if (!match) {
    throw new Error('[withHmsIapFix] no `agp` entry in react-native/gradle/libs.versions.toml');
  }
  return match[1];
}

function ensureInsertedOnce(contents, needle, insertion) {
  if (contents.includes(needle)) {
    return contents;
  }
  return insertion(contents);
}

function addHuaweiReposToProjectGradle(contents) {
  let updated = contents;

  updated = ensureInsertedOnce(
    updated,
    "maven { url 'https://developer.huawei.com/repo/' }",
    (c) =>
      c.replace(
        /buildscript\s*\{\s*repositories\s*\{/,
        `buildscript {\n    repositories {\n        ${HUAWEI_REPO}`
      )
  );

  updated = ensureInsertedOnce(
    updated,
    "allprojects {\n    repositories {\n        maven { url 'https://developer.huawei.com/repo/' }",
    (c) =>
      c.replace(
        /allprojects\s*\{\s*repositories\s*\{/,
        `allprojects {\n    repositories {\n        ${HUAWEI_REPO}`
      )
  );

  return updated;
}

function addHuaweiClasspathsToProjectGradle(contents, projectRoot) {
  const ANDROID_GRADLE_PLUGIN_CLASSPATH =
    `classpath('com.android.tools.build:gradle:${readReactNativeAgpVersion(projectRoot)}')`;
  let updated = contents;

  const hasAgconnect = updated.includes('com.huawei.agconnect:agcp');
  const hasAgpWithVersion = updated.includes('com.android.tools.build:gradle:');
  const hasAgpWithoutVersion = !hasAgpWithVersion && updated.includes('com.android.tools.build:gradle');

  // Huawei agcp plugin requires an explicit version — replace the versionless line
  if (hasAgpWithoutVersion) {
    updated = updated.replace(
      /classpath\(['"]com\.android\.tools\.build:gradle['"]\)/,
      ANDROID_GRADLE_PLUGIN_CLASSPATH
    );
  }

  if (!hasAgpWithVersion && !hasAgpWithoutVersion) {
    updated = updated.replace(
      /buildscript\s*\{([\s\S]*?)dependencies\s*\{/,
      (match) => `${match}\n        ${ANDROID_GRADLE_PLUGIN_CLASSPATH}`
    );
  }

  if (!hasAgconnect) {
    updated = updated.replace(
      /buildscript\s*\{([\s\S]*?)dependencies\s*\{/,
      (match) => `${match}\n        ${AGCONNECT_CLASSPATH}`
    );
  }

  return updated;
}

function applyHuaweiPluginToAppGradle(contents) {
  if (contents.includes("apply plugin: 'com.huawei.agconnect'")) {
    return contents;
  }

  return `${contents}\napply plugin: 'com.huawei.agconnect'\n`;
}

function copyAgconnectServicesJson(projectRoot) {
  const src = path.join(projectRoot, 'agconnect-services.json');
  const dest = path.join(projectRoot, 'android', 'app', 'agconnect-services.json');

  if (!fs.existsSync(src)) {
    throw new Error(
      'Missing agconnect-services.json at project root. Download it from AppGallery Connect and place it next to app.json.'
    );
  }

  fs.copyFileSync(src, dest);
}

const withHmsIapFix = (config, { appId, pushMarker = 'huawei_push' } = {}) => {
  if (typeof appId !== 'string' || !/^\d+$/.test(appId)) {
    throw new Error('[react-native-huawei] appId must be your numeric AppGallery app ID as a string.');
  }
  if (typeof pushMarker !== 'string' || !pushMarker.trim()) {
    throw new Error('[react-native-huawei] pushMarker must be a nonempty string.');
  }
  config = withDangerousMod(config, [
    'android',
    async (config) => {
      const projectRoot = config.modRequest.projectRoot;

      copyAgconnectServicesJson(projectRoot);

      return config;
    },
  ]);

  config = withProjectBuildGradle(config, (config) => {
    let contents = config.modResults.contents;

    contents = addHuaweiReposToProjectGradle(contents);
    contents = addHuaweiClasspathsToProjectGradle(contents, config.modRequest.projectRoot);

    config.modResults.contents = contents;
    return config;
  });

  config = withAppBuildGradle(config, (config) => {
    config.modResults.contents = applyHuaweiPluginToAppGradle(
      config.modResults.contents
    );
    return config;
  });

  config = withAndroidManifest(config, (config) => {
    const manifest = config.modResults.manifest;
    const app = manifest.application?.[0];

    if (!app) {
      return config;
    }

    // Root manifest namespace
    manifest.$ = manifest.$ || {};
    manifest.$['xmlns:tools'] = 'http://schemas.android.com/tools';

    // HMS appservice SDK bundles REQUEST_INSTALL_PACKAGES — remove it from
    // the merged manifest so Google Play doesn't reject the build.
    const permissions = manifest['uses-permission'] || [];
    const alreadyRemoved = permissions.some(
      (p) =>
        p.$?.['android:name'] === 'android.permission.REQUEST_INSTALL_PACKAGES' &&
        p.$?.['tools:node'] === 'remove'
    );
    if (!alreadyRemoved) {
      permissions.push({
        $: {
          'android:name': 'android.permission.REQUEST_INSTALL_PACKAGES',
          'tools:node': 'remove',
        },
      });
    }
    manifest['uses-permission'] = permissions;

    // App attributes
    app.$ = app.$ || {};

    const existingToolsReplace = app.$['tools:replace'];
    if (!existingToolsReplace) {
      app.$['tools:replace'] = 'android:allowBackup';
    } else if (!existingToolsReplace.includes('android:allowBackup')) {
      app.$['tools:replace'] = `${existingToolsReplace},android:allowBackup`;
    }

    // Huawei app id metadata
    if (appId) {
      const metaData = app['meta-data'] || [];
      const metaName = 'com.huawei.hms.client.appid';

      const existing = metaData.find((item) => item.$?.['android:name'] === metaName);
      if (existing) existing.$['android:value'] = `appid=${appId}`;
      if (!existing) {
        metaData.push({
          $: {
            'android:name': metaName,
            'android:value': `appid=${appId}`,
          },
        });
      }

      const markerName = 'com.aplicart.huawei.PUSH_MARKER';
      const marker = metaData.find((item) => item.$?.['android:name'] === markerName);
      if (marker) marker.$['android:value'] = pushMarker;
      else metaData.push({ $: { 'android:name': markerName, 'android:value': pushMarker } });
      app['meta-data'] = metaData;
    }

    return config;
  });

  return config;
};

module.exports = withHmsIapFix;