/* SPDX-License-Identifier: MPL-2.0 */
"use strict";
// Runs in WKContentWorld.defaultClient in EVERY frame, including cross-origin video frames.
// Website JavaScript has no access to this bridge. Native only returns playback state.
const browser = (() => {
  let listener = () => {};
  let lastSpeed;
  let lastSeek;
  const pending = [];
  globalThis.aniReceive = state => {
    if ([1, 1.25, 1.5, 1.75, 2].includes(state.speed) && lastSpeed !== state.speed) {
      lastSpeed = state.speed;
      listener({speed: state.speed});
    }
    for (const resolve of pending.splice(0)) resolve({speed: lastSpeed ?? 1});
    // Initial sync must not replay a seek issued before this frame was created.
    if (lastSeek !== undefined && state.seekID !== lastSeek && Math.abs(state.seek) === 10) {
      for (const video of document.querySelectorAll("video")) {
        if (!video.isConnected || video.paused || video.getClientRects().length === 0) continue;
        try {
          if (Number.isFinite(video.duration)) {
            video.currentTime = Math.max(0, Math.min(video.duration, video.currentTime + state.seek));
          }
        } catch (_) { /* Live and protected players may reject seeking. */ }
      }
    }
    lastSeek = state.seekID;
  };
  function request() {
    if (!document.hidden) globalThis.webkit?.messageHandlers?.aniPlayback?.postMessage("state");
  }
  setInterval(request, 750);
  document.addEventListener("visibilitychange", request);
  return {runtime: {
    onMessage: {addListener: callback => { listener = callback; }},
    sendMessage: () => new Promise(resolve => { pending.push(resolve); request(); })
  }};
})();
