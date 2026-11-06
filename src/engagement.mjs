export function calendarDay(date=new Date()) {
 return `${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,'0')}-${String(date.getDate()).padStart(2,'0')}`;
}
function validDay(day) {
 if(typeof day!=='string'||!/^\d{4}-\d{2}-\d{2}$/.test(day))return false;
 const d=new Date(day+'T12:00:00');return Number.isFinite(d.getTime())&&calendarDay(d)===day;
}
export const normalizeVisits = value => [...new Set((Array.isArray(value)?value:[]).filter(validDay))].sort().slice(-400);
function shifted(date,days) {const d=new Date(date);d.setHours(12,0,0,0);d.setDate(d.getDate()+days);return d;}
export function recordVisit(visits,date=new Date()) {
 return normalizeVisits([...normalizeVisits(visits),calendarDay(date)]);
}
export function attendance(visits,date=new Date()) {
 const days=new Set(normalizeVisits(visits)),today=calendarDay(date);
 let cursor=days.has(today)?date:shifted(date,-1),streak=0;
 while(days.has(calendarDay(cursor))){streak++;cursor=shifted(cursor,-1);}
 const monday=shifted(date,-((date.getDay()+6)%7));
 return {streak,today,visitedToday:days.has(today),week:Array.from({length:7},(_,i)=>{
  const d=shifted(monday,i),day=calendarDay(d);
  return {day,label:d.toLocaleDateString('en-US',{weekday:'short'}),visited:days.has(day),today:day===today,future:day>today};
 })};
}
