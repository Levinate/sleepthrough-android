/* Keep this file ES5: an old WebView must show recovery instructions, not a blank screen. */
(function () {
  var match = navigator.userAgent.match(/(?:Chrome|Chromium)\/(\d+)/);
  if (match && Number(match[1]) < 100) {
    document.getElementById('app').innerHTML = '<section class="auth-screen"><span class="eyebrow">A quick update</span><h1>Let’s open your sleep space.</h1><p>Your Android System WebView is out of date. Update it and Google Chrome, then reopen SleepThrough.</p><p>Your saved data stays on this phone. You do not need to reinstall or clear app data.</p><a class="button" href="https://play.google.com/store/apps/details?id=com.google.android.webview">Update Android System WebView</a><a class="button outline" href="https://play.google.com/store/apps/details?id=com.android.chrome">Update Google Chrome</a></section>';
    return;
  }
  var script = document.createElement('script');
  script.src = 'main.js';
  script.onerror = function () { document.getElementById('app').innerHTML = '<h1>Please reopen SleepThrough.</h1><p>The app could not finish opening. Your saved data has not been cleared.</p>'; };
  document.body.appendChild(script);
}());
