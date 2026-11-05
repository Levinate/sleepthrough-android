package com.sleepthrough.app;

import static org.junit.Assert.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import com.getcapacitor.JSObject;

/** Full visible journey in the actual APK, using only synthetic data on a disposable emulator. */
@RunWith(AndroidJUnit4.class)
public class MvpJourneyTest {
 private String js(ActivityScenario<MainActivity> activity,String script) throws Exception {
  AtomicReference<String> result=new AtomicReference<>();CountDownLatch done=new CountDownLatch(1);
  activity.onActivity(a->a.getBridge().getWebView().evaluateJavascript(script,v->{result.set(v);done.countDown();}));
  assertTrue(done.await(5,TimeUnit.SECONDS));return result.get();
 }
 private void waitFor(ActivityScenario<MainActivity> a,String expression) throws Exception {
  for(int i=0;i<250;i++){if("true".equals(js(a,expression)))return;Thread.sleep(100);}
  fail("Timed out: "+expression+"; page="+js(a,"document.body.innerText.slice(0,1200)"));
 }
 private void run(ActivityScenario<MainActivity> a,String body) throws Exception {
  js(a,"window.qaDone='pending';(async()=>{"+body+"})().then(()=>window.qaDone='ok').catch(e=>window.qaDone='ERROR: '+e.message)");
  waitFor(a,"window.qaDone && window.qaDone!=='pending'");assertEquals("\"ok\"",js(a,"window.qaDone"));
 }
 private void helpers(ActivityScenario<MainActivity> a) throws Exception {
  waitFor(a,"!!window.Capacitor?.Plugins?.SecureVault && !!document.querySelector('[data-action=mvp-start-quiz]')");
  js(a,"window.qa={wait:async(f)=>{for(let i=0;i<200;i++){if(await f())return;await new Promise(r=>setTimeout(r,80));}throw Error('UI wait timed out');},tap:s=>{const e=document.querySelector(s);if(!e||e.disabled)throw Error('Missing or disabled: '+s);e.click();},state:async()=>JSON.parse((await Capacitor.Plugins.SecureVault.get({key:'state:local'})).value),fill:(s,v)=>{const e=document.querySelector(s);if(!e)throw Error('Missing field: '+s);e.value=v;e.dispatchEvent(new Event('input',{bubbles:true}));}}");
 }
 private void snapshot(ActivityScenario<MainActivity> a,String name) throws Exception {
  CountDownLatch done=new CountDownLatch(1);AtomicReference<Throwable> error=new AtomicReference<>();
  activitySnapshot(a,name,done,error);assertTrue(done.await(10,TimeUnit.SECONDS));if(error.get()!=null)throw new RuntimeException(error.get());
 }
 private void activitySnapshot(ActivityScenario<MainActivity> a,String name,CountDownLatch done,AtomicReference<Throwable> error){
  a.onActivity(activity->{try{
   android.view.View view=activity.getWindow().getDecorView();android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(view.getWidth(),view.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);view.draw(new android.graphics.Canvas(bitmap));
   java.io.File dir=new java.io.File(activity.getExternalFilesDir(null),"qa");dir.mkdirs();try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(dir,name+".png"))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
  }catch(Throwable e){error.set(e);}finally{done.countDown();}});
 }
 @Test public void completeAssessmentNightCheckinAndPracticeSurviveRecreation() throws Exception {
  assertTrue("Never seed a personal phone",android.os.Build.MODEL.startsWith("sdk_gphone"));
  try(ActivityScenario<MainActivity> a=ActivityScenario.launch(MainActivity.class)){
   waitFor(a,"!!window.Capacitor?.Plugins?.SecureVault");
   run(a,"await Capacitor.Plugins.SecureVault.set({key:'state:local',value:JSON.stringify({started:false,records:{},quiz:{answers:[],step:0}})})");
   a.recreate();helpers(a);
   run(a,"qa.tap('[data-action=mvp-start-quiz]');await qa.wait(()=>document.querySelector('[data-mvp-answer]')); const answers=[0,2,3,1,2,2,0,3,1,0,0,1,2,2,1,0,3,2,1,2,1];for(let i=0;i<answers.length;i++){if(i===3){await qa.wait(()=>document.querySelector('[data-action=mvp-fact]'));qa.tap('[data-action=mvp-fact]');}await qa.wait(()=>document.querySelector('.mvp-quiz .row small')?.textContent===`${i+1} / 21`);await qa.wait(()=>!document.querySelector('[data-mvp-answer]')?.disabled);qa.tap(`[data-mvp-answer='${answers[i]}']`);}await qa.wait(()=>document.querySelector('.mvp-result'));if(document.body.innerText.includes('Sleep Score'))throw Error('Obsolete score visible');if(document.querySelectorAll('.factor-row').length!==3)throw Error('Expected three supported factors');");
   snapshot(a,"01-result");
   run(a,"qa.tap('[data-action=mvp-lead]');await qa.wait(()=>document.querySelector('#mvp-lead-form'));qa.fill('[name=name]','QA Sarah');qa.fill('[name=email]','qa@example.test');document.querySelector('[name=adult]').checked=true;document.querySelector('#mvp-lead-form').requestSubmit();await qa.wait(()=>document.querySelector('.mvp-plans'));qa.tap('[data-action=mvp-checkout]');if(!document.querySelector('#consent-error').textContent)throw Error('Consent gate missing');if(location.hash==='#checkout')throw Error('Unchecked checkout');");
   snapshot(a,"02-plans");
   run(a,"document.querySelector('#mvp-terms-check').checked=true;qa.tap('[data-action=mvp-checkout]');await qa.wait(()=>document.querySelector('[data-action=mvp-checkout-success]'));qa.tap('[data-action=mvp-checkout-success]');await qa.wait(()=>document.querySelector('[data-action=mvp-onboarding-next]'));qa.tap('[data-action=mvp-onboarding-next]');await qa.wait(()=>document.querySelector('#breath-circle'));qa.tap('[data-action=mvp-onboarding-next]');await qa.wait(()=>document.querySelector('#mvp-routine-form'));document.querySelector('#mvp-routine-form').requestSubmit();await qa.wait(()=>document.querySelector('[data-action=mvp-reminders]'));qa.tap('[data-action=mvp-onboarding-next]');await qa.wait(()=>document.querySelector('[data-action=mvp-onboarding-finish]'));qa.tap('[data-action=mvp-onboarding-finish]');await qa.wait(()=>document.querySelector('.awake-main'));if(!document.querySelector('[data-action=checkin]'))throw Error('Morning check-in missing');");
   snapshot(a,"03-today");
   run(a,"qa.tap('[data-action=mvp-awake-open]');await qa.wait(()=>document.querySelector('.protocol'));qa.tap('[data-action=mvp-night-next]');await qa.wait(()=>document.querySelector('.protocol-content small'));qa.tap('[data-action=mvp-night-next]');await qa.wait(()=>document.querySelector('#mvp-park-form'));qa.fill('[name=thought]','Bring the notes tomorrow');document.querySelector('#mvp-park-form').requestSubmit();await qa.wait(()=>document.querySelector('#breath-circle'));await qa.wait(async()=>{const s=await qa.state();return Object.values(s.records).some(r=>r.kind==='night'&&r.data.wakeEvents?.length===1)});");
   snapshot(a,"04-night");
   AtomicReference<Float> brightness=new AtomicReference<>();a.onActivity(activity->brightness.set(activity.getWindow().getAttributes().screenBrightness));assertEquals(.08f,brightness.get(),.001f);
   run(a,"location.hash='today';await qa.wait(()=>document.querySelector('.awake-main'));qa.tap('[data-action=mvp-awake-open]');await qa.wait(()=>document.querySelector('#breath-circle'));const s=await qa.state();if(Object.values(s.records).find(r=>r.kind==='night').data.wakeEvents.length!==1)throw Error('Reopen counted twice');qa.tap('[data-action=mvp-night-next]');await qa.wait(()=>document.querySelector('[data-action=mvp-night-sleepy]'));qa.tap('[data-action=mvp-night-sleepy]');await qa.wait(()=>document.querySelector('.awake-main'));const last=(await qa.state()).currentNightDay;location.hash='night-detail/'+last;await qa.wait(()=>document.querySelector('[data-action=checkin][data-day]'));qa.tap('[data-action=checkin][data-day]');await qa.wait(()=>document.querySelector('[data-mvp-check]'));qa.tap('[data-mvp-check=\"1\"]');qa.tap('[data-mvp-check=\"2\"]');qa.tap('[data-mvp-check=\"3\"]');await qa.wait(()=>document.querySelector('[data-action=mvp-check-close]'));if(!document.querySelector('#modal-body').textContent.includes('Bring the notes tomorrow'))throw Error('Parked thought missing');");
   snapshot(a,"05-checkin");
   run(a,"qa.tap('[data-action=mvp-check-close]');location.hash='lesson/12';await qa.wait(()=>document.querySelector('[data-lesson-step=\"1\"]'));qa.tap('[data-lesson-step=\"1\"]');await qa.wait(()=>document.querySelector('[data-work-pick=jaw]'));qa.tap('[data-work-pick=jaw]');await qa.wait(()=>document.querySelector('[data-work-pick=jaw]')?.getAttribute('aria-pressed')==='true');qa.tap('[data-work-pick=shoulders]');await qa.wait(()=>document.querySelector('[data-work-pick=shoulders]')?.getAttribute('aria-pressed')==='true');qa.tap('[data-work-pick=hands]');await qa.wait(()=>document.querySelector('[data-work-continue]')?.disabled===false);qa.tap('[data-work-continue]');await qa.wait(()=>document.querySelector('[data-action=complete]'));qa.tap('[data-action=complete]');await qa.wait(()=>location.hash==='#complete');await qa.wait(async()=>!!(await qa.state()).records['lesson:12']?.data.complete);");
   snapshot(a,"06-practice");
   a.recreate();waitFor(a,"document.querySelector('#app') && !document.body.innerText.includes('Opening your sleep space')");
   run(a,"const s=JSON.parse((await Capacitor.Plugins.SecureVault.get({key:'state:local'})).value);if(s.records['profile'].data.name!=='QA Sarah'||!s.records['lesson:12'].data.complete||!s.records['lesson:12'].data.work.picks.hands)throw Error('Reopen lost profile or practice');if(!Object.values(s.records).some(r=>r.kind==='night'&&r.data.checkinComplete))throw Error('Check-in lost');location.hash='settings';");
   waitFor(a,"!!document.querySelector('[data-action=mvp-cancel-open]')");
   run(a,"document.querySelector('[data-action=mvp-cancel-open]').click();await new Promise(r=>setTimeout(r,100));document.querySelector('[data-action=mvp-cancel-confirm]').click();for(let i=0;i<100;i++){const s=JSON.parse((await Capacitor.Plugins.SecureVault.get({key:'state:local'})).value);if(s.records.commerce.data.subscription.cancelAtEnd)return;await new Promise(r=>setTimeout(r,100));}throw Error('Cancellation not saved');");
  }
 }
}
