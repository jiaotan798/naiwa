/** 解码实际 OGG 核对静音准备段与源骨骼起拍，防止再次只裁声音、不裁动作。 */
import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import crypto from 'node:crypto';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
const project=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const audio=process.argv[2]||path.join(project,'mod/src/main/resources/assets/naiwa/sounds/laugh_full.ogg');
const model=JSON.parse(fs.readFileSync(path.join(project,'assets/source/nailong_origin_zero.bbmodel'),'utf8'));
const laugh=model.animations.find(a=>a.name==='laugh');
const spine=Object.values(laugh.animators).find(b=>b.name==='spine').keyframes.filter(k=>k.channel==='rotation');
const motionStart=spine.find(k=>Math.abs(Number(k.data_points[0].x))>.1).time;
const decoded=spawnSync('ffmpeg',['-v','error','-i',audio,'-ac','1','-ar','16000','-f','f32le','pipe:1'],{windowsHide:true,maxBuffer:8*1024*1024});
assert(decoded.status===0,'OGG 解码失败：'+decoded.stderr);
let audibleStart=-1,audibleEnd=-1;
// 10 ms RMS 窗口、-32 dB 阈值与参考素材检测口径一致；尾部淡出允许不足一帧的差异。
for(let offset=0;offset<decoded.stdout.length;offset+=640){
 let sum=0,count=0;for(let i=offset;i<Math.min(offset+640,decoded.stdout.length);i+=4){sum+=decoded.stdout.readFloatLE(i)**2;count++;}
 if(Math.sqrt(sum/count)>=10**(-32/20)){if(audibleStart<0)audibleStart=offset/64000;audibleEnd=(offset+count*4)/64000;}
}
const duration=decoded.stdout.length/64000;
// 新模型比原七秒音轨多 0.04 秒表情收尾；只容许两帧采样的无声尾段，不能宽放起拍误差。
assert(Math.abs(duration-7)<.025,'原七秒音轨时长不符');
assert(laugh.length>=duration-.025&&laugh.length-duration<=.05,'模型与音轨收尾间隔超过两帧');
assert(Math.abs(audibleStart-motionStart)<=.04,`声音起拍 ${audibleStart}s 与身体起拍 ${motionStart}s 不符`);
assert(audibleEnd<=7.01&&audibleEnd>6.8,'声音未在退出前收尾');
const report={audio,sha256:crypto.createHash('sha256').update(fs.readFileSync(audio)).digest('hex'),duration,audibleStart,audibleEnd,motionStart,fullSequenceAudibleStart:.6+audibleStart,checks:'PASS'};
console.log(JSON.stringify(report,null,2));
