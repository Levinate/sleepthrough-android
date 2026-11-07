import fs from 'node:fs';
import {build} from 'esbuild';
fs.mkdirSync('www',{recursive:true});
for(const name of ['index.html','base.css','cabinet-theme.css','boot.js'])fs.copyFileSync('src/'+name,'www/'+name);
fs.copyFileSync('shared/activities.css','www/practice.css');
for(const legacy of ['style.css','mvp.css']){const file='www/'+legacy;if(fs.existsSync(file))fs.unlinkSync(file);}
fs.mkdirSync('www/assets',{recursive:true});
for(const scene of ['room','calendar','clock','thoughts','rhythm','energy'])fs.copyFileSync('shared/assets/quiz-'+scene+'.svg','www/assets/quiz-'+scene+'.svg');
fs.copyFileSync('shared/lessons.js','www/lessons.js');
fs.copyFileSync('shared/config.js','www/config.js');
await build({entryPoints:['src/main.js'],bundle:true,outfile:'www/main.js',minify:true,target:['chrome89'],legalComments:'linked'});
console.log('Android web assets built locally.');
