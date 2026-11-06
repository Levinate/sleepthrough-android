import test from 'node:test';
import assert from 'node:assert/strict';
import {routeFor,canEnter} from '../src/access.mjs';
import fs from 'node:fs';
test('Every cabinet entry requires a session or explicit test mode, even with a legacy local profile',()=>{
 const guest={user:null,demo:false,state:{started:true,records:{profile:{data:{adult:true,demo:true}}}}};
 for(const route of ['today','dashboard','path','lessons','lesson/8','night','checkin','journal','insights','health','settings','account','quiz','plans','checkout','result','onboarding'])assert.equal(routeFor('#'+route,guest).route,'login',route);
 assert.equal(canEnter(guest),false);
 assert.equal(routeFor('#privacy',guest).route,'privacy');
 assert.equal(routeFor('#password',guest).route,'forgot');
});
test('Retired acquisition routes never show a quiz, paywall or checkout after login',()=>{
 for(const store of [{user:{id:'A'},demo:false},{user:null,demo:true}]){
  for(const route of ['quiz','result','profile','lead','plans','checkout','onboarding','welcome','home'])assert.equal(routeFor('#'+route,store).route,'today');
  assert.equal(routeFor('#lessons',store).route,'path');assert.equal(routeFor('#account',store).route,'settings');
 }
});
test('Shipping bundle excludes acquisition prompts and subscription controls',()=>{
 const bundle=fs.readFileSync('www/main.js','utf8');
 for(const text of ['Where should we','Complete demo checkout','mvp-start-quiz','mvp-checkout','mvp-cancel-confirm','How long do you feel awake?'])assert(!bundle.includes(text),text);
});
