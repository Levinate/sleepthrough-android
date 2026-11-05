import{Capacitor,registerPlugin}from'@capacitor/core';
import{createClient}from'@supabase/supabase-js';
import{chooseNights,cloudSummary,days,localDay,dayAt,SAMSUNG}from'./sleep.js';
import{readVault,vaultDetail}from'./vault.js';
export const native=Capacitor.isNativePlatform(),Vault=registerPlugin('SecureVault'),Health=registerPlugin('SleepHealth');
const preview=new Map();
// Browser-only preview remains isolated; Android always uses the encrypted native vault.
export const storage={
 async getItem(key){
  if(!native)return preview.get(key)??null;
  return readVault(Vault,key);
 },
 async setItem(key,value){if(native)await Vault.set({key,value});else preview.set(key,value)},
 async removeItem(key){if(native)await Vault.remove({key});else preview.delete(key)}
};
const config=window.SLEEPTHROUGH_CONFIG;
export const client=createClient(config.url,config.publishableKey,{auth:{storage,persistSession:true,autoRefreshToken:true,detectSessionInUrl:false,flowType:'pkce'}});
const empty=()=>({records:{},health:{enabled:false,cloud:false,records:{},summaries:{},selection:{},token:null,lastSync:null},quiz:{answers:[],step:0},source:'combined',reportVersion:1,started:false,migrated:false});
export const store={uid:'local',user:null,state:empty(),status:'On this phone',busy:true,ready:false,healthBusy:false,conflicts:[],recovery:false,error:'',errorCode:'',errorDetail:'',errorPhase:'',generation:0};
let notify=()=>{},write=Promise.resolve(),syncing=false,cloudTimer;
export function subscribe(fn){notify=fn;}
export function emit(){notify(store);}
export function pruneHealth(){
 const h=store.state.health,rawStart=days(28)[0],summaryStart=days(366)[0];let changed=false;
 h.records=Object.fromEntries(Object.entries(h.records).filter(([,r])=>{
  // An unreadable imported timestamp must not block access to the entire diary.
  const keep=!Number.isFinite(Date.parse(r?.end))||dayAt(r.end,r.endOffsetSeconds)>=rawStart;if(!keep)changed=true;return keep;
 }));
 h.summaries=Object.fromEntries(Object.entries(h.summaries).filter(([day])=>{const keep=day>=summaryStart;if(!keep)changed=true;return keep;}));
 for(const r of Object.values(store.state.records))if(r.kind==='health'&&r.data?.day<summaryStart&&!r.deleted){r.deleted=true;r.dirty=true;changed=true;}
 return changed;
}
export function record(id){return store.state.records[id]?.deleted?null:store.state.records[id]?.data||null;}
export function records(kind){return Object.values(store.state.records).filter(r=>r.kind===kind&&!r.deleted).map(r=>r.data);}
function storageError(phase,error){
 const codes=new Set(['vault_key_missing','vault_key_invalid','vault_locked','vault_format','vault_decrypt','vault_verify','vault_read','vault_write','storage_error','invalid_json','invalid_state','UNIMPLEMENTED','UNAVAILABLE']);
 store.errorPhase=phase;store.errorCode=phase+':'+(codes.has(error?.code)?error.code:'unavailable');store.errorDetail=vaultDetail(error);store.ready=false;
 store.error=phase==='write'?'Your latest changes could not be saved. Keep the app open and retry saving.':phase==='decode'?'Your saved data needs recovery. It has not been replaced. Please retry.':error?.code==='vault_locked'?'Unlock your phone, then retry. Do not clear app data.':'Your saved data could not be opened. Please retry. Do not clear app data.';
 store.status=phase==='write'?'Changes not saved':'Local data unavailable';
}
const object=value=>value!==null&&typeof value==='object'&&!Array.isArray(value);
function restore(value){
 let saved;try{saved=JSON.parse(value);}catch{throw Object.assign(Error('Invalid saved JSON'),{code:'invalid_json'});}
 const invalid=()=>{throw Object.assign(Error('Invalid saved state'),{code:'invalid_state'});};
 if(!object(saved))invalid();
 const result={...empty(),...saved,health:{...empty().health,...saved.health},quiz:{...empty().quiz,...saved.quiz}};
 for(const map of [result.records,result.health.records,result.health.summaries,result.health.selection])if(!object(map))invalid();
 if(Object.values(result.records).some(r=>!object(r)||!object(r.data)||typeof r.kind!=='string')||!Array.isArray(result.quiz.answers)||!Number.isInteger(result.quiz.step)||result.quiz.step<0||result.quiz.step>20)invalid();
 if(saved.reportVersion!==1){result.source='combined';result.reportVersion=1;}
 return result;
}
export function persist(){
 if(!store.ready||store.busy||store.error)return Promise.reject(Error('Wait for your saved data to open before saving changes.'));
 const uid=store.uid,ticket=store.generation,snapshot=JSON.stringify(store.state);
 write=write.catch(()=>{}).then(()=>storage.setItem('state:'+uid,snapshot)).catch(error=>{if(ticket===store.generation){storageError('write',error);emit();}throw error;});return write;
}
export async function loadUser(user){
 const ticket=++store.generation,uid=user?.id||'local';store.ready=false;store.busy=true;store.error='';store.errorCode='';store.errorDetail='';store.errorPhase='';store.user=user;store.uid=uid;store.conflicts=[];store.state=empty();store.status=user?'Loading account…':'On this phone';emit();let phase='read',pruned=false;
 try{
  // Finish earlier validated saves before reading the same account again.
  await write.catch(()=>{});if(ticket!==store.generation)return false;
  const value=await storage.getItem('state:'+uid);if(ticket!==store.generation)return false;
  phase='decode';if(value!==null)store.state=restore(value);
  pruned=pruneHealth();store.ready=true;
 }catch(error){if(ticket!==store.generation)return false;storageError(phase,error);}
 if(ticket!==store.generation)return false;store.busy=false;
 if(pruned&&store.ready)try{await persist();}catch{/* persist retains the current snapshot for retry */}
 if(ticket!==store.generation)return false;emit();if(user&&store.ready)await sync();return store.ready;
}
export async function retryStorage(){
 if(store.errorPhase!=='write')return loadUser(store.user);
 // Keep the unsaved in-memory snapshot instead of replacing it with an older disk copy.
 store.error='';store.errorCode='';store.errorDetail='';store.errorPhase='';store.ready=true;
 try{await persist();store.status='Saved on this phone';emit();await sync();return true;}catch{return false;}
}
export async function resumeApp(){
 // A lifecycle event is not a reason to write an empty or unread profile.
 if(!store.ready||store.busy||store.error)return;
 if(pruneHealth())await persist();
 await sync();if(store.ready&&!store.error&&store.state.health.enabled)await healthSync();
}
export async function setRecord(id,kind,data){if(store.busy||store.error)throw Error('Wait for your data to finish loading.');const old=store.state.records[id];store.state.records[id]={kind,data,revision:old?.revision||0,dirty:true,deleted:false};await persist();store.status=store.user?'Saved on phone · waiting to sync':'Saved on this phone';emit();clearTimeout(cloudTimer);cloudTimer=setTimeout(sync,500);}
export async function deleteRecord(id){const r=store.state.records[id];if(!r)return;r.deleted=true;r.dirty=true;await persist();emit();await sync();}
export async function sync(){
 if(!store.user||syncing||!navigator.onLine||store.error||!store.ready||store.busy)return;
 syncing=true;const uid=store.uid,ticket=store.generation;store.status='Syncing…';emit();
 try{
  let remote=[];for(let offset=0;;offset+=500){const{data,error}=await client.from('sleepthrough_mobile_records').select('*').eq('user_id',uid).range(offset,offset+499);if(error)throw error;remote.push(...data);if(data.length<500)break;}
  if(ticket!==store.generation)return;
  if(!store.state.migrated){
   if(!remote.length){const{data,error}=await client.from('sleepthrough_user_state').select('display_name,payload').eq('user_id',uid).maybeSingle();if(error)throw error;
    if(data){const old=data.payload||{};const add=(id,kind,value)=>{if(!store.state.records[id])store.state.records[id]={kind,data:value,revision:0,dirty:true};};add('profile','profile',{name:data.display_name||store.user.user_metadata?.display_name||'Your',adult:true});for(let day=1;day<=28;day++)if(old.complete?.includes(day)||old.notes?.[day]||old.lessonSteps?.[day]||old.lessonWork?.[day])add('lesson:'+day,'lesson',{day,step:old.lessonSteps?.[day]||0,complete:old.complete?.includes(day)||false,reflection:old.notes?.[day]||'',work:old.lessonWork?.[day]||{},contentVersion:2});const history=old.history||[old.entry].filter(Boolean);for(const n of history){const day=n.day||localDay(new Date(n.date));add('night:'+day,'night',{day,awakenings:n.wakes,awakeMinutes:n.awake,sleepMinutes:n.hours==null?null:n.hours*60,rested:n.rested||'',note:'',tags:'',source:'manual'});}}
   }store.state.migrated=true;
  }
  store.conflicts=[];
  for(const r of remote){const local=store.state.records[r.id];if(local?.dirty){if(local.revision!==r.revision)store.conflicts.push({id:r.id,remote:r});}else store.state.records[r.id]={kind:r.kind,data:r.data,revision:r.revision,dirty:false};}
  const remoteIds=new Set(remote.map(r=>r.id));for(const[id,r]of Object.entries(store.state.records))if(r.revision&&!r.dirty&&!remoteIds.has(id))delete store.state.records[id];
  pruneHealth();
  for(const[id,r]of Object.entries(store.state.records)){
   if(!r.dirty||store.conflicts.some(c=>c.id===id))continue;if(r.kind==='health'&&!store.state.health.cloud&&!r.deleted)continue;
   const snapshot=JSON.stringify(r.data),expected=r.revision;
   let result;
   if(r.deleted){if(!remoteIds.has(id)){delete store.state.records[id];continue;}if(expected){result=await client.from('sleepthrough_mobile_records').delete().eq('user_id',uid).eq('id',id).eq('revision',expected).select('id');if(result.error)throw result.error;if(!result.data.length)throw Error('This record changed on another device. Retry to review it.');}if(ticket!==store.generation)return;delete store.state.records[id];continue;}
   const payload={user_id:uid,id,kind:r.kind,data:r.data,revision:expected+1,updated_at:new Date().toISOString()};
   result=expected?await client.from('sleepthrough_mobile_records').update(payload).eq('user_id',uid).eq('id',id).eq('revision',expected).select('id'):await client.from('sleepthrough_mobile_records').insert(payload).select('id');
   if(result.error)throw result.error;if(!result.data?.length)throw Error('This record changed on another device. Retry to review it.');
   if(ticket!==store.generation)return;const current=store.state.records[id];current.revision=expected+1;current.dirty=JSON.stringify(current.data)!==snapshot;
  }
  await persist();store.status=store.conflicts.length?'Changes need your choice':Object.values(store.state.records).some(r=>r.dirty)?'Saved on phone · sync pending':'All changes synced';
 }catch{if(ticket===store.generation)store.status='Saved on phone · cloud unavailable. Retry sync.';}
 finally{syncing=false;emit();}
}
export async function resolveConflict(id,useLocal){const c=store.conflicts.find(c=>c.id===id);if(!c)return;if(useLocal){store.state.records[id].revision=c.remote.revision;}else store.state.records[id]={kind:c.remote.kind,data:c.remote.data,revision:c.remote.revision,dirty:false};store.conflicts=store.conflicts.filter(x=>x.id!==id);await persist();await sync();}
export async function signOut(){await sync();if(Object.values(store.state.records).some(r=>r.dirty)&&!confirm('Some changes are only on this phone. Signing out will discard these local changes. Sign out anyway?'))return false;const old=store.uid;const{error}=await client.auth.signOut({scope:'local'});if(error)throw Error('Could not sign out. Try again.');await storage.removeItem('state:'+old);await loadUser(null);return true;}
export async function startLocal(name){store.state.started=true;await setRecord('profile','profile',{name,adult:true,createdAt:new Date().toISOString()});}
export async function healthSync(){
 if(store.healthBusy||!store.state.health.enabled||!native||!store.ready||store.busy||store.error)return;
 store.healthBusy=true;const ticket=store.generation,h=structuredClone(store.state.health);emit();
 try{
  if(!(await Health.getSleepPermission()).granted){store.state.health.enabled=false;await persist();throw Error('Sleep access is off. Open Health Connect to reconnect.');}
  let raw={...h.records},token=h.token,affected=new Set();
  const upsert=r=>{if(r.origin===SAMSUNG){if(raw[r.id])affected.add(dayAt(raw[r.id].end,raw[r.id].endOffsetSeconds));raw[r.id]=r;}};
  if(token){let more=true;while(more){const page=await Health.readChanges({token});if(page.expired){token=null;break;}for(const id of page.deletions){for(const[day,s]of Object.entries(h.summaries))if(s.sourceIds?.includes(id))affected.add(day);delete raw[id];}for(const r of page.upserts)upsert(r);token=page.nextToken;more=page.hasMore;}}
  if(!token){token=(await Health.getChangesToken()).token;const from=new Date();from.setDate(from.getDate()-29);let next=null;const fresh={};do{const page=await Health.readSleepSessions({from:from.toISOString(),to:new Date().toISOString(),pageToken:next});for(const r of page.records)if(r.origin===SAMSUNG)fresh[r.id]=r;next=page.nextPageToken;}while(next);const windowStart=days(28)[0];for(const[day]of Object.entries(h.summaries))if(day>=windowStart)affected.add(day);raw=fresh;}
  const derived=chooseNights(raw,h.selection);for(const day of affected)if(!derived[day])delete h.summaries[day];Object.assign(h.summaries,derived);
  const keepStart=days(366)[0],rawStart=days(28)[0];h.summaries=Object.fromEntries(Object.entries(h.summaries).filter(([day])=>day>=keepStart));h.records=Object.fromEntries(Object.entries(raw).filter(([,r])=>r.end.slice(0,10)>=rawStart));h.token=token;h.lastSync=new Date().toISOString();
  if(ticket!==store.generation)return;store.state.health=h;await persist();
  if(h.cloud&&store.user){for(const[day,s]of Object.entries(h.summaries))await setRecord('health:'+day,'health',cloudSummary(s));for(const[id,r]of Object.entries(store.state.records))if(r.kind==='health'&&!h.summaries[r.data.day])await deleteRecord(id);}
  return Object.keys(derived).length;
 }finally{store.healthBusy=false;emit();}
}
export async function clearHealth(){store.state.health=empty().health;for(const[id,r]of Object.entries(store.state.records))if(r.kind==='health'){r.deleted=true;r.dirty=true;}await persist();emit();await sync();}
export async function setHealthCloud(enabled){store.state.health.cloud=enabled;await setRecord('consent-health','profile',{granted:enabled,version:1,date:new Date().toISOString()});if(enabled){for(const[day,s]of Object.entries(store.state.health.summaries))await setRecord('health:'+day,'health',cloudSummary(s));}else{for(const[id,r]of Object.entries(store.state.records))if(r.kind==='health'){r.deleted=true;r.dirty=true;}}await persist();await sync();}
window.addEventListener('online',()=>sync());
