// Run with: node docs/design/verify-native-adapter.cjs
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const assets = path.join(__dirname, '../../app/src/main/assets');
assert.equal(fs.readFileSync(path.join(assets, 'route-recommendation.html'), 'utf8'),
  fs.readFileSync(path.join(__dirname, 'route-recommendation.html'), 'utf8'));
for (const page of ['fun-stop-games.html', 'fun-challenges.html', 'trip-card.html']) {
  assert.equal(fs.readFileSync(path.join(assets, page), 'utf8'), fs.readFileSync(path.join(__dirname, page), 'utf8'));
}
for (const script of fs.readdirSync(path.join(__dirname, 'js')).filter(name => name.endsWith('.js'))) {
  assert.equal(fs.readFileSync(path.join(assets, 'js', script), 'utf8'), fs.readFileSync(path.join(__dirname, 'js', script), 'utf8'));
}
const calls = [], storage = new Map();
const viewportMeta = { content: '' };
const window = { devicePixelRatio: 2, CoDrive: {
  viewport: () => '{"width":829,"density":2}',
  readState: () => JSON.stringify({ route: '{"stops":[]}', conversation: '[]' }),
  startSpeech: id => calls.push(['start', id]), cancelSpeech: id => calls.push(['cancel', id])
} };
vm.runInNewContext(fs.readFileSync(path.join(assets, 'native-adapter.js'), 'utf8'), {
  window, document: { querySelector: () => viewportMeta }, sessionStorage: { setItem: (key, value) => storage.set(key, value) }
});
assert.equal(viewportMeta.content, 'width=829, initial-scale=1');
assert.equal(storage.get('codrive.recommendation.conversation.v1'), '[]');
const recognition = new window.SpeechRecognition();
const events = [];
recognition.onstart = () => events.push('start');
recognition.onresult = event => events.push([event.results[0][0].transcript, event.results[0].isFinal]);
recognition.onend = () => events.push('end');
recognition.start();
window.CoDriveSpeech.dispatch(1, 'start', {});
window.CoDriveSpeech.dispatch(1, 'result', { text: '카페', complete: false });
window.CoDriveSpeech.dispatch(1, 'result', { text: '카페를 추천해줘', complete: true });
window.CoDriveSpeech.dispatch(1, 'end', {});
window.CoDriveSpeech.dispatch(1, 'result', { text: '늦게 도착한 결과', complete: true });
assert.deepEqual(events, ['start', ['카페', false], ['카페를 추천해줘', true], 'end']);
recognition.start();
recognition.abort();
window.CoDriveSpeech.dispatch(2, 'result', { text: '취소된 결과', complete: true });
assert.deepEqual(calls, [['start', 1], ['start', 2], ['cancel', 2]]);
assert.equal(events.length, 5);
console.log('PASS: identical bundled design, native checkpoint restore, partial/final speech and late-result cancellation');
