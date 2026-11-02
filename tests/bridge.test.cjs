const { test } = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');
function load(platform, bridge) {
  const subscriptions = [];
  const module = { exports: {} };
  const js = ts.transpileModule(fs.readFileSync(path.join(__dirname, '../src/HuaweiModule.ts'), 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS },
  }).outputText;
  vm.runInNewContext(js, {
    module, exports: module.exports,
    require: name => {
      assert.equal(name, 'react-native');
      return {
        Platform: { OS: platform }, NativeModules: { Huawei: bridge },
        NativeEventEmitter: class {
          addListener(event, listener) {
            const subscription = { event, listener, remove() {} };
            subscriptions.push(subscription);
            return subscription;
          }
        },
      };
    },
  });
  return { ...module.exports, subscriptions };
}
test('requires Android and a linked module only when called', () => {
  assert.throws(() => load('ios').getHuaweiModule(), /only available on Android/);
  assert.throws(() => load('android').getHuaweiModule(), /not linked/);
});
test('forwards calls, nullable optional arguments, synchronous data and events', () => {
  let product;
  const payload = { route: 'inbox' };
  const bridge = {
    getPushToken: async () => 'test-token',
    startManageSubscriptionActivity: id => { product = id; },
    getInitialPushData: () => payload,
  };
  const loaded = load('android', bridge);
  const module = loaded.getHuaweiModule();
  module.startManageSubscriptionActivity();
  assert.equal(product, null);
  assert.equal(module.getInitialPushData(), payload);
  let token;
  const sub = module.addListener('onPushToken', event => { token = event.token; });
  loaded.subscriptions[0].listener({ token: 'test-token' });
  assert.equal(token, 'test-token');
  assert.equal(typeof sub.remove, 'function');
});
