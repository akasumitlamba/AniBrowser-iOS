/* SPDX-License-Identifier: MPL-2.0 */
"use strict";
(() => {
  const allowed = [1, 1.25, 1.5, 1.75, 2];
  let speed = 1;
  const attached = new WeakSet();
  const attempts = new WeakMap();
  const recovering = new WeakSet();

  // Background play: prevent web players from auto-pausing when tab/window is hidden
  try {
    if (typeof document !== "undefined") {
      Object.defineProperty(document, "hidden", { get: () => false, configurable: true });
      Object.defineProperty(document, "visibilityState", { get: () => "visible", configurable: true });
      Object.defineProperty(document, "webkitHidden", { get: () => false, configurable: true });
      Object.defineProperty(document, "webkitVisibilityState", { get: () => "visible", configurable: true });
    }
  } catch (_) {}
  try {
    const stopProp = e => { if (e && e.stopImmediatePropagation) e.stopImmediatePropagation(); };
    if (typeof window !== "undefined" && window.addEventListener) {
      window.addEventListener("visibilitychange", stopProp, true);
      window.addEventListener("webkitvisibilitychange", stopProp, true);
    }
    if (typeof document !== "undefined" && document.addEventListener) {
      document.addEventListener("visibilitychange", stopProp, true);
      document.addEventListener("webkitvisibilitychange", stopProp, true);
    }
  } catch (_) {}
  function apply(video) {
    if (!video.isConnected) return;
    // Bound retries when a player continually fights the selected rate.
    const now = performance.now();
    let state = attempts.get(video);
    if (!state || now - state.start > 1000) state = {start: now, count: 0};
    if (state.count >= 8) return;
    if (video.playbackRate === speed && video.defaultPlaybackRate === speed) return;
    state.count++;
    attempts.set(video, state);
    try {
      if (video.preservesPitch !== true) video.preservesPitch = true;
      if (video.defaultPlaybackRate !== speed) video.defaultPlaybackRate = speed;
      if (video.playbackRate !== speed) video.playbackRate = speed;
    } catch (_) { /* A player may temporarily reject a rate during media replacement. */ }
  }
  function scan(root = document) {
    for (const video of root.querySelectorAll("video")) {
      if (!attached.has(video)) {
        attached.add(video);
        for (const event of ["loadedmetadata", "play", "playing", "emptied"])
          video.addEventListener(event, () => apply(video));
        video.addEventListener("ratechange", () => {
          if (video.playbackRate === speed || recovering.has(video)) return;
          recovering.add(video);
          setTimeout(() => { recovering.delete(video); apply(video); }, 120);
        });
      }
      apply(video);
    }
  }
  function setSpeed(value) {
    if (!allowed.includes(value)) return;
    speed = value;
    for (const video of document.querySelectorAll("video")) attempts.delete(video);
    scan();
  }
  browser.runtime.onMessage.addListener(message => setSpeed(message.speed));
  browser.runtime.sendMessage({type: "speed"}).then(message => setSpeed(message.speed)).catch(() => {});
  new MutationObserver(records => {
    for (const record of records) for (const node of record.addedNodes) {
      if (node.nodeType !== 1) continue;
      if (node.matches("video")) scan(node.parentNode || document);
      else if (node.querySelector("video")) scan(node);
    }
  }).observe(document, {childList: true, subtree: true});
  document.addEventListener("visibilitychange", () => { if (!document.hidden) scan(); });
  // Low-frequency recovery for players resetting their rate or replacing sources.
  setInterval(() => { if (!document.hidden) scan(); }, 2000);
  scan();
})();
