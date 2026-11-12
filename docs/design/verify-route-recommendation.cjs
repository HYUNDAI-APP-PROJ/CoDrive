// Run with: node docs/design/verify-route-recommendation.cjs
// Exercise speech events without recording audio or accessing a speech service.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const html = fs.readFileSync(path.join(__dirname, 'route-recommendation.html'), 'utf8');
const script = html.match(/<script>([\s\S]*?)<\/script>/)[1];

function setup({ supported = true, initial = '', storage = new Map(), storageFailure = false, search = '' } = {}) {
  const elements = new Map();
  const pageEvents = {};
  let latest;
  let nextNode = 0;
  function element(id) {
    if (!elements.has(id)) {
      const attributes = new Map();
      const classes = new Set();
      elements.set(id, {
        value: id === 'prompt' ? initial : '',
        maxLength: 1000,
        hidden: false,
        disabled: id === 'send-button',
        textContent: '',
        offsetWidth: 100,
        events: {},
        children: [],
        dataset: id.startsWith('mode-') ? { mode: id.slice(5) } : {},
        scrollHeight: 900,
        content: { cloneNode: () => ({ querySelector: () => element('reply-copy-' + nextNode++) }) },
        classList: {
          add: (...names) => names.forEach(name => classes.add(name)),
          remove: (...names) => names.forEach(name => classes.delete(name)),
          contains: name => classes.has(name)
        },
        getAttribute: name => attributes.get(name),
        setAttribute: (name, value) => attributes.set(name, value),
        addEventListener(name, listener) { this.events[name] = listener; },
        append(...nodes) { this.children.push(...nodes); },
        replaceChildren(...nodes) { this.children = [...nodes]; },
        scrollTo(options) { this.lastScroll = options; },
        focus() {},
        scrollIntoView() {}
      });
    }
    return elements.get(id);
  }
  class FakeRecognition {
    constructor() { latest = this; }
    start() { this.onstart(); }
    stop() { this.stopped = true; }
    abort() { this.aborted = true; this.onend(); }
  }
  const window = {
    location: { search },
    addEventListener: (name, listener) => { pageEvents[name] = listener; },
    sessionStorage: {
      getItem(name) { if (storageFailure) throw Error('Storage unavailable'); return storage.get(name) || null; },
      setItem(name, value) { if (storageFailure) throw Error('Storage unavailable'); storage.set(name, value); }
    }
  };
  if (supported) window.SpeechRecognition = FakeRecognition;
  vm.runInNewContext(script, {
    window,
    URLSearchParams,
    document: {
      getElementById: element,
      createElement: () => element('node-' + nextNode++),
      querySelectorAll: selector => selector === '[data-mode]' ? [element('mode-personal'), element('mode-group')] : []
    },
    setTimeout: () => 1,
    clearTimeout() {}
  });
  return {
    element,
    click: id => element(id).events.click(),
    submit: () => element('prompt-form').events.submit({ preventDefault() {} }),
    createGroup: name => {
      element('group-name').value = name;
      element('group-create-form').events.submit({ preventDefault() {} });
    },
    input: value => { element('prompt').value = value; element('prompt').events.input(); },
    recognition: () => latest,
    storage,
    pageEvents
  };
}

function result(recognition, transcript, isFinal) {
  const item = [{ transcript }];
  item.isFinal = isFinal;
  recognition.onresult({ results: [item] });
}

// Speech end sends exactly once, records the full request, and clears the input.
const normal = setup({ initial: '대전 근교' });
normal.click('voice-button');
const first = normal.recognition();
assert.equal(first.lang, 'ko-KR');
assert.equal(first.continuous, false);
assert.equal(normal.element('send-button').disabled, true);
assert.equal(normal.element('voice-button').classList.contains('recording'), true);
result(first, '조용한 카페', false);
assert.equal(normal.element('prompt').value, '대전 근교 조용한 카페');
assert.equal(normal.element('send-button').disabled, true);
first.onspeechend();
assert.equal(first.stopped, true);
result(first, '조용한 카페 추천해줘', true);
first.onend();
assert.equal(normal.element('prompt').value, '');
assert.equal(normal.element('send-button').disabled, true);
assert.equal(normal.element('voice-button').getAttribute('aria-pressed'), 'false');
assert.equal(normal.element('conversation').children.length, 1);
assert.equal(normal.element('conversation').children[0].children[0].children[1].textContent, '대전 근교 조용한 카페 추천해줘');
first.onend();
assert.equal(normal.element('conversation').children.length, 1);

// A second click cancels; late results cannot overwrite the restored input.
normal.input('다음 요청 초안');
normal.click('voice-button');
const cancelled = normal.recognition();
result(cancelled, '취소할 내용', false);
normal.click('voice-button');
assert.equal(cancelled.aborted, true);
result(cancelled, '늦게 도착한 내용', true);
cancelled.onend();
assert.equal(normal.element('prompt').value, '다음 요청 초안');
assert.equal(normal.element('conversation').children.length, 1);

// Typed edits interrupt recognition without deleting the edit.
normal.click('voice-button');
const interrupted = normal.recognition();
normal.input('직접 수정한 요청');
result(interrupted, '지연된 인식 결과', true);
assert.equal(normal.element('prompt').value, '직접 수정한 요청');
normal.submit();
assert.equal(normal.element('prompt').value, '');
assert.equal(normal.element('send-button').disabled, true);
assert.equal(normal.element('conversation').children.length, 2);
assert.equal(normal.element('conversation').children[0].children[0].children[1].textContent, '대전 근교 조용한 카페 추천해줘');
assert.equal(normal.element('conversation').children[1].children[0].children[1].textContent, '직접 수정한 요청');
normal.submit();
assert.equal(normal.element('conversation').children.length, 2);

const reloaded = setup({ storage: normal.storage });
assert.equal(reloaded.element('conversation').children.length, 2);
assert.equal(reloaded.element('conversation').children[1].children[0].children[1].textContent, '직접 수정한 요청');

const noStorage = setup({ storageFailure: true });
noStorage.input('  줄바꿈\n<img src=x onerror=alert(1)>  ');
noStorage.submit();
assert.equal(noStorage.element('prompt').value, '');
assert.equal(noStorage.element('conversation').children[0].children[0].children[1].textContent, '  줄바꿈\n<img src=x onerror=alert(1)>  ');

// No speech or permission failure never fabricates text or loses the draft.
for (const error of [null, 'not-allowed', 'network']) {
  const failure = setup({ initial: '기존 요청' });
  failure.click('voice-button');
  const active = failure.recognition();
  if (error) active.onerror({ error });
  active.onend();
  assert.equal(failure.element('prompt').value, '기존 요청');
  assert.equal(failure.element('send-button').disabled, false);
  assert.equal(failure.element('voice-status').hidden, false);
  assert.equal(failure.element('conversation').children.length, 0);
}

const unsupported = setup({ supported: false });
unsupported.click('voice-button');
assert.equal(unsupported.element('prompt').value, '');
assert.match(unsupported.element('voice-message').textContent, /지원하지/);

const leaving = setup();
leaving.click('voice-button');
const active = leaving.recognition();
leaving.pageEvents.pagehide();
assert.equal(active.aborted, true);

// Existing mock group keeps the same composer and displays its identity.
const grouped = setup({ initial: '전환해도 유지할 초안' });
const composer = grouped.element('prompt');
grouped.click('mode-group');
assert.equal(grouped.element('context-title').textContent, '강릉 주말 여행');
assert.equal(grouped.element('context-count').textContent, '차량 3대');
assert.equal(grouped.element('context-count').hidden, false);
assert.equal(grouped.element('group-create').hidden, true);
assert.equal(grouped.element('prompt'), composer);
assert.equal(composer.value, '전환해도 유지할 초안');
grouped.submit();
assert.equal(composer.value, '');
assert.equal(grouped.element('conversation').children.length, 1);
assert.match(grouped.element('conversation').children[0].children[0].children[0].textContent, /강릉 주말 여행/);
grouped.click('mode-personal');
assert.equal(grouped.element('conversation').children.length, 0);
grouped.click('mode-group');
assert.equal(grouped.element('conversation').children.length, 1);

// A missing group routes to creation and blocks group sends and recording.
const missing = setup({ search: '?group=none', initial: '작성 중인 요청' });
missing.click('mode-group');
assert.equal(missing.element('content').hidden, true);
assert.equal(missing.element('group-create').hidden, false);
assert.equal(missing.element('context-count').hidden, true);
missing.submit();
missing.click('voice-button');
assert.equal(missing.recognition(), undefined);
assert.equal(missing.element('conversation').children.length, 0);
assert.equal(missing.element('prompt').value, '작성 중인 요청');
missing.createGroup('   ');
assert.match(missing.element('group-create-error').textContent, /그룹 이름/);
assert.equal(missing.element('group-create').hidden, false);
missing.click('back-personal');
assert.equal(missing.element('content').hidden, false);
missing.click('mode-group');
missing.createGroup('제주 드라이브');
assert.equal(missing.element('group-create').hidden, true);
assert.equal(missing.element('content').hidden, false);
assert.equal(missing.element('context-title').textContent, '제주 드라이브');
assert.equal(missing.element('context-count').textContent, '차량 3대');
assert.equal(missing.element('prompt').value, '작성 중인 요청');
missing.submit();
assert.match(missing.element('conversation').children[0].children[0].children[0].textContent, /제주 드라이브/);

console.log('PASS: group routing, mock identity, composer preservation, creation, scoped history, automatic voice send, cancellation, errors and reload');
