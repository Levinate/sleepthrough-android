export const SAMSUNG='com.sec.android.app.shealth';
export const ALGORITHM='sleepthrough-stages-1';
const asleep=new Set([2,4,5,6]),awake=new Set([1,3,7]);
export function dayAt(time,offsetSeconds){const date=new Date(Date.parse(time)+(Number.isFinite(offsetSeconds)?offsetSeconds*1000:-new Date(time).getTimezoneOffset()*60000));return date.toISOString().slice(0,10);}
export function localDay(date=new Date()){return [date.getFullYear(),String(date.getMonth()+1).padStart(2,'0'),String(date.getDate()).padStart(2,'0')].join('-');}
export function days(count,now=new Date()){return Array.from({length:count},(_,i)=>{const d=new Date(now);d.setDate(d.getDate()-count+1+i);return localDay(d);});}
export function summarize(record){
 const start=Date.parse(record.start),end=Date.parse(record.end),day=dayAt(record.end,record.endOffsetSeconds);
 const base={day,start:record.start,end:record.end,periodMinutes:(end-start)/60000,awakenings:null,awakeMinutes:null,sleepMinutes:null,source:'samsung',algorithmVersion:ALGORITHM,quality:'missing',verifiedAt:new Date().toISOString(),sourceIds:record.sourceIds||[record.id]};
 if(!Number.isFinite(start)||!Number.isFinite(end)||end<=start)return{...base,quality:'invalid'};
 const stages=(record.stages||[]).map(s=>({...s,a:Date.parse(s.start),b:Date.parse(s.end)})).sort((a,b)=>a.a-b.a);
 if(!stages.length)return base;
 let cursor=start,complete=true,invalid=false;
 for(const s of stages){if(!Number.isFinite(s.a)||!Number.isFinite(s.b)||s.a<start||s.b>end||s.b<=s.a||s.a<cursor){invalid=true;break;}if(s.a!==cursor||(!asleep.has(s.stage)&&!awake.has(s.stage)))complete=false;cursor=s.b;}
 if(cursor!==end)complete=false;
 if(invalid||!complete)return{...base,quality:invalid?'invalid':'partial'};
 const sleep=stages.filter(s=>asleep.has(s.stage));
 if(!sleep.length)return{...base,quality:'partial'};
 const first=sleep[0].a,last=sleep.at(-1).b;
 let count=0,awakeMs=0,lastWakeEnd=null;
 for(const s of stages){if(awake.has(s.stage)&&s.a>=first&&s.b<=last){if(s.a!==lastWakeEnd)count++;awakeMs+=s.b-s.a;lastWakeEnd=s.b;}else lastWakeEnd=null;}
 return{...base,quality:'complete',awakenings:count,awakeMinutes:awakeMs/60000,sleepMinutes:sleep.reduce((n,s)=>n+s.b-s.a,0)/60000};
}
export function chooseNights(records,selection={}){
 const grouped={};for(const r of Object.values(records)){if(r.origin!==SAMSUNG)continue;const day=dayAt(r.end,r.endOffsetSeconds);(grouped[day]??=[]).push(r);}
 const summaries={};for(const [day,rs] of Object.entries(grouped)){let chosen=rs.filter(r=>(selection[day]||[]).includes(r.id));if(!chosen.length)chosen=[rs.sort((a,b)=>(Date.parse(b.end)-Date.parse(b.start))-(Date.parse(a.end)-Date.parse(a.start)))[0]];chosen.sort((a,b)=>Date.parse(a.start)-Date.parse(b.start));const merged={...chosen[0],end:chosen.at(-1).end,endOffsetSeconds:chosen.at(-1).endOffsetSeconds,stages:chosen.flatMap(r=>r.stages),sourceIds:chosen.map(r=>r.id)};summaries[day]=summarize(merged);}
 return summaries;
}
export function average(rows,field){const vals=rows.map(r=>r?.[field]).filter(v=>Number.isFinite(v));return vals.length?{value:vals.reduce((a,b)=>a+b,0)/vals.length,n:vals.length}:{value:null,n:0};}
export function buckets(rows,period,now=new Date()){const dates=days(period,now),map=new Map(rows.map(r=>[r.day,r]));if(period===7)return dates.map(day=>({label:day.slice(8),...average([map.get(day)],'awakenings')}));return Array.from({length:4},(_,i)=>({label:'W'+(i+1),...average(dates.slice(i*7,i*7+7).map(d=>map.get(d)),'awakenings')}));}
export function cloudSummary(r){const{day,start,end,periodMinutes,awakenings,awakeMinutes,sleepMinutes,source,algorithmVersion,quality,verifiedAt}=r;return{day,start,end,periodMinutes,awakenings,awakeMinutes,sleepMinutes,source,algorithmVersion,quality,verifiedAt};}
