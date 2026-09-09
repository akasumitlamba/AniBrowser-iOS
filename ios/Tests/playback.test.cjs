/* SPDX-License-Identifier: MPL-2.0 */
const {test} = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');
const code = fs.readFileSync(path.join(__dirname, '../Resources/playback.js'), 'utf8');

async function fixture(saved = 1.5) {
  const videos = [];
  let update, mutation, tick;
  const context = {
    document: {hidden: false, querySelectorAll: () => videos, addEventListener() {}},
    browser: {runtime: {onMessage: {addListener: callback => update = callback},
      sendMessage: async () => ({speed: saved})}},
    MutationObserver: class {constructor(callback) { mutation = callback; } observe() {}},
    queueMicrotask, performance, WeakSet, WeakMap, setTimeout,
    setInterval: callback => { tick = callback; },
  };
  function add() {
    const listeners = {};
    const video = {isConnected: true, playbackRate: 1, defaultPlaybackRate: 1,
      addEventListener: (event, callback) => listeners[event] = callback,
      fire: event => listeners[event]?.()};
    videos.push(video);
    return video;
  }
  const first = add();
  vm.runInNewContext(code, context);
  await new Promise(setImmediate);
  return {first, add, update: value => update({speed: value}), mutation: () => mutation([{addedNodes:[{nodeType:1,matches:()=>true}]}]), tick: () => tick()};
}

test('restores the saved rate and preserves pitch on initial playback', async () => {
  const {first} = await fixture(1.75);
  assert.equal(first.playbackRate, 1.75);
  assert.equal(first.defaultPlaybackRate, 1.75);
  assert.equal(first.preservesPitch, true);
});
test('applies saved speed to a replacement episode player', async () => {
  const f = await fixture(2);
  f.first.isConnected = false;
  const next = f.add();
  f.mutation();
  await new Promise(setImmediate);
  assert.equal(next.playbackRate, 2);
});
test('recovers from a player resetting speed', async () => {
  const {first} = await fixture();
  first.playbackRate = 1;
  first.fire('ratechange');
  await new Promise(resolve => setTimeout(resolve, 160));
  assert.equal(first.playbackRate, 1.5);
});
test('changes existing and future videos and rejects invalid rates', async () => {
  const f = await fixture();
  f.update(1.25);
  assert.equal(f.first.playbackRate, 1.25);
  f.update(99);
  const next = f.add();
  f.tick();
  assert.equal(next.playbackRate, 1.25);
});
test('bounds work when a player repeatedly resets its rate', async () => {
  const {first} = await fixture();
  let writes = 0;
  Object.defineProperty(first, 'playbackRate', {get: () => 1, set: () => { writes++; }});
  for (let i = 0; i < 100; i++) first.fire('ratechange');
  await new Promise(resolve => setTimeout(resolve, 160));
  assert.ok(writes <= 8, `unexpected ${writes} repeated writes`);
});

