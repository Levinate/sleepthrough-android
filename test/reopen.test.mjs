import test from 'node:test';
import assert from 'node:assert/strict';
import {build} from 'esbuild';

let sequence=0;
async function harness(){
 const disk=new Map(),calls=[];let getHook,setHook;
 globalThis.testVault={
  async get({key}){calls.push(['get',key]);if(getHook)return getHook(key);return{value:disk.get(key)??null};},
  async set({key,value}){calls.push(['set',key]);if(setHook)await setHook(key,value);disk.set(key,value);},
  async remove({key}){disk.delete(key);}
 };
 globalThis.window={SLEEPTHROUGH_CONFIG:{url:'https://example.test',publishableKey:'public'},addEventListener(){}};
 Object.defineProperty(globalThis,'navigator',{value:{onLine:false},configurable:true});
 const bundle=await build({entryPoints:['src/store.js'],bundle:true,write:false,format:'esm',plugins:[{name:'native-test',setup(b){
  b.onResolve({filter:/^@capacitor\/core$|^@supabase\/supabase-js$/},a=>({path:a.path,namespace:'sdk'}));
  b.onLoad({filter:/.*/,namespace:'sdk'},a=>({contents:a.path.startsWith('@capacitor')?"export const Capacitor={isNativePlatform:()=>true};export const registerPlugin=name=>name==='SecureVault'?globalThis.testVault:{};":"export const createClient=()=>({auth:{}});"}));
 }}]});
 const s=await import('data:text/javascript;base64,'+Buffer.from(bundle.outputFiles[0].text+'\n// instance '+sequence++).toString('base64'));
 return{...s,disk,calls,getHook:fn=>getHook=fn,setHook:fn=>setHook=fn};
}
const profile=name=>JSON.stringify({records:{profile:{kind:'profile',data:{name,adult:true}}},started:true});

test('native adapter restores profile, diary, lesson and quiz on reopen without writing on open/resume',async()=>{
 const s=await harness();await s.loadUser(null);await s.startLocal('Reopen');
 await s.setRecord('night:2026-11-05','night',{day:'2026-11-05',awakenings:3});
 await s.setRecord('lesson:1','lesson',{day:1,complete:true,reflection:'Saved reflection'});
 s.store.state.quiz={answers:[1,2],step:2};await s.persist();const before=s.disk.get('state:local');s.calls.length=0;
 assert.equal(await s.loadUser(null),true);await s.resumeApp();
 assert.equal(s.record('profile').name,'Reopen');assert.equal(s.record('night:2026-11-05').awakenings,3);
 assert.equal(s.record('lesson:1').reflection,'Saved reflection');assert.deepEqual(s.store.state.quiz.answers,[1,2]);
 assert.equal(s.disk.get('state:local'),before);assert.equal(s.calls.filter(c=>c[0]==='set').length,0);
});
test('resume and callers cannot write while an existing profile is still loading',async()=>{
 const s=await harness();const saved=profile('Keep me');s.disk.set('state:local',saved);let release;
 s.getHook(()=>new Promise(resolve=>release=resolve));const loading=s.loadUser(null);
 while(!release)await new Promise(resolve=>setImmediate(resolve));
 await s.resumeApp();await assert.rejects(s.persist());assert.equal(s.disk.get('state:local'),saved);
 release({value:saved});await loading;assert.equal(s.record('profile').name,'Keep me');
 assert.equal(s.calls.filter(c=>c[0]==='set').length,0);
});
test('failed native read preserves ciphertext and blocks later writes; retry recovers',async()=>{
 const s=await harness(),saved=profile('Protected');s.disk.set('state:local',saved);
 s.getHook(()=>{throw{code:'vault_decrypt'};});assert.equal(await s.loadUser(null),false);
 assert.equal(s.store.errorCode,'read:vault_decrypt');await s.resumeApp();await assert.rejects(s.persist());
 assert.equal(s.disk.get('state:local'),saved);assert.equal(s.calls.filter(c=>c[0]==='set').length,0);
 s.getHook(null);assert.equal(await s.retryStorage(),true);assert.equal(s.record('profile').name,'Protected');
});
test('malformed JSON and invalid state remain unchanged and have distinct recovery codes',async()=>{
 const s=await harness();for(const[value,code]of [['{broken','invalid_json'],['{"records":[]}','invalid_state']]){
  s.disk.set('state:local',value);assert.equal(await s.loadUser(null),false);
  assert.equal(s.store.errorCode,'decode:'+code);await assert.rejects(s.persist());assert.equal(s.disk.get('state:local'),value);
 }
});
test('failed save retains unsaved state and retry saves it instead of reloading old disk copy',async()=>{
 const s=await harness();s.disk.set('state:local',profile('Before'));await s.loadUser(null);
 s.setHook(()=>{throw{code:'vault_write'};});await assert.rejects(s.setRecord('profile','profile',{name:'After',adult:true}));
 assert.equal(s.record('profile').name,'After');assert.equal(s.store.errorCode,'write:vault_write');
 assert.equal(JSON.parse(s.disk.get('state:local')).records.profile.data.name,'Before');
 s.setHook(null);assert.equal(await s.retryStorage(),true);assert.equal(JSON.parse(s.disk.get('state:local')).records.profile.data.name,'After');
});
test('transient native read failure retries without modifying saved data',async()=>{
 const s=await harness(),saved=profile('Retry');let attempts=0;s.disk.set('state:local',saved);
 s.getHook(()=>{if(attempts++===0)throw{code:'vault_read'};return{value:saved};});
 assert.equal(await s.loadUser(null),true);assert.equal(attempts,2);assert.equal(s.record('profile').name,'Retry');
 assert.equal(s.calls.filter(c=>c[0]==='set').length,0);
});
test('overlapping account loads cannot replace newer account state with an older result',async()=>{
 const s=await harness();let release;
 s.getHook(key=>key==='state:A'?new Promise(resolve=>release=resolve):{value:profile('B')});
 const first=s.loadUser({id:'A'});while(!release)await new Promise(resolve=>setImmediate(resolve));
 await s.loadUser({id:'B'});release({value:profile('A')});await first;
 assert.equal(s.store.uid,'B');assert.equal(s.record('profile').name,'B');assert.equal(s.store.ready,true);
});
test('reopening waits for a pending valid save before reading it',async()=>{
 const s=await harness();await s.loadUser(null);let release;s.setHook(()=>new Promise(resolve=>release=resolve));
 const saving=s.setRecord('profile','profile',{name:'Pending',adult:true});while(!release)await new Promise(resolve=>setImmediate(resolve));
 const loading=s.loadUser(null);release();await saving;await loading;assert.equal(s.record('profile').name,'Pending');
});
test('invalid imported timestamp does not prevent reopening; expired stages are pruned after a valid read',async()=>{
 const s=await harness();const saved=JSON.parse(profile('Diary stays available'));
 saved.health={records:{bad:{end:'invalid timestamp'},old:{end:'2020-01-01T08:00:00Z'}},summaries:{'2020-01-01':{}},selection:{}};
 s.disk.set('state:local',JSON.stringify(saved));assert.equal(await s.loadUser(null),true);
 assert.equal(s.record('profile').name,'Diary stays available');assert(s.store.state.health.records.bad);
 assert(!s.store.state.health.records.old);const disk=JSON.parse(s.disk.get('state:local'));
 assert(disk.health.records.bad);assert(!disk.health.records.old);assert.deepEqual(disk.health.summaries,{});
});
