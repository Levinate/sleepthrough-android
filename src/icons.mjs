export function icon(name){
 const paths={home:'<path d="m3 10 9-7 9 7v10a1 1 0 0 1-1 1h-5v-8H9v8H4a1 1 0 0 1-1-1z"/>',book:'<path d="M12 5v16M3 3h5a4 4 0 0 1 4 2 4 4 0 0 1 4-2h5v16h-5a4 4 0 0 0-4 2 4 4 0 0 0-4-2H3z"/>',chart:'<path d="M3 3v18h18M6 15l5-6 4 3 6-8"/>',journal:'<path d="M5 3h14v18H5zM8 7h8M8 11h8M8 15h5"/>',account:'<path d="m12 2 3 7 7 3-7 3-3 7-3-7-7-3 7-3z"/>',check:'<path d="m5 12 4 4L19 6"/>'};
 return `<svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${paths[name]||paths.account}</svg>`;
}
