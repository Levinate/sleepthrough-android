import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
test('Old Android WebView gets update instructions without running an incompatible bundle',()=>{
 const app={innerHTML:''},scripts=[];
 const context={navigator:{userAgent:'Android 9; Chrome/69.0.3497.100'},document:{getElementById:()=>app,createElement:()=>({}),body:{appendChild:s=>scripts.push(s)}}};
 vm.runInNewContext(fs.readFileSync('src/boot.js','utf8'),context);
 assert.equal(scripts.length,0);assert.match(app.innerHTML,/Update Android System WebView/);assert.match(app.innerHTML,/do not need to reinstall/);
 context.navigator.userAgent='Android 16; Chrome/132.0';vm.runInNewContext(fs.readFileSync('src/boot.js','utf8'),context);
 assert.equal(scripts[0].src,'main.js');
});
