import{ALGORITHM}from'./sleep.js';
export function mergeNights(diary,health){
 const manual=new Map(diary.map(r=>[r.day,r])),device=new Map(health.map(r=>[r.day,r]));
 return [...new Set([...manual.keys(),...device.keys()])].sort().map(day=>{
  const m=manual.get(day),h=device.get(day),valid=h?.quality==='complete'&&h.algorithmVersion===ALGORITHM;
  const row={...h,...m,day,source:'combined',device:h||null,diary:m||null,fieldSources:{}};
  for(const field of ['awakenings','awakeMinutes','sleepMinutes']){
   if(Number.isFinite(m?.[field])){row[field]=m[field];row.fieldSources[field]='diary';}
   else if(valid&&Number.isFinite(h?.[field])){row[field]=h[field];row.fieldSources[field]='samsung';}
   else{row[field]=null;row.fieldSources[field]='unknown';}
  }
  return row;
 });
}
export const fieldSource=(row,field)=>({diary:'Your diary',samsung:'Samsung Health',unknown:'No data'}[row.fieldSources?.[field]]|| (row.source==='samsung'?'Samsung Health':'Your diary'));
