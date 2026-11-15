const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
class Storage {
  get length() { return 0; }
  getItem() { return null; }
  key() { return null; }
  setItem() {}
  removeItem() {}
  clear() {}
}
let snapshot;
const nodes = {
  'route-active': { hidden: false }, 'route-loading': { hidden: true },
  'route-owner': { textContent: ' 주말 여행 ' }, 'route-source': { textContent: '내비게이션 경로' },
  'route-timeline': { children: [] }
};
const point = (label, name) => ({ querySelector: selector => ({ textContent: selector === '.route-point-label' ? label : name }) });
const host = { readWebState: () => '{}', saveWebState() {}, viewport: () => '{"width":1682,"density":1}',
  saveRoutePreview: json => { snapshot = JSON.parse(json); } };
const window = { CoDrive: host, devicePixelRatio: 1, dispatchEvent() {} };
const document = { getElementById: id => nodes[id] ?? null, querySelector: () => ({ content: '' }), addEventListener() {} };
vm.runInNewContext(fs.readFileSync(path.resolve(__dirname, '../../main/assets/app-navigation.js'), 'utf8'),
  { window, document, Storage, sessionStorage: new Storage(), Event: class {} });
nodes['route-timeline'].children = [point('현재 위치', '대전 유성구'), point('경유지 1', '호숫가 식당'), point('목적지', '전망 카페')];
window.CoDriveSavePage();
assert.equal(snapshot.status, 'active'); assert.equal(snapshot.owner, '주말 여행');
assert.deepEqual(snapshot.steps.map(step => step.name), ['대전 유성구', '호숫가 식당', '전망 카페']);
nodes['route-active'].hidden = true; nodes['route-loading'].hidden = false;
window.CoDriveSavePage(); assert.equal(snapshot.status, 'loading'); assert.deepEqual(snapshot.steps, []);
nodes['route-loading'].hidden = true;
window.CoDriveSavePage(); assert.equal(snapshot.status, 'empty'); assert.deepEqual(snapshot.steps, []);
nodes['route-active'].hidden = false; nodes['route-timeline'].children = [];
window.CoDriveSavePage(); assert.equal(snapshot.status, 'empty');
snapshot = null; delete nodes['route-timeline'];
window.CoDriveSavePage(); assert.equal(snapshot, null);
console.log('Route preview bridge: active / loading / empty / missing-panel cases passed.');
