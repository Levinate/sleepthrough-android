const pause=ms=>new Promise(resolve=>setTimeout(resolve,ms));

/** Respect Android's back-off hint; never retry a permanent/decryption failure. */
export async function readVault(vault,key,wait=pause){
 for(let attempt=0;;attempt++)try{return(await vault.get({key})).value;}
 catch(error){
  const meta=error?.data;
  const retryable=typeof meta?.retryable==='boolean'?meta.retryable:['vault_read','storage_error'].includes(error?.code);
  if(!retryable||attempt>=2)throw error;
  const hint=Number(meta?.retryAfterMs);
  if(meta?.retryable===true&&Number.isFinite(hint)&&hint>10000)throw error;
  const delay=meta?.retryable===true?Math.min(10000,Math.max(5000,Number.isFinite(hint)?hint:5000))* (attempt+1):200*(attempt+1);
  await wait(Math.min(20000,delay));
 }
}

/** Only native technical identifiers and device model; never render exception messages. */
export function vaultDetail(error){
 const meta=error?.data;
 const diagnostic=typeof meta?.diagnostic==='string'&&/^[A-Za-z0-9_/.-]{1,240}$/.test(meta.diagnostic)?meta.diagnostic:'';
 const device=typeof meta?.device==='string'?meta.device.slice(0,120):'';
 return diagnostic?[diagnostic,device].filter(Boolean).join(' · '):'';
}
