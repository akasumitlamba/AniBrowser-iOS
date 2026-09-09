/* SPDX-License-Identifier: MPL-2.0 */
const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const code = fs.readFileSync(path.join(__dirname, '../main/assets/extensions/anibrowser/background.js'), 'utf8');
async function fixture(rules) {
  let before, respond;
  const event = () => ({addListener(){}});
  const port = {onMessage:{addListener:f=>respond=f},onDisconnect:event(),postMessage:m=>{
    if(m.type==='navigation') queueMicrotask(()=>respond({type:'navigationResult',id:m.id,allowed:rules[new URL(m.destination).hostname]===true}));
  }};
  vm.runInNewContext(code,{URL,Map,Set,Promise,setTimeout,clearTimeout,browser:{
    storage:{local:{get:async()=>({speed:1.5}),set:async()=>{}}},
    tabs:{query:async()=>[],sendMessage:async()=>{},onCreated:event(),onRemoved:event()},
    runtime:{onMessage:event(),connectNative:()=>port},
    webRequest:{onBeforeRequest:{addListener:f=>before=f},onCompleted:event(),onErrorOccurred:event()}
  }});
  await new Promise(setImmediate);
  return {before};
}
test('Block preference still permits direct visits and ordinary cross-site links',async()=>{
  const f=await fixture({});
  const result=await f.before({tabId:1,requestId:'new',url:'https://b.example/',originUrl:'https://a.example/'});
  assert.equal(result.cancel,undefined);
});
test('Block stops a cross-domain HTTP redirect',async()=>{
  const f=await fixture({});
  await f.before({tabId:1,requestId:'chain',url:'https://a.example/'});
  assert.equal((await f.before({tabId:1,requestId:'chain',url:'https://b.example/'})).cancel,true);
});
test('Allow resumes redirects without changing request method or body',async()=>{
  const f=await fixture({'a.example':true});
  await f.before({tabId:1,requestId:'chain',url:'https://a.example/'});
  const result=await f.before({tabId:1,requestId:'chain',url:'https://b.example/',method:'POST'});
  assert.equal(Object.keys(result).length,0);
});
test('each redirect hop follows its own source-domain rule',async()=>{
  const f=await fixture({'a.example':true,'b.example':false});
  await f.before({tabId:1,requestId:'chain',url:'https://a.example/'});
  await f.before({tabId:1,requestId:'chain',url:'https://b.example/'});
  assert.equal((await f.before({tabId:1,requestId:'chain',url:'https://c.example/'})).cancel,true);
});
