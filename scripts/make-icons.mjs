import fs from 'node:fs/promises';
import path from 'node:path';
import zlib from 'node:zlib';
import assert from 'node:assert/strict';

// 用固定像素稿生成小型物品和状态图标；不依赖图片库，改稿后重新运行并保留脚本作为来源。
const root = path.resolve(import.meta.dirname, '../mod/src/main/resources/assets/naiwa/textures');
const colors = {'.':[0,0,0,0], o:[100,64,28,255], y:[244,207,49,255], w:[247,235,191,255],
  g:[77,147,47,255], k:[37,42,22,255], b:[157,220,230,210], c:[223,251,246,230]};
const face = [
  '................','.....oooooo.....','...ooyyyyyyoo...','..oyyyyyyyyyyo..',
  '..oyggyyyyggyo..','..oygkyyyygkyo..','..oyyyyyyyyyyo..','...oyykkkk yyo..'.replace(' ',''),
  '...oyyyyyyyyo...','....oywwwwyo....','..ooyywwwwyyoo..','..oyyywwwwyyyo..',
  '...oyywwwwyyo...','....oyyyyyyo....','....oo....oo....','................'];
// 每一行恰好 16 像素，源稿错误在生成时拒绝，避免输出截断图标。
face[7] = '...oyykkkkyyo...';
const bottle = [
  '......oooo......','......owwo......','......obbo......','.....obccbo.....',
  '....obccccbo....','...obccccccbo...','...obyyyyyybo...','...obygyygybo...',
  '...obykyykybo...','...obyyyyyybo...','...obyykkyybo...','...obyyyyyybo...',
  '...obwwwwwwbo...','....obbbbbbo....','.....oooooo.....','................'];
function crc(bytes) {
  let value = 0xffffffff;
  for (const b of bytes) {value ^= b; for(let i=0;i<8;i++) value=(value>>>1)^((value&1)?0xedb88320:0);}
  return (value^0xffffffff)>>>0;
}
function chunk(type, data) {
  const name=Buffer.from(type), size=Buffer.alloc(4), check=Buffer.alloc(4);
  size.writeUInt32BE(data.length); check.writeUInt32BE(crc(Buffer.concat([name,data])));
  return Buffer.concat([size,name,data,check]);
}
async function write(name, rows) {
  assert.equal(rows.length,16); rows.forEach(row=>assert.equal(row.length,16));
  const data=Buffer.alloc(16*(16*4+1));
  rows.forEach((row,y)=>[...row].forEach((pixel,x)=>Buffer.from(colors[pixel]).copy(data,y*65+1+x*4)));
  const header=Buffer.alloc(13);header.writeUInt32BE(16,0);header.writeUInt32BE(16,4);header[8]=8;header[9]=6;
  const png=Buffer.concat([Buffer.from([137,80,78,71,13,10,26,10]),chunk('IHDR',header),chunk('IDAT',zlib.deflateSync(data)),chunk('IEND',Buffer.alloc(0))]);
  const target=path.join(root,name+'.png');await fs.mkdir(path.dirname(target),{recursive:true});await fs.writeFile(target,png);
}
await write('item/naiwa_totem',face);
await write('item/laugh_gas_bottle',bottle);
await write('mob_effect/laughing',face);
await write('mob_effect/ascending',face);
console.log('4 张 16×16 图标生成完成');

// 圣光使用连续透明纹理，替代多层实体锥面造成的条带；固定公式是可重新生成的素材源。
const beamWidth=128,beamHeight=256,beamPixels=Buffer.alloc(beamHeight*(beamWidth*4+1));
const smooth=x=>{x=Math.max(0,Math.min(1,x));return x*x*(3-2*x);};
for(let y=0;y<beamHeight;y++)for(let x=0;x<beamWidth;x++){
  const across=x/(beamWidth-1)*2-1,along=y/(beamHeight-1);
  const radial=Math.pow(Math.max(0,1-across*across),2.2);
  const ends=smooth(along/.12)*smooth((1-along)/.08);
  const strands=.82+.12*Math.cos(across*34+Math.sin(along*5)*.25)+.06*Math.cos(across*73);
  const alpha=Math.round(255*radial*ends*strands),offset=y*(beamWidth*4+1)+1+x*4;
  beamPixels.set([255,255,255,alpha],offset);
  if(x===0||x===beamWidth-1||y===0||y===beamHeight-1)assert.equal(alpha,0,'圣光四边必须完全透明');
}
const beamHeader=Buffer.alloc(13);beamHeader.writeUInt32BE(beamWidth,0);beamHeader.writeUInt32BE(beamHeight,4);beamHeader[8]=8;beamHeader[9]=6;
const beamPng=Buffer.concat([Buffer.from([137,80,78,71,13,10,26,10]),chunk('IHDR',beamHeader),chunk('IDAT',zlib.deflateSync(beamPixels)),chunk('IEND',Buffer.alloc(0))]);
await fs.mkdir(path.join(root,'effect'),{recursive:true});await fs.writeFile(path.join(root,'effect/ascension_beam.png'),beamPng);
console.log('128×256 连续渐隐圣光纹理生成完成');
