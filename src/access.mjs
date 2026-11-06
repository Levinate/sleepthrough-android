const PUBLIC=new Set(['login','signup','forgot','password','privacy','terms']);
const RETIRED=new Set(['quiz','result','profile','lead','plans','checkout','onboarding','welcome','home']);
export const canEnter = store => !!store.user || store.demo===true;
export function routeFor(hash,store){
 const [input,arg]=String(hash||'today').replace(/^#/,'').split('/');
 let route=({dashboard:'today',lessons:'path',account:'settings'})[input]||input;
 if(RETIRED.has(route))route='today';
 if(!canEnter(store)&&!PUBLIC.has(route))route='login';
 if(route==='password'&&!store.recovery)route='forgot';
 return {route,arg};
}
