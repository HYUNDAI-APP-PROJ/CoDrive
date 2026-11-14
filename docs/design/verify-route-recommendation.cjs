// Run with: node docs/design/verify-route-recommendation.cjs
// Exercise speech events without recording audio or accessing a speech service.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const html = fs.readFileSync(path.join(__dirname, 'route-recommendation.html'), 'utf8');
const script = html.match(/<script>([\s\S]*?)<\/script>/)[1];
const exampleQueries = [...html.matchAll(/data-example="([^"]+)"/g)].map(match => match[1]);

function setup({ supported = true, initial = '', storage = new Map(), storageFailure = false, search = '', initialTime = 0 } = {}) {
  const elements = new Map();
  const pageEvents = {};
  let latest;
  let nextNode = 0;
  let now = initialTime;
  let nextTimer = 0;
  const timers = new Map();
  function element(id) {
    if (!elements.has(id)) {
      const attributes = new Map();
      if (id === 'auto-route') attributes.set('aria-checked', 'false');
      const classes = new Set();
      elements.set(id, {
        value: id === 'prompt' ? initial : '',
        maxLength: 1000,
        hidden: false,
        open: false,
        showModal() { this.open = true; },
        close() { this.open = false; },
        disabled: id === 'send-button',
        textContent: '',
        offsetWidth: 100,
        events: {},
        style: { setProperty() {}, removeProperty() {} },
        setPointerCapture() {},
        children: [],
        dataset: id.startsWith('mode-') ? { mode: id.slice(5) }
          : id.startsWith('example-') ? { example: exampleQueries[Number(id.slice(8))] } : {},
        scrollHeight: 900,
        content: { cloneNode: () => {
          const prefix = 'reply-' + nextNode++;
          return { querySelector: selector => element(prefix + selector) };
        } },
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
        contains(node) { return this.children.includes(node); },
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
    Date: class extends Date { static now() { return now; } },
    URLSearchParams,
    document: {
      getElementById: element,
      createElement: () => element('node-' + nextNode++),
      querySelectorAll: selector => selector === '[data-mode]' ? [element('mode-personal'), element('mode-group')]
        : selector === '[data-example]' ? exampleQueries.map((query, index) => element('example-' + index)) : []
    },
    setTimeout: (callback, delay) => {
      const id = ++nextTimer;
      timers.set(id, { callback, due: now + delay });
      return id;
    },
    clearTimeout: id => timers.delete(id)
  });
  return {
    element,
    advance(ms) {
      const end = now + ms;
      while (true) {
        const next = [...timers].filter(([, timer]) => timer.due <= end).sort((a, b) => a[1].due - b[1].due)[0];
        if (!next) break;
        const [id, timer] = next;
        now = Math.max(now, timer.due);
        timers.delete(id);
        timer.callback();
      }
      now = end;
    },
    click: id => element(id).events.click({ detail: 1 }),
    pointer: (type, x) => element('auto-route').events[type]({ isPrimary: true, button: 0, pointerId: 1, clientX: x }),
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

const places = ['대청호 산책로', '호숫가 식당', '전망 카페'];
const routeNames = page => page.element('route-timeline').children.map(point => point.children[1].textContent);
const replyAt = (page, turn = 0) => page.element('conversation').children[turn].children[1];
const choosePlace = (page, place, turn = 0) => replyAt(page, turn).querySelector('.place-list').children[place]
  .children[0].querySelector('.place-choice').events.click();

function configuredRoute(from = 'personal') {
  const page = setup({ initial: '기존 경로' });
  page.click('mode-' + from);
  page.submit();
  page.advance(3000);
  choosePlace(page, 0);
  page.advance(2000);
  assert.equal(page.element('route-owner').hidden, false);
  assert.equal(page.element('route-owner').textContent, from === 'personal' ? '개인' : '강릉 주말 여행');
  return page;
}
function crossModeRequest(from, automatic = false) {
  const page = configuredRoute(from);
  page.click('mode-' + (from === 'personal' ? 'group' : 'personal'));
  if (automatic) page.click('auto-route');
  page.input('새 경로');
  page.submit();
  page.advance(3000);
  if (!automatic) {
    assert.equal(page.element('route-confirm').open, false);
    choosePlace(page, 2);
  }
  return page;
}

// Both directions and both entry points keep the existing route until acceptance.
for (const from of ['personal', 'group']) for (const automatic of [false, true]) {
  const page = crossModeRequest(from, automatic);
  assert.equal(page.element('route-confirm').open, true);
  assert.equal(Number(page.element('route-countdown').textContent), 10);
  assert.equal(page.element('route-loading').hidden, true);
  assert.deepEqual(routeNames(page), ['대전 유성구', places[0]]);
  assert.equal(page.element('route-owner').textContent, from === 'personal' ? '개인' : '강릉 주말 여행');
  page.advance(1000);
  assert.equal(Number(page.element('route-countdown').textContent), 9);
  page.click('route-accept');
  assert.equal(page.element('route-confirm').open, false);
  assert.equal(page.element('route-loading').hidden, false);
  page.advance(1999);
  assert.deepEqual(routeNames(page), []);
  page.advance(1);
  assert.deepEqual(routeNames(page), ['대전 유성구', ...(automatic ? places : [places[2]])]);
  assert.equal(page.element('route-owner').textContent, from === 'personal' ? '강릉 주말 여행' : '개인');
  const state = JSON.parse(page.storage.get('codrive.recommendation.route.v1'));
  assert.equal(state.owner, from === 'personal' ? 'group:mock-gangneung' : 'personal');
}

// Rejection and Escape cancel the automatic deadline without changing ownership.
for (const from of ['personal', 'group']) {
  const page = crossModeRequest(from);
  page.advance(2500);
  page.click('route-reject');
  page.advance(20000);
  assert.equal(page.element('route-confirm').open, false);
  assert.deepEqual(routeNames(page), ['대전 유성구', places[0]]);
  assert.equal(page.element('route-owner').textContent, from === 'personal' ? '개인' : '강릉 주말 여행');
  choosePlace(page, 2);
  assert.equal(page.element('route-confirm').open, true);
  assert.equal(Number(page.element('route-countdown').textContent), 10);
  page.element('route-confirm').events.cancel({ preventDefault() {} });
  page.advance(20000);
  assert.equal(page.element('route-confirm').open, false);
  assert.deepEqual(routeNames(page), ['대전 유성구', places[0]]);
}

// Exactly 10 seconds triggers acceptance, followed by the separate 2-second application.
for (const from of ['personal', 'group']) {
  const automatic = from === 'personal';
  const page = crossModeRequest(from, automatic);
  page.advance(9999);
  assert.equal(page.element('route-confirm').open, true);
  assert.equal(Number(page.element('route-countdown').textContent), 1);
  assert.deepEqual(routeNames(page), ['대전 유성구', places[0]]);
  page.advance(1);
  assert.equal(page.element('route-confirm').open, false);
  assert.equal(page.element('route-loading').hidden, false);
  page.advance(2000);
  assert.deepEqual(routeNames(page), ['대전 유성구', ...(automatic ? places : [places[2]])]);
}

// Reload resumes the popup deadline rather than restarting its 10 seconds.
const confirming = crossModeRequest('personal');
confirming.advance(3500);
const restoredConfirmation = setup({ storage: confirming.storage, initialTime: 11500 });
assert.equal(restoredConfirmation.element('route-confirm').open, true);
assert.equal(Number(restoredConfirmation.element('route-countdown').textContent), 7);
restoredConfirmation.advance(6499);
assert.equal(restoredConfirmation.element('route-confirm').open, true);
restoredConfirmation.advance(1);
assert.equal(restoredConfirmation.element('route-loading').hidden, false);
restoredConfirmation.advance(2000);
assert.deepEqual(routeNames(restoredConfirmation), ['대전 유성구', places[2]]);

// Ownership is captured at submission, independent of the tab selected at completion.
for (const from of ['personal', 'group']) {
  const page = configuredRoute(from);
  const to = from === 'personal' ? 'group' : 'personal';
  page.click('mode-' + to);
  page.click('auto-route');
  page.input('다른 모드에서 보낸 요청');
  page.submit();
  page.click('mode-' + from);
  page.advance(3000);
  assert.equal(page.element('route-confirm').open, true);
  page.click('route-reject');
  page.input('같은 모드에서 보낸 요청');
  page.submit();
  page.click('mode-' + to);
  page.advance(3000);
  assert.equal(page.element('route-confirm').open, false);
  page.advance(2000);
  assert.deepEqual(routeNames(page), ['대전 유성구', ...places]);
}

// A group route keeps its origin while reloading during the application stage.
const groupApplying = setup({ initial: '그룹 경로' });
groupApplying.click('mode-group');
groupApplying.submit();
groupApplying.advance(3000);
choosePlace(groupApplying, 0);
const restoredGroupApply = setup({ storage: groupApplying.storage, initialTime: 4000 });
restoredGroupApply.advance(1000);
restoredGroupApply.input('개인 경로');
restoredGroupApply.submit();
restoredGroupApply.advance(3000);
choosePlace(restoredGroupApply, 2);
assert.equal(restoredGroupApply.element('route-confirm').open, true);
restoredGroupApply.click('route-reject');

// A custom group's alias stays visible across tabs and reloads, even outside that group.
const aliasedRoute = setup({ search: '?group=none', initial: '그룹 경로 요청' });
aliasedRoute.click('mode-group');
aliasedRoute.createGroup('제주 드라이브');
aliasedRoute.submit();
aliasedRoute.advance(3000);
choosePlace(aliasedRoute, 2);
aliasedRoute.advance(2000);
assert.equal(aliasedRoute.element('route-owner').textContent, '제주 드라이브');
aliasedRoute.click('mode-personal');
assert.equal(aliasedRoute.element('route-owner').textContent, '제주 드라이브');
const restoredAlias = setup({ storage: aliasedRoute.storage, initialTime: 6000 });
assert.equal(restoredAlias.element('route-owner').textContent, '제주 드라이브');

// Arbitrary prompts always yield the same choices. OFF never changes the route until selection.
for (const initial of ['맛집을 찾아줘', '아무 글귀 123', '<img src=x>']) {
  const manual = setup({ initial, search: '?route=recommended' });
  assert.equal(manual.element('route-empty').hidden, false);
  assert.equal(manual.element('route-owner').hidden, true);
  assert.equal(manual.element('route-status').textContent, '안내 중인 경로가 없어요');
  manual.submit();
  const reply = replyAt(manual);
  assert.equal(reply.querySelector('.place-list').hidden, false);
  assert.equal(reply.querySelector('.route-preview').hidden, true);
  assert.deepEqual(reply.querySelector('.place-list').children.map(item => item.children[0].querySelector('.place-name').textContent), places);
  assert.equal(reply.querySelector('.reply-body').inert, true);
  choosePlace(manual, 1);
  assert.deepEqual(routeNames(manual), []);
  manual.advance(3000);
  assert.equal(reply.querySelector('.reply-body').inert, false);
  assert.deepEqual(routeNames(manual), []);
  choosePlace(manual, 1);
  assert.deepEqual(routeNames(manual), []);
  assert.equal(manual.element('route-loading').hidden, false);
  assert.equal(manual.element('route-owner').hidden, true);
  assert.equal(manual.element('route-empty').hidden, true);
  assert.equal(manual.element('route-active').hidden, true);
  assert.equal(manual.element('route-status').textContent, '네비게이션에 반영중입니다');
  manual.advance(1999);
  assert.deepEqual(routeNames(manual), []);
  manual.advance(1);
  assert.equal(manual.element('route-loading').hidden, true);
  assert.deepEqual(routeNames(manual), ['대전 유성구', '호숫가 식당']);
  assert.equal(manual.element('route-timeline').children.at(-1).children[0].textContent, '목적지');
  assert.deepEqual(routeNames(setup({ storage: manual.storage })), ['대전 유성구', '호숫가 식당']);
}

// ON is captured at send time and completes even when its conversation is hidden.
const automatic = setup({ initial: '어디든 가자' });
automatic.click('auto-route');
automatic.submit();
const automaticReply = replyAt(automatic);
assert.equal(automaticReply.querySelector('.place-list').hidden, true);
assert.equal(automaticReply.querySelector('.route-preview').children.length, 3);
automatic.click('auto-route');
automatic.click('mode-group');
automatic.advance(2999);
assert.deepEqual(routeNames(automatic), []);
automatic.advance(1);
assert.deepEqual(routeNames(automatic), []);
assert.equal(automatic.element('route-loading').hidden, false);
automatic.advance(1999);
assert.equal(automatic.element('route-active').hidden, true);
automatic.advance(1);
assert.equal(automatic.element('route-loading').hidden, true);
assert.deepEqual(routeNames(automatic), ['대전 유성구', ...places]);
assert.equal(automatic.element('route-timeline').children[0].getAttribute('aria-current'), 'location');
assert.equal(automatic.element('route-timeline').children[1].children[0].textContent, '경유지 1');
assert.equal(automatic.element('route-source').textContent, '추천받은 경로');
automatic.click('mode-personal');
automatic.input('직접 고를게');
automatic.submit();
automatic.advance(3000);
choosePlace(automatic, 0, 1);
automatic.click('mode-group');
automatic.click('mode-personal');
automatic.advance(3000);
assert.deepEqual(routeNames(automatic), ['대전 유성구', places[0]]);
// A direct selection supersedes an automatic request that is still loading.
automatic.click('auto-route');
automatic.input('자동 경로 요청');
automatic.submit();
choosePlace(automatic, 1, 1);
automatic.advance(3000);
assert.deepEqual(routeNames(automatic), ['대전 유성구', places[1]]);

// Pending automatic routes and completed selections survive a reload.
const queued = setup({ initial: '자동으로 설정해줘' });
queued.click('auto-route');
queued.submit();
const resumed = setup({ storage: queued.storage, initialTime: 1000 });
resumed.click('mode-group');
resumed.advance(1999);
assert.deepEqual(routeNames(resumed), []);
resumed.advance(1);
assert.equal(resumed.element('route-loading').hidden, false);
resumed.advance(2000);
assert.deepEqual(routeNames(resumed), ['대전 유성구', ...places]);
assert.deepEqual(routeNames(setup({ storage: resumed.storage, initialTime: 6000 })), ['대전 유성구', ...places]);

// Reload during navigation application resumes only the remaining part of its 2 seconds.
const linking = setup({ initial: '직접 선택할게' });
linking.submit();
linking.advance(3000);
choosePlace(linking, 2);
linking.advance(750);
const loadingReload = setup({ storage: linking.storage, initialTime: 3750 });
assert.equal(loadingReload.element('route-loading').hidden, false);
loadingReload.click('mode-group');
loadingReload.advance(1249);
assert.deepEqual(routeNames(loadingReload), []);
loadingReload.advance(1);
assert.equal(loadingReload.element('route-loading').hidden, true);
assert.deepEqual(routeNames(loadingReload), ['대전 유성구', places[2]]);
const corruptRoute = setup({ storage: new Map([['codrive.recommendation.route.v1', '{"stops":[99],"pendingAt":3000}']]) });
corruptRoute.advance(3000);
assert.deepEqual(routeNames(corruptRoute), []);

// Example clicks and speech share the same ON behavior, including without storage.
for (const input of ['example', 'speech']) {
  const page = setup({ storageFailure: true });
  page.click('auto-route');
  if (input === 'example') page.click('example-0');
  else {
    page.click('voice-button');
    result(page.recognition(), '어떤 글귀든 같은 코스', true);
    page.recognition().onend();
  }
  page.advance(3000);
  assert.equal(page.element('route-loading').hidden, false);
  page.advance(2000);
  assert.deepEqual(routeNames(page), ['대전 유성구', ...places]);
}

// Speech end sends exactly once, records the full request, and clears the input.
for (const index of [0, 4]) {
  const example = setup({ initial: '작성 중인 초안' });
  example.click('example-' + index);
  assert.equal(example.element('prompt').value, '');
  assert.equal(example.element('conversation').children.length, 1);
  assert.equal(example.element('conversation').children[0].children[0].children[1].textContent,
    '대전 근교에서 조용히 다녀올 드라이브 코스를 추천해줘');
  assert.equal(example.element('empty-intro').inert, true);
  assert.equal(example.element('conversation').children[0].children[1].querySelector('.result').classList.contains('is-loading'), true);
}
const normal = setup({ initial: '대전 근교' });
assert.equal(normal.element('content').classList.contains('has-conversation'), false);
assert.equal(normal.element('empty-intro').getAttribute('aria-hidden'), 'false');
assert.equal(normal.element('context-label').getAttribute('aria-hidden'), 'true');
assert.equal(normal.element('context-title').textContent, '');
assert.equal(normal.element('auto-copy').hidden, false);
assert.match(normal.element('auto-copy').textContent, /직접 선택/);
normal.click('auto-route');
assert.equal(normal.element('auto-copy').hidden, false);
assert.equal(normal.element('auto-copy').textContent, '최대한 빠르게 경로를 설정해줘요.');
normal.click('auto-route');
assert.equal(normal.element('auto-copy').hidden, false);
assert.match(normal.element('auto-copy').textContent, /직접 선택/);

// Drag commits on release; its synthetic click must not toggle a second time.
const sliding = setup();
sliding.pointer('pointerdown', 0);
sliding.pointer('pointermove', 22);
assert.equal(sliding.element('auto-route').getAttribute('aria-checked'), 'false');
sliding.pointer('pointerup', 22);
sliding.click('auto-route');
assert.equal(sliding.element('auto-route').getAttribute('aria-checked'), 'true');
sliding.pointer('pointerdown', 22);
sliding.pointer('pointermove', 0);
sliding.pointer('pointerup', 0);
sliding.click('auto-route');
assert.equal(sliding.element('auto-route').getAttribute('aria-checked'), 'false');
sliding.pointer('pointerdown', 0);
sliding.pointer('pointermove', 6);
sliding.pointer('pointerup', 6);
sliding.click('auto-route');
assert.equal(sliding.element('auto-route').getAttribute('aria-checked'), 'false');
sliding.pointer('pointerdown', 0);
sliding.pointer('pointermove', 22);
sliding.pointer('pointercancel', 22);
assert.equal(sliding.element('auto-route').getAttribute('aria-checked'), 'false');
assert.equal(sliding.element('auto-route').classList.contains('dragging'), false);
sliding.element('auto-route').events.click({ detail: 0 });
assert.equal(sliding.element('auto-route').getAttribute('aria-checked'), 'true');

normal.click('voice-button');
const first = normal.recognition();
assert.equal(first.lang, 'ko-KR');
assert.equal(first.continuous, false);
assert.equal(normal.element('send-button').disabled, true);
assert.equal(normal.element('voice-button').classList.contains('recording'), true);
assert.equal(normal.element('voice-button').getAttribute('aria-label'), '음성 입력 취소');
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
assert.equal(normal.element('voice-button').getAttribute('aria-label'), '음성으로 입력하기');
assert.equal(normal.element('conversation').children.length, 1);
assert.equal(normal.element('content').classList.contains('has-conversation'), true);
assert.equal(normal.element('empty-intro').getAttribute('aria-hidden'), 'true');
const voiceReply = normal.element('conversation').children[0].children[1];
assert.equal(voiceReply.querySelector('.result').classList.contains('is-loading'), true);
assert.equal(voiceReply.querySelector('.reply-body').getAttribute('aria-hidden'), 'true');
normal.advance(2999);
assert.equal(voiceReply.querySelector('.result').classList.contains('is-loading'), true);
normal.advance(1);
assert.equal(voiceReply.querySelector('.result').classList.contains('is-loading'), false);
assert.equal(voiceReply.querySelector('.reply-body').getAttribute('aria-hidden'), 'false');
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
assert.equal(noStorage.element('content').classList.contains('has-conversation'), false);
noStorage.input('  줄바꿈\n<img src=x onerror=alert(1)>  ');
noStorage.submit();
const typedReply = noStorage.element('conversation').children[0].children[1];
assert.equal(typedReply.querySelector('.result').getAttribute('aria-busy'), 'true');
noStorage.advance(3000);
assert.equal(typedReply.querySelector('.result').getAttribute('aria-busy'), 'false');
assert.equal(noStorage.element('content').classList.contains('has-conversation'), true);
assert.equal(noStorage.element('prompt').value, '');
assert.equal(noStorage.element('conversation').children[0].children[0].children[1].textContent, '  줄바꿈\n<img src=x onerror=alert(1)>  ');

// Reload keeps the remaining delay; a detached reply cannot alter another tab's history.
const pending = setup({ initial: '로딩 중인 요청' });
pending.submit();
const pendingReload = setup({ storage: pending.storage, initialTime: 1000 });
const resumedReply = pendingReload.element('conversation').children[0].children[1];
pendingReload.advance(1999);
assert.equal(resumedReply.querySelector('.result').classList.contains('is-loading'), true);
pendingReload.advance(1);
assert.equal(resumedReply.querySelector('.result').classList.contains('is-loading'), false);
pending.click('mode-group');
pending.advance(3000);
assert.equal(pending.element('conversation').children.length, 0);
pending.click('mode-personal');
assert.equal(pending.element('conversation').children.length, 1);
assert.equal(pending.element('conversation').children[0].children[1].querySelector('.result').classList.contains('is-loading'), false);

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
assert.equal(grouped.element('context-label').getAttribute('aria-hidden'), 'false');
assert.equal(grouped.element('context-title').textContent, '강릉 주말 여행');
assert.equal(grouped.element('context-count').textContent, '차량 3대');
assert.equal(grouped.element('context-count').hidden, false);
assert.equal(grouped.element('context-members').textContent, '나 · 민수 · 지연');
assert.equal(grouped.element('context-members').hidden, false);
assert.equal(grouped.element('group-create').hidden, true);
assert.equal(grouped.element('prompt'), composer);
assert.equal(composer.value, '전환해도 유지할 초안');
grouped.submit();
assert.equal(composer.value, '');
assert.equal(grouped.element('conversation').children.length, 1);
assert.match(grouped.element('conversation').children[0].children[0].children[0].textContent, /강릉 주말 여행/);
grouped.click('mode-personal');
assert.equal(grouped.element('context-label').getAttribute('aria-hidden'), 'true');
// Keep the outgoing identity intact until its fade-out finishes.
assert.equal(grouped.element('context-title').textContent, '강릉 주말 여행');
assert.equal(grouped.element('context-members').textContent, '나 · 민수 · 지연');
assert.equal(grouped.element('conversation').children.length, 0);
assert.equal(grouped.element('empty-intro').getAttribute('aria-hidden'), 'false');
grouped.click('mode-group');
assert.equal(grouped.element('context-label').getAttribute('aria-hidden'), 'false');
assert.equal(grouped.element('conversation').children.length, 1);
assert.equal(grouped.element('empty-intro').getAttribute('aria-hidden'), 'true');

// A missing group routes to creation and blocks group sends and recording.
const missing = setup({ search: '?group=none', initial: '작성 중인 요청' });
missing.click('mode-group');
assert.equal(missing.element('content').hidden, true);
assert.equal(missing.element('group-create').hidden, false);
assert.equal(missing.element('context-count').hidden, true);
assert.equal(missing.element('context-members').hidden, true);
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
assert.equal(missing.element('context-members').textContent, '나 · 민수 · 지연');
assert.equal(missing.element('prompt').value, '작성 중인 요청');
missing.submit();
assert.match(missing.element('conversation').children[0].children[0].children[0].textContent, /제주 드라이브/);

console.log('PASS: cross-mode confirmation, 10-second timeout, accept/reject/Escape, ownership restoration, 2-second application and existing input flows');
