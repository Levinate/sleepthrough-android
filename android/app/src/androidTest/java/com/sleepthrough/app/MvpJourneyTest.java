package com.sleepthrough.app;

import static org.junit.Assert.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
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
   String output=InstrumentationRegistry.getArguments().getString("additionalTestOutputDir");java.io.File dir=output==null?new java.io.File(activity.getExternalFilesDir(null),"qa"):new java.io.File(output);dir.mkdirs();try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(dir,name+".png"))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
  }catch(Throwable e){error.set(e);}finally{done.countDown();}});
 }


 private void geometry(ActivityScenario<MainActivity> a,String route) throws Exception {
  assertEquals("Side gutters on "+route,"true",js(a,"parseFloat(getComputedStyle(document.querySelector('#app')).paddingLeft)>=16 && parseFloat(getComputedStyle(document.querySelector('#app')).paddingRight)>=16"));
  assertEquals("No horizontal overflow on "+route,"true",js(a,"document.documentElement.scrollWidth<=innerWidth+1"));
  assertEquals("Icons are bounded on "+route,"true",js(a,"Array.from(document.querySelectorAll('.button .icon')).every(e=>e.getBoundingClientRect().width<=28&&e.getBoundingClientRect().height<=28)"));
  assertEquals("Cards contain their buttons on "+route,"true",js(a,"Array.from(document.querySelectorAll('.card .button')).every(e=>{const b=e.getBoundingClientRect(),c=e.closest('.card').getBoundingClientRect();return b.left>=c.left+10&&b.right<=c.right-10;})"));
  assertEquals("Lesson CTA has a usable height on "+route,"true",js(a,"Array.from(document.querySelectorAll('.studio-footer .button')).every(e=>e.getBoundingClientRect().height>=44&&e.getBoundingClientRect().height<=92)"));
  assertEquals("Lesson CTA stays above navigation on "+route,"true",js(a,"Array.from(document.querySelectorAll('.studio-footer .button')).every(e=>e.getBoundingClientRect().bottom<=document.querySelector('#bottom').getBoundingClientRect().top-4)"));
 }
 @Test public void cabinetRequiresLoginAndTestDataSurvivesRecreation() throws Exception {
  assertTrue("Never seed a personal phone",android.os.Build.HARDWARE.equals("ranchu") && (android.os.Build.MODEL.startsWith("sdk_gphone") || android.os.Build.MODEL.equals("Android SDK built for x86_64")));
  try(ActivityScenario<MainActivity> a=ActivityScenario.launch(MainActivity.class)){
   waitFor(a,"!!window.Capacitor?.Plugins?.SecureVault");
   run(a,"await Capacitor.Plugins.SecureVault.remove({key:'state:demo'})");
   a.recreate();waitFor(a,"!!document.querySelector('[data-action=enter-demo]') && !document.querySelector('[data-action=enter-demo]').disabled");
   snapshot(a,"01-login");
   for(String route:new String[]{"today","night","quiz","plans","checkout","path"}){
    js(a,"location.hash='#"+route+"'");waitFor(a,"!!document.querySelector('#auth-form')");
    assertEquals("Guest navigation hidden","true",js(a,"document.querySelector('#bottom').classList.contains('hidden')"));
   }
   js(a,"document.querySelector('[data-action=enter-demo]').click()");waitFor(a,"document.body.innerText.includes('A clearer night') && location.hash==='#today'");
   assertEquals("Five stable app destinations","5",js(a,"document.querySelectorAll('#bottom a').length"));
   geometry(a,"today");snapshot(a,"02-today");
   js(a,"location.hash='#checkout'");waitFor(a,"document.body.innerText.includes('A clearer night')");
   assertEquals("No checkout","true",js(a,"!document.querySelector('[data-action=mvp-checkout]') && !document.querySelector('[data-mvp-answer]')"));
   js(a,"location.hash='#path'");waitFor(a,"!!document.querySelector('.week-tabs')");snapshot(a,"03-path");
   assertEquals("No totals on course screen","true",js(a,"!document.querySelector('.learning-stats')"));
   js(a,"location.hash='#settings'");waitFor(a,"!!document.querySelector('.learning-stats')");snapshot(a,"04-account");
   assertEquals("Sample points stay separate","true",js(a,"document.querySelector('.learning-stats').innerText.includes('10')"));
   js(a,"location.hash='#today'");waitFor(a,"!!document.querySelector('[data-action=mvp-awake-open]')");
   js(a,"document.querySelector('[data-action=mvp-awake-open]').click()");waitFor(a,"!!document.querySelector('[data-action=mvp-night-next]')");
   geometry(a,"night");snapshot(a,"05-night");
   js(a,"document.querySelector('[data-action=mvp-night-next]').click()");Thread.sleep(300);
   js(a,"document.querySelector('[data-action=mvp-night-next]').click()");waitFor(a,"!!document.querySelector('#mvp-park-form')");
   js(a,"document.querySelector('[name=thought]').value='Review my list after breakfast';document.querySelector('#mvp-park-form').requestSubmit()");waitFor(a,"!!document.querySelector('.breath-circle')");
   js(a,"document.querySelector('[data-action=mvp-night-next]').click()");waitFor(a,"!!document.querySelector('[data-action=mvp-night-sleepy]')");
   js(a,"document.querySelector('[data-action=mvp-night-sleepy]').click()");waitFor(a,"location.hash==='#today'");
   js(a,"document.querySelector('[data-action=checkin]').click()");waitFor(a,"!!document.querySelector('[data-mvp-check]')");
   js(a,"document.querySelector('[data-mvp-check=\"2\"]').click()");Thread.sleep(200);
   js(a,"document.querySelector('[data-mvp-check=\"1\"]').click()");Thread.sleep(200);
   js(a,"document.querySelector('[data-mvp-check=\"4\"]').click()");waitFor(a,"document.querySelector('#modal-body').innerText.includes('Your night is saved')");
   js(a,"document.querySelector('[data-action=mvp-check-close]').click();location.hash='#lesson/12'");waitFor(a,"!!document.querySelector('[data-lesson-step]')");
   js(a,"document.querySelector('[data-lesson-step=\"1\"]').click()");waitFor(a,"!!document.querySelector('[data-work-pick=jaw]')");
   js(a,"document.querySelector('[data-work-pick=jaw][data-value=\"1\"]').click()");Thread.sleep(150);
   js(a,"document.querySelector('[data-work-pick=shoulders][data-value=\"1\"]').click()");Thread.sleep(150);
   js(a,"document.querySelector('[data-work-pick=hands][data-value=\"1\"]').click()");waitFor(a,"document.querySelector('[data-work-continue]')?.disabled===false");
   js(a,"document.querySelector('[data-work-continue]').click()");waitFor(a,"!!document.querySelector('[data-action=complete]')");
   js(a,"document.querySelector('[data-action=complete]').click()");waitFor(a,"location.hash==='#complete'");snapshot(a,"06-practice");
   a.recreate();waitFor(a,"!!document.querySelector('[data-action=enter-demo]') && !document.querySelector('[data-action=enter-demo]').disabled");
   js(a,"document.querySelector('[data-action=enter-demo]').click()");waitFor(a,"document.body.innerText.includes('A clearer night')");
   run(a,"const s=JSON.parse((await Capacitor.Plugins.SecureVault.get({key:'state:demo'})).value);if(!s.records['lesson:12']?.data.complete)throw Error('Completed lesson lost');if(!Object.values(s.records).some(r=>r.kind==='night'&&r.data.wakeEvents?.some(e=>e.parkedThought==='Review my list after breakfast')))throw Error('Night note lost');");
   for(String route:new String[]{"today","path","insights","journal","settings"}){js(a,"location.hash='#"+route+"'");Thread.sleep(200);assertEquals("No horizontal overflow: "+route,"true",js(a,"document.documentElement.scrollWidth<=innerWidth+1"));}

   for(int day:new int[]{1,4,8,12,2,3,10}){
    js(a,"location.hash='#lesson/"+day+"'");waitFor(a,"document.querySelector('.studio-top>span')?.textContent==='DAY "+day+" / 28'");
    js(a,"document.querySelector('[data-lesson-step=\"0\"]').click()");waitFor(a,"!!document.querySelector('.studio-intro')");geometry(a,"lesson intro "+day);snapshot(a,"intro-"+day);
    js(a,"document.querySelector('[data-lesson-step=\"1\"]').click()");waitFor(a,"!!document.querySelector('.activity-workbench')");geometry(a,"lesson exercise "+day);snapshot(a,"exercise-"+day);
   }
   js(a,"location.hash='#insights'");waitFor(a,"!!document.querySelector('[data-source=samsung]')");
   assertEquals("Integration is correctly labelled","true",js(a,"document.querySelector('[data-source=samsung]').innerText==='Health Integration'"));
   js(a,"document.querySelector('[data-source=samsung]').click()");waitFor(a,"!!document.querySelector('.integration-empty')");geometry(a,"health empty state");snapshot(a,"07-health-integration");
   js(a,"location.hash='#settings'");waitFor(a,"!!document.querySelector('[data-action=signout]')");
   js(a,"document.querySelector('[data-action=signout]').click()");waitFor(a,"!!document.querySelector('#auth-form')");
  }
 }
}
