// 현장 시연용 조작 패널. 주소에 ?dev=1 이 있을 때만 화면 오른쪽 아래에 나타난다.
// dev=1 이 없으면 첫 줄에서 바로 끝나며 DOM·저장소·전역 변수·데이터 모듈에 아무것도 하지 않는다.
// mock-scenario.js 를 먼저 불러와야 한다. 앱 배포 화면에서는 이 파일을 포함하지 않는다.
(() => {
  'use strict';
  if (new URLSearchParams(window.location.search).get('dev') !== '1') return;

  const mock = window.CoDriveMock;
  if (!mock) throw new Error('dev-panel.js 보다 mock-scenario.js 를 먼저 불러와야 합니다.');
  const OPEN_KEY = 'codrive.fun.dev-panel.open.v1';
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
    .dev-body button:not(.dev-toggle) { min-height: 48px; padding: 8px 10px; border: 1px solid #d7d1c7; border-radius: 6px; color: #24221e; background: #fffdf8; font-size: 15px; line-height: 1.3; word-break: keep-all; }
    .dev-body button:not(.dev-toggle):hover { background: #e9e4da; }
    .dev-body button[aria-pressed="true"] { color: #fffdf8; background: #24221e; border-color: #24221e; }
    .dev-actions { display: grid; gap: 8px; padding-top: 12px; border-top: 1px solid #d7d1c7; }
    .dev-status { margin: 0; color: #706c64; line-height: 1.4; }
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
        <button type="button" data-dev-action="near-two-hours">연속 주행 1시간 59분으로</button>
        <button type="button" data-dev-action="end-trip">여행 종료 처리</button>
        <button type="button" data-dev-action="reset">시연 초기화</button>
      </div>
      <p class="dev-status" id="dev-status" role="status"></p>
    </div>`;
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
  toggle.addEventListener('click', () => setOpen(!panel.classList.contains('open')));

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
  });
  panel.querySelector('[data-dev-action="reset"]').addEventListener('click', () => {
    try {
      for (const key of Object.keys(window.sessionStorage)) {
        if (key.startsWith('codrive.fun.') && key !== OPEN_KEY || key.startsWith('codrive.history.')) window.sessionStorage.removeItem(key);
      }
    } catch { /* 저장소를 쓸 수 없으면 새로고침만 한다. */ }
    window.location.reload();
  });

  function render() {
    const current = mock.scenario();
    const label = mock.scenarios().find(item => item.name === current)?.label ?? current;
    panel.querySelector('#dev-current').textContent = '현재: ' + label;
    for (const button of scenarioGroup.querySelectorAll('button')) button.setAttribute('aria-pressed', String(button.dataset.devScenario === current));
  }

  document.body.append(panel);
  let open = false;
  try { open = window.sessionStorage.getItem(OPEN_KEY) === '1'; } catch { /* 기본은 접힌 상태 */ }
  setOpen(open);
  render();
})();
