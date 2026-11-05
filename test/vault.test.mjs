import test from'node:test';import assert from'node:assert/strict';
import{readVault,vaultDetail}from'../src/vault.js';

test('temporary Android Keystore failure honors back-off and retries the same key',async()=>{
 let attempts=0;const delays=[],keys=[];
 const vault={async get({key}){keys.push(key);if(++attempts<3)throw{code:'vault_read',data:{retryable:true,retryAfterMs:7000}};return{value:'saved profile'};}};
 assert.equal(await readVault(vault,'state:local',async ms=>delays.push(ms)),'saved profile');
 assert.deepEqual(delays,[7000,14000]);assert.deepEqual(keys,['state:local','state:local','state:local']);
});
test('permanent native failure is returned immediately with the original diagnostic',async()=>{
 const error={code:'vault_read',data:{retryable:false,diagnostic:'key_get/UnrecoverableKeyException/KeyStoreException/KS7'}};let calls=0;
 await assert.rejects(readVault({async get(){calls++;throw error;}},'state:local',()=>assert.fail('No delay')),e=>e===error);assert.equal(calls,1);
});
test('native retries are bounded and an invalid hint cannot produce an unbounded wait',async()=>{
 const delays=[];let calls=0;
 await assert.rejects(readVault({async get(){calls++;throw{code:'vault_read',data:{retryable:true,retryAfterMs:Infinity}};}},'state:local',async ms=>delays.push(ms)));
 assert.equal(calls,3);assert.deepEqual(delays,[5000,10000]);
});
test('bad ciphertext, missing key and locked key are not retried without a new user action',async()=>{
 for(const code of ['vault_decrypt','vault_key_missing','vault_key_invalid','vault_locked']){
  let calls=0;await assert.rejects(readVault({async get(){calls++;throw{code};}},'state:local',()=>assert.fail('No retry')));assert.equal(calls,1);
 }
});
test('a long native back-off hint is returned for a later manual retry, never shortened',async()=>{
 let calls=0;await assert.rejects(readVault({async get(){calls++;throw{code:'vault_read',data:{retryable:true,retryAfterMs:60000}};}},'state:local',()=>assert.fail('Do not retry early')));assert.equal(calls,1);
});
test('diagnostics show allowlisted identifiers and never exception messages or stack traces',()=>{
 assert.equal(vaultDetail({message:'private text',stack:'private stack',data:{diagnostic:'cipher_init/InvalidKeyException/KS7',device:'Samsung QA · Android 14 / API 34'}}),'cipher_init/InvalidKeyException/KS7 · Samsung QA · Android 14 / API 34');
 assert.equal(vaultDetail({data:{diagnostic:'email@example.test <script>',device:'device'}}),'');
});
