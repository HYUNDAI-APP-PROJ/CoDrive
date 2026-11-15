// 엔터 요소 화면(fun-stop-games · fun-challenges · trip-card)이 함께 쓰는 공통 골격 동작.
// - mountRoutePanel(): 왼쪽 "현재 경로" 패널을 navi.js 의 getActiveRoute() 로 그리고 변화를 따라간다.
//   표시 규칙은 route-recommendation.html 의 renderRoute 와 같다(현재 위치 → 남은 경유지 → 목적지, 지난 지점 제외).
// - duration(): 초를 "3시간 12분" 같은 문구로 바꾼다.
// measurement.js · mock-scenario.js · navi.js 다음에 불러온다.
(() => {
  'use strict';
  const M = window.CoDriveMeasurement;
  const navi = window.CoDriveNavi;
  if (!M || !navi) throw new Error('fun-shell.js 보다 measurement.js 와 navi.js 를 먼저 불러와야 합니다.');

  function duration(seconds) {
    const hours = Math.floor(seconds / 3600), minutes = Math.floor(seconds % 3600 / 60);
    if (!hours && !minutes) return '1분 미만';
    return [hours && hours + '시간', minutes && minutes + '분'].filter(Boolean).join(' ');
  }

  /** 현재 경로 패널을 그리고 경로가 바뀔 때마다 다시 그린다. 첫 표시가 끝나면 이행되는 Promise 를 돌려준다. */
  function mountRoutePanel() {
    const $ = id => document.getElementById(id);
    let shownKey = '';
    async function render() {
      const route = await navi.getActiveRoute();
      const value = M.isAvailable(route) && route.value.stops.length ? route.value : null;
      const key = JSON.stringify(value);
      if (key === shownKey) return;
      shownKey = key;
      $('route-empty').hidden = Boolean(value);
      $('route-active').hidden = !value;
      $('route-owner').hidden = !value;
      $('route-owner').textContent = value ? value.ownerLabel : '';
      $('route-source').textContent = value ? value.source : '';
      const timeline = $('route-timeline');
      timeline.replaceChildren();
      if (!value) return;
      let waypoint = 0;
      [{ placeName: value.currentLocation }, ...value.stops].forEach((stop, index) => {
        const point = document.createElement('li');
        point.className = 'route-point';
        if (index === 0) point.setAttribute('aria-current', 'location');
        const label = document.createElement('p');
        label.className = 'route-point-label';
        label.textContent = index === 0 ? '현재 위치' : stop.stopKind === 'DESTINATION' ? '목적지' : '경유지 ' + ++waypoint;
        const title = document.createElement('p');
        title.className = 'route-point-name';
        title.textContent = stop.placeName;
        point.append(label, title);
        timeline.append(point);
      });
    }
    navi.subscribe(render);
    return render();
  }

  window.CoDriveFunShell = Object.freeze({ duration, mountRoutePanel });
})();
