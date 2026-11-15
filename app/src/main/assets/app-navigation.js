(() => {
  'use strict';
  const host = window.CoDrive;
  const storedKey = key => key.startsWith('codrive.fun.') || key.startsWith('codrive.history.');
  try {
    for (const [key, value] of Object.entries(JSON.parse(host.readWebState()))) {
      if (storedKey(key) && typeof value === 'string' && sessionStorage.getItem(key) === null)
        sessionStorage.setItem(key, value);
    }
  } catch { /* A new installation has no entertainment checkpoints. */ }
  function save() {
    const state = {};
    for (let i = 0; i < sessionStorage.length; i++) {
      const key = sessionStorage.key(i);
      if (storedKey(key)) state[key] = sessionStorage.getItem(key);
    }
    host.saveWebState(JSON.stringify(state));
  }
  // These pages already persist each decision to sessionStorage. Checkpoint the same writes on Android.
  for (const method of ['setItem', 'removeItem', 'clear']) {
    const original = Storage.prototype[method];
    Storage.prototype[method] = function (...args) {
      const result = original.apply(this, args);
      if (this === sessionStorage && (method === 'clear' || storedKey(String(args[0])))) save();
      return result;
    };
  }
  window.CoDriveSavePage = () => {
    window.dispatchEvent(new Event('codrive-pause'));
    save();
  };
  window.CoDriveViewport = () => {
    const viewport = JSON.parse(host.viewport());
    document.querySelector('meta[name="viewport"]').content =
      'width=' + viewport.width + ', initial-scale=' + viewport.density / window.devicePixelRatio;
  };
  window.CoDriveViewport();
  document.addEventListener('DOMContentLoaded', () => {
    const pages = { 'route-recommendation.html': 'recommendation', 'fun-stop-games.html': 'games',
      'fun-challenges.html': 'challenges', 'trip-card.html': 'history' };
    const current = pages[location.pathname.split('/').pop()];
    const icons = {
      start: '<path d="m3 10 9-7 9 7v10H15v-7H9v7H3Z"/>',
      group: '<circle cx="9" cy="8" r="3"/><path d="M3 21v-3a6 6 0 0 1 12 0v3M16 5a3 3 0 0 1 0 6m2 4a5 5 0 0 1 3 5"/>',
      driving: '<path d="M20 10c0 6-8 12-8 12S4 16 4 10a8 8 0 1 1 16 0Z"/><circle cx="12" cy="10" r="2.5"/>',
      recommendation: '<circle cx="5" cy="5" r="2"/><circle cx="19" cy="19" r="2"/><path d="M7 5h9a4 4 0 0 1 0 8H8a3 3 0 0 0 0 6h9"/>',
      games: '<circle cx="12" cy="12" r="9"/><path d="M12 3v18M3 12h18M6 6l12 12M6 18 18 6"/>',
      challenges: '<path d="m12 3 8 3v6c0 5-8 9-8 9s-8-4-8-9V6Zm-4 9 3 3 5-6"/>',
      voice: '<rect x="9" y="2" width="6" height="13" rx="3"/><path d="M5 10v2a7 7 0 0 0 14 0v-2m-7 9v3m-4 0h8"/>',
      history: '<path d="M3 11a9 9 0 1 1 2.8 7.5M3 4v7h7m2-4v6l4 2"/>'
    };
    const style = document.createElement('style');
    style.textContent = `
      button.nav-item{width:100%;border:0;background:transparent;text-align:left;line-height:1.4}
      button.nav-item.active{color:#24221e;background:#f3f0e9;font-weight:600}
      button.nav-item:disabled{opacity:.55;cursor:default}
      .navigation-toggle{display:none}
      @media(max-width:600px){
        .navigation-toggle{display:block;position:fixed;top:8px;left:16px;z-index:30;min-height:56px;padding:12px 16px;border:1px solid #d7d1c7;border-radius:6px;color:#24221e;background:#f3f0e9}
        .main{padding-top:80px}
        .sidebar.navigation-open{display:block;position:fixed;inset:72px 0 0;z-index:40;width:100%;padding:16px;box-shadow:0 -2px 12px #15151540}
        .sidebar.navigation-open .brand{display:none}
        .sidebar.navigation-open .nav-item{justify-content:flex-start;padding:14px 12px}
        .sidebar.navigation-open .nav-item span{display:inline}
      }`;
    document.head.append(style);
    const nav = document.querySelector('.nav');
    nav.setAttribute('aria-label', '주요 메뉴'); nav.replaceChildren();
    for (const item of JSON.parse(host.menu())) {
      const button = document.createElement('button');
      button.type = 'button'; button.className = 'nav-item'; button.dataset.screen = item.id;
      button.innerHTML = '<svg aria-hidden="true" viewBox="0 0 24 24">' + icons[item.id] + '</svg>';
      const label = document.createElement('span'); label.textContent = item.label; button.append(label);
      button.disabled = !item.enabled;
      button.setAttribute('aria-label', item.label + (item.enabled ? '' : ', 준비 중'));
      if (item.id === current) { button.classList.add('active'); button.setAttribute('aria-current', 'page'); }
      button.addEventListener('click', () => {
        document.querySelector('.sidebar').classList.remove('navigation-open');
        toggle.setAttribute('aria-expanded', 'false');
        if (item.id !== current) { window.CoDriveSavePage(); host.navigate(item.id); }
      });
      nav.append(button);
    }
    const toggle = document.createElement('button');
    toggle.type = 'button'; toggle.className = 'navigation-toggle'; toggle.textContent = '화면 이동';
    toggle.setAttribute('aria-expanded', 'false');
    toggle.addEventListener('click', () => {
      const open = document.querySelector('.sidebar').classList.toggle('navigation-open');
      toggle.setAttribute('aria-expanded', String(open));
    });
    document.body.append(toggle);
    document.addEventListener('keydown', event => {
      if (event.key === 'Escape') {
        document.querySelector('.sidebar').classList.remove('navigation-open');
        toggle.setAttribute('aria-expanded', 'false');
      }
    });
  });
})();
