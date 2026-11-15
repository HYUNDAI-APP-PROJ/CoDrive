(() => {
  'use strict';
  const host = window.CoDrive;
  window.CoDriveViewport = () => {
    const viewport = JSON.parse(host.viewport());
    const meta = document.querySelector('meta[name="viewport"]');
    const content = 'width=' + viewport.width + ', initial-scale=' + viewport.density / window.devicePixelRatio;
    if (meta.content !== content) meta.content = content;
  };
  window.CoDriveViewport();
  try {
    const saved = JSON.parse(host.readState());
    for (const [field, key] of [['conversation', 'codrive.recommendation.conversation.v1'], ['route', 'codrive.recommendation.route.v1']]) {
      if (typeof saved[field] === 'string') sessionStorage.setItem(key, saved[field]);
    }
  } catch { /* A new installation starts with an empty recommendation screen. */ }
  let nextId = 0;
  const requests = new Map();
  class NativeSpeechRecognition {
    start() {
      this.id = ++nextId;
      requests.set(this.id, this);
      host.startSpeech(this.id);
    }
    // Android already ends audio capture before delivering speechend.
    stop() { }
    abort() {
      requests.delete(this.id);
      host.cancelSpeech(this.id);
      this.onend?.();
    }
  }
  window.SpeechRecognition = NativeSpeechRecognition;
  window.CoDriveSpeech = {
    dispatch(id, event, payload) {
      const request = requests.get(id);
      if (!request) return;
      if (event === 'result') {
        const item = [{ transcript: payload.text }];
        item.isFinal = payload.complete;
        request.onresult?.({ results: [item] });
      } else {
        if (event === 'end') requests.delete(id);
        request['on' + event]?.(payload);
      }
    }
  };
})();
