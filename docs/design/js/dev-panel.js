// 현장 시연용 조작 패널. 주소에 ?dev=1 이 있을 때만 화면 오른쪽 아래에 나타난다.
// dev=1 이 없으면 첫 줄에서 바로 끝나며 DOM·저장소·전역 변수·데이터 모듈에 아무것도 하지 않는다.
// mock-scenario.js 를 먼저 불러와야 한다. 앱 배포 화면에서는 이 파일을 포함하지 않는다.
(() => {
  'use strict';
  if (new URLSearchParams(window.location.search).get('dev') !== '1') return;

  const mock = window.CoDriveMock;
  if (!mock) throw new Error('dev-panel.js 보다 mock-scenario.js 를 먼저 불러와야 합니다.');
  const OPEN_KEY = 'codrive.fun.dev-panel.open.v1';
  // 새로고침하는 버튼(여행 횟수·초기화)을 누른 뒤, 다시 불러온 화면에서 포커스를 펼치기 버튼에 둔다.
  const FOCUS_KEY = 'codrive.fun.dev-panel.focus-toggle.v1';
  const NEAR_TWO_HOURS = 2 * 3600 - 60; // 연속 주행 1시간 59분

  const style = document.createElement('style');
  style.dataset.codriveDev = '';
  style.textContent = `
    .dev-panel { position: fixed; z-index: 100; right: 16px; bottom: 16px; width: min(320px, calc(100vw - 32px)); max-height: calc(100dvh - 32px); display: flex; flex-direction: column; color: #24221e; background: #fffdf8; border: 1px solid #d7d1c7; border-radius: 8px; font-size: 16px; }
    .dev-panel:not(.open) { width: auto; }
    .dev-toggle { min-height: 48px; display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 12px 16px; border: 0; border-radius: 8px; color: #24221e; background: transparent; font-size: 16px; font-weight: 600; text-align: left; }
    .dev-toggle:hover { background: #e9e4da; }
    .dev-toggle svg { width: 20px; height: 20px; transition: transform 220ms ease-out; }
    .dev-panel.open .dev-toggle svg { transform: rotate(180deg); }
    .dev-body { min-height: 0; display: grid; gap: 12px; padding: 0 16px 16px; overflow-y: auto; }
    .dev-current { margin: 0; color: #706c64; line-height: 1.4; }
    .dev-scenarios { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; }
    .dev-trips { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; }
    .dev-label { margin: 0; color: #706c64; font-size: 15px; line-height: 1.4; }
    .dev-body button:not(.dev-toggle) { min-height: 48px; padding: 8px 10px; border: 1px solid #d7d1c7; border-radius: 6px; color: #24221e; background: #fffdf8; font-size: 15px; line-height: 1.3; word-break: keep-all; }
    .dev-body button:not(.dev-toggle):hover { background: #e9e4da; }
    .dev-body button[aria-pressed="true"] { color: #fffdf8; background: #24221e; border-color: #24221e; }
    .dev-actions { display: grid; gap: 8px; padding-top: 12px; border-top: 1px solid #d7d1c7; }
    .dev-status { max-width: 288px; margin: 0; padding: 0 16px 12px; color: #706c64; line-height: 1.4; }
    .dev-status:empty { display: none; }
    @media (prefers-reduced-motion: reduce) { .dev-toggle svg { transition: none; } }
  `;
  document.head.append(style);

  const panel = document.createElement('aside');
  panel.className = 'dev-panel';
  panel.dataset.codriveDev = '';
  panel.setAttribute('aria-label', '시연 조작');
  panel.innerHTML = `
    <button class="dev-toggle" type="button" aria-expanded="false" aria-controls="dev-body">시연 조작<svg aria-hidden="true" viewBox="0 0 24 24"><path d="m6 15 6-6 6 6"/></svg></button>
    <div class="dev-body" id="dev-body" hidden>
      <p class="dev-current" id="dev-current"></p>
      <div class="dev-scenarios" role="group" aria-label="시나리오"></div>
      <div class="dev-actions">
        <p class="dev-label" id="dev-trip-label">같은 그룹 여행 횟수 (누르면 시연 기록을 다시 시작해요)</p>
        <div class="dev-trips" role="group" aria-labelledby="dev-trip-label">
          <button type="button" data-dev-trip="1">1번째</button><button type="button" data-dev-trip="2">2번째</button><button type="button" data-dev-trip="3">3번째</button>
        </div>
        <button type="button" data-dev-action="near-two-hours">연속 주행 1시간 59분으로</button>
        <button type="button" data-dev-action="end-trip">여행 종료 처리</button>
        <button type="button" data-dev-action="reset">시연 초기화</button>
      </div>
    </div>
    <p class="dev-status" id="dev-status" role="status"></p>`;
  const toggle = panel.querySelector('.dev-toggle');
  const body = panel.querySelector('.dev-body');
  const scenarioGroup = panel.querySelector('.dev-scenarios');
  const status = panel.querySelector('#dev-status');

  function setOpen(open) {
    panel.classList.toggle('open', open);
    toggle.setAttribute('aria-expanded', String(open));
    body.hidden = !open;
    try { window.sessionStorage.setItem(OPEN_KEY, open ? '1' : '0'); } catch { /* 저장 없이도 패널은 동작한다. */ }
  }
  toggle.addEventListener('click', () => {
    status.textContent = '';
    setOpen(!panel.classList.contains('open'));
  });
  // 시나리오·여행 종료처럼 화면을 바꾸는 버튼을 누르면 패널을 접어 결과를 가리지 않게 한다.
  // 결과 문구(dev-status)는 접힌 상태에서도 펼치기 버튼 아래에 보인다.
  function collapse() {
    setOpen(false);
    toggle.focus();
  }

  // 새로고침해도 고른 시나리오가 유지되도록 주소의 scenario 값도 함께 바꾼다.
  function keepInUrl(name) {
    const url = new URL(window.location.href);
    url.searchParams.set('scenario', name);
    window.history.replaceState(window.history.state, '', url);
  }

  for (const { name, label } of mock.scenarios()) {
    const button = document.createElement('button');
    button.type = 'button';
    button.dataset.devScenario = name;
    button.textContent = label;
    button.addEventListener('click', () => {
      mock.setScenario(name);
      keepInUrl(name);
      status.textContent = '';
      render();
      collapse();
    });
    scenarioGroup.append(button);
  }

  panel.querySelector('[data-dev-action="near-two-hours"]').addEventListener('click', () => {
    status.textContent = mock.setDrivingSecondsForDemo(NEAR_TWO_HOURS)
      ? '연속 주행을 1시간 59분으로 바꿨어요.' : '차량 정보가 없는 시나리오에서는 바꿀 수 없어요.';
  });
  panel.querySelector('[data-dev-action="end-trip"]').addEventListener('click', () => {
    const tripLog = window.CoDriveTripLog;
    status.textContent = !tripLog ? '이 화면에는 여행 기록이 없어요.'
      : tripLog.endTrip() ? '여행을 종료했어요.' : '이미 종료한 여행이에요.';
    collapse();
  });
  // 시연 기록(시나리오·여행 기록·화면 상태)을 지우고 다시 불러온다. 주소의 값은 유지하고, 패널은 접힌 채로 다시 연다.
  function restart(url = window.location.href) {
    try {
      for (const key of Object.keys(window.sessionStorage)) {
        if (key.startsWith('codrive.fun.') || key.startsWith('codrive.history.')) window.sessionStorage.removeItem(key);
      }
      window.sessionStorage.setItem(FOCUS_KEY, '1');
    } catch { /* 저장소를 쓸 수 없으면 새로고침만 한다. */ }
    setOpen(false);
    window.location.replace(url);
  }
  panel.querySelector('[data-dev-action="reset"]').addEventListener('click', () => restart());
  // 같은 그룹 여행 횟수는 여행 중에 바뀌지 않는 값이므로, 바꾸면 그 횟수로 시연 기록을 처음부터 다시 만든다(?trip=N).
  for (const button of panel.querySelectorAll('[data-dev-trip]')) button.addEventListener('click', () => {
    const url = new URL(window.location.href);
    url.searchParams.set('trip', button.dataset.devTrip);
    restart(url.href);
  });

  function render() {
    const current = mock.scenario();
    const label = mock.scenarios().find(item => item.name === current)?.label ?? current;
    panel.querySelector('#dev-current').textContent = '현재: ' + label;
    for (const button of scenarioGroup.querySelectorAll('button')) button.setAttribute('aria-pressed', String(button.dataset.devScenario === current));
    const trip = window.CoDriveTripLog?.getTripNumberWithGroup();
    if (trip) trip.then(m => {
      for (const button of panel.querySelectorAll('[data-dev-trip]')) button.setAttribute('aria-pressed', String(Number(button.dataset.devTrip) === m.value));
    });
    else panel.querySelector('.dev-trips').closest('.dev-actions').querySelectorAll('.dev-trips, #dev-trip-label').forEach(node => { node.hidden = true; });
  }

  document.body.append(panel);
  let open = false;
  try { open = window.sessionStorage.getItem(OPEN_KEY) === '1'; } catch { /* 기본은 접힌 상태 */ }
  setOpen(open);
  render();
  try {
    if (window.sessionStorage.getItem(FOCUS_KEY) === '1') {
      window.sessionStorage.removeItem(FOCUS_KEY);
      toggle.focus();
    }
  } catch { /* 포커스 이동 없이도 패널은 동작한다. */ }
})();
