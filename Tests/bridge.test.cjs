/* SPDX-License-Identifier: MPL-2.0 */
const {test} = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');
const bridge = fs.readFileSync(path.join(__dirname, '../Resources/bridge.js'), 'utf8');
const playback = fs.readFileSync(path.join(__dirname, '../Resources/playback.js'), 'utf8');
function fixture() {
  const video = {isConnected: true, paused: false, duration: 100, currentTime: 50,
    playbackRate: 1, defaultPlaybackRate: 1, getClientRects: () => [1], addEventListener() {}};
  const ticks = [], calls = [];
  const context = vm.createContext({
    document: {hidden: false, querySelectorAll: () => [video], addEventListener() {}},
    webkit: {messageHandlers: {aniPlayback: {postMessage: message => calls.push(message)}}},
    setInterval: fn => ticks.push(fn), setTimeout, performance,
    MutationObserver: class {observe() {}}
  });
  vm.runInContext(bridge + '\n' + playback, context);
  return {video, context, calls, ticks, receive: state => context.aniReceive(state)};
}
test('native state restores speed and updates embedded-frame video', async () => {
  const f = fixture();
  assert.deepEqual(f.calls, ['state']);
  f.receive({speed: 1.75, seekID: 0, seek: 0});
  await new Promise(setImmediate);
  assert.equal(f.video.playbackRate, 1.75);
  f.receive({speed: 2, seekID: 0, seek: 0});
  assert.equal(f.video.playbackRate, 2);
});
test('seek executes once, clamps bounds and never replays old seeks in new frames', () => {
  const f = fixture();
  f.receive({speed: 1, seekID: 12, seek: 10});
  assert.equal(f.video.currentTime, 50);
  f.receive({speed: 1, seekID: 13, seek: 10});
  f.receive({speed: 1, seekID: 13, seek: 10});
  assert.equal(f.video.currentTime, 60);
  f.video.currentTime = 98;
  f.receive({speed: 1, seekID: 14, seek: 10});
  assert.equal(f.video.currentTime, 100);
  f.video.currentTime = 2;
  f.receive({speed: 1, seekID: 15, seek: -10});
  assert.equal(f.video.currentTime, 0);
});
test('seek ignores paused, hidden and live videos and unsupported deltas', () => {
  const f = fixture();
  f.receive({speed: 1, seekID: 0, seek: 0});
  f.video.paused = true;
  f.receive({speed: 1, seekID: 1, seek: 10});
  f.video.paused = false;
  f.video.getClientRects = () => [];
  f.receive({speed: 1, seekID: 2, seek: 10});
  f.video.getClientRects = () => [1];
  f.video.duration = Infinity;
  f.receive({speed: 1, seekID: 3, seek: 10});
  f.video.duration = 100;
  f.receive({speed: 99, seekID: 4, seek: 500});
  assert.equal(f.video.currentTime, 50);
  assert.equal(f.video.playbackRate, 1);
});
test('hidden documents stop bridge polling', () => {
  const f = fixture();
  f.context.document.hidden = true;
  f.ticks[0]();
  assert.equal(f.calls.length, 1);
  f.context.document.hidden = false;
  f.ticks[0]();
  assert.equal(f.calls.length, 2);
});
