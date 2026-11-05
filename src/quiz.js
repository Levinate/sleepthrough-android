export const questions=[
 ['What would you like to change?',['Wake less often','Return to sleep more easily','Feel less pressure around sleep','Understand my pattern']],
 ['How long has this been happening?',['Less than a month','1–3 months','More than 3 months','Not sure']],
 ['How many nights a week feel difficult?',['0 nights','1–2 nights','3–4 nights','5+ nights','Not sure']],
 ['When do you tend to wake?',['Soon after falling asleep','In the middle of the night','Closer to morning','It varies']],
 ['How many awakenings do you remember?',['None','One','Two','Three or more','Not sure']],
 ['How long do you feel awake?',['Under 15 minutes','15–30 minutes','30–60 minutes','More than an hour','Not sure']],
 ['What first catches your attention when you wake?',['Racing thoughts','Worry about sleep','Light, noise or comfort','Physical discomfort','Nothing — I am just wide awake','Not sure']],
 ['Is your mind busy before bed?',['Rarely','Sometimes','Often','Almost always']],
 ['What do you usually do after waking?',['Check the time','Use my phone or work','Rest quietly','Choose a quiet activity','It varies']],
 ['How much does your wake-up time vary?',['Very little','Up to an hour','More than an hour','My schedule changes','Not sure']],
 ['What does your last hour before bed look like?',['Work or planning','Screens and entertainment','A quiet ritual','It varies']],
 ['Which evening factor would you like to notice?',['Caffeine','Alcohol','Late work','Daytime naps','None / prefer not to say']],
 ['How much does sleep affect the next day?',['Very little','A little','Noticeably','A lot','Prefer not to say']],
 ['What might make your room more comfortable?',['Light','Sound','Temperature','Bedding','Nothing obvious / not sure']],
 ['Do you wear a sleep tracker?',['Apple Watch','Galaxy Watch or Pixel Watch','Fitbit','Oura ring','Garmin','Another tracker','No tracker']],
 ['What would feel easiest to start with?',['A short explanation','A practical exercise','A morning diary','A night-time protocol']],
 ['How stressful have your days felt recently?',['Not especially stressful','A little stressful','Very stressful','Extremely stressful','Prefer not to say']],
 ['Do heat or sweating disturb your nights?',['No','Sometimes','Often','Not sure / prefer not to say']],
 ['When do you usually have your last caffeinated drink?',['Before 2 p.m.','After 2 p.m.','I do not have caffeine','It varies / not sure']],
 ['How often do you have alcohol in the evening?',['Never or rarely','Some evenings','Most evenings','Prefer not to say']],
 ['How often do bathroom trips interrupt your night?',['None','Once','Twice','Three or more','Not sure']]
];
const has=(value,n)=>Array.isArray(value)?value.includes(n):value===n;
export function profileFor(a){const scores=[0,0,0,0],evidence=[[],[],[],[]];function add(i,q,n){scores[i]+=n;evidence[i].push(q);}if(has(a[6],0))add(0,6,2);if(a[7]>=2)add(0,7,2);if(a[10]===0)add(0,10,1);if(has(a[6],1))add(1,6,2);if(a[8]===0)add(1,8,2);if([2,3].includes(a[9]))add(2,9,2);if(a[10]===3)add(2,10,1);if(a[8]===1)add(2,8,1);if(has(a[6],2))add(3,6,2);if(Number.isInteger(a[13])&&a[13]<4)add(3,13,2);const ranking=scores.map((score,i)=>({score,i})).sort((x,y)=>y.score-x.score),top=ranking[0],mixed=top.score>=3&&top.score===ranking[1].score,clear=top.score>=3&&evidence[top.i].length>=2&&!mixed;const names=['A busy mind at night','Pressure around sleep','A changing routine','Your sleep environment'];return{rulesVersion:1,name:clear?names[top.i]:mixed?'A few factors to explore':'Your pattern is still unfolding',day:clear?[8,23,15,18][top.i]:2,evidence:clear?evidence[top.i]:[],secondary:clear&&ranking[1].score>=2?names[ranking[1].i]:null,scores,completedAt:new Date().toISOString()};}
