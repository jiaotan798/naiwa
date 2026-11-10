/** 原生 Blockbench 采样网格骨骼，保留源工程的蒙皮、曲线和表情；游戏端插值播放，另叠加整蛊程序姿势。 */
import fs from 'node:fs/promises';
import path from 'node:path';
import net from 'node:net';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {spawn} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {lodPath, simplifyModel} from './simplify-model.mjs';

const project = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const source = path.join(project, 'assets/source/nailong_origin_zero.bbmodel');
const output = path.join(project, 'assets/runtime');
const sourceBytes = await fs.readFile(source);
const sha256 = createHash('sha256').update(sourceBytes).digest('hex');
// 局部修形的法线源绑定 BBModel 摘要，避免编辑网格后静默套用旧法线。
const normalSource = await fs.readFile(path.join(project, 'assets/source/nailong_face_normals.json')).catch(error => {
  if (error.code === 'ENOENT') return null;
  throw error;
});
const normalSha256 = normalSource && createHash('sha256').update(normalSource).digest('hex');
const manifestPath = path.join(output, 'model-manifest.json');
const modelPath = 'assets/naiwa/models/entity/naiwa.nwm';
const lodOnly = process.argv.includes('--lod-only');
if (lodOnly || process.argv.includes('--check')) {
  const manifest = JSON.parse(await fs.readFile(manifestPath, 'utf8'));
  assert.equal(manifest.sourceSha256, sha256, '模型已变化，请先运行 bake-model.mjs 重新采样');
  assert.equal(manifest.normalSha256 ?? null, normalSha256, '局部法线源已变化，请先重新采样');
  assert.ok(manifest.files[modelPath], '采样清单缺少高模');
  if (!lodOnly) assert.ok(manifest.files[lodPath], '采样清单缺少低模，请运行 bake-model.mjs --lod-only');
  for (const [file, digest] of Object.entries(manifest.files)) {
    if (lodOnly && file === lodPath) continue;
    assert.equal(createHash('sha256').update(await fs.readFile(path.join(output, file))).digest('hex'), digest, '采样产物损坏：' + file);
  }
  if (lodOnly) {
    // 高模已通过原采样清单检查，直接离线派生低模，无需重复启动 Blockbench。
    const {bytes, ...stats} = await simplifyModel(await fs.readFile(path.join(output, modelPath)));
    await fs.writeFile(path.join(output, lodPath), bytes);
    manifest.lod = stats;
    manifest.files[lodPath] = createHash('sha256').update(bytes).digest('hex');
    await fs.writeFile(manifestPath, JSON.stringify(manifest, null, 2) + '\n');
    console.log('低模采样完成：' + stats.vertices + ' 顶点、' + stats.triangles + ' 三角面');
  } else console.log('模型源文件与采样产物 SHA256 一致');
  process.exit(0);
}
const model = JSON.parse(sourceBytes);
const bones = model.elements.filter(e => e.type === 'armature_bone');
const meshes = model.elements.filter(e => e.type === 'mesh');
assert.equal(meshes.length, 1, '当前接入资产必须为一张带权重网格');
const mesh = meshes[0], vertexKeys = Object.keys(mesh.vertices);
assert.deepEqual(mesh.origin, [0,0,0], '接入网格原点必须归零');
assert.deepEqual(mesh.rotation, [0,0,0], '接入网格不能保留对象旋转');
const vertexIndex = new Map(vertexKeys.map((key, i) => [key, i]));
const normalOverrides = normalSource ? JSON.parse(normalSource) : null;
if (normalOverrides) assert.equal(normalOverrides.sourceSha256, sha256, '局部法线与源网格不匹配，请重新核对修形区域');
const work = path.join(project, '.bake-work');
await fs.mkdir(work, {recursive: true});
// 独立用户目录避免改动其它工程和插件配置。
const port = await new Promise((resolve, reject) => {
  const server = net.createServer(); server.once('error', reject);
  server.listen(0, '127.0.0.1', () => {const p = server.address().port; server.close(e => e ? reject(e) : resolve(p));});
});
const env = {...process.env}; delete env.ELECTRON_RUN_AS_NODE;
const log = await fs.open(path.join(work, 'blockbench.log'), 'w');
// 普通构建只校验采样产物，重新采样才需要调用者指定编辑器。
const blockbench = process.env.BLOCKBENCH;
assert.ok(blockbench, '重新采样须用 BLOCKBENCH 指定 Blockbench 5.2.1；普通构建使用 --check');
const child = spawn(blockbench,
  ['--userData', path.join(work, 'userdata'), '--remote-debugging-port=' + port, '--remote-debugging-address=127.0.0.1'],
  {cwd: project, env, windowsHide: true, stdio: ['ignore', log.fd, log.fd]});
let socket, requestId = 0, spawnError;
child.on('error', error => {spawnError = error;});
const pending = new Map(), pause = ms => new Promise(resolve => setTimeout(resolve, ms));
async function evaluate(expression) {
  const id = ++requestId;
  const response = await new Promise((resolve, reject) => {
    const timer = setTimeout(() => {pending.delete(id); reject(new Error('Blockbench 采样超时'));}, 120000);
    pending.set(id, {resolve: value => {clearTimeout(timer); resolve(value);}, reject: error => {clearTimeout(timer); reject(error);}});
    socket.send(JSON.stringify({id, method: 'Runtime.evaluate', params: {expression, awaitPromise: true, returnByValue: true}}));
  });
  if (response.exceptionDetails) throw new Error(JSON.stringify(response.exceptionDetails));
  return response.result?.value;
}
try {
  let target;
  for (let i = 0; i < 120; i++) {
    if (spawnError) throw spawnError;
    try {target = (await (await fetch('http://127.0.0.1:' + port + '/json/list')).json()).find(t => t.type === 'page');} catch {}
    if (target) break;
    await pause(500);
  }
  assert.ok(target, '独立 Blockbench 未启动');
  socket = new WebSocket(target.webSocketDebuggerUrl);
  await new Promise((resolve, reject) => {socket.addEventListener('open', resolve, {once: true}); socket.addEventListener('error', reject, {once: true});});
  socket.addEventListener('message', event => {
    const message = JSON.parse(event.data), callback = pending.get(message.id);
    if (callback) {pending.delete(message.id); message.error ? callback.reject(new Error(JSON.stringify(message.error))) : callback.resolve(message.result);}
  });
  let ready = false;
  for (let i = 0; i < 120; i++) {
    ready = await evaluate('typeof Blockbench !== "undefined" && !!Blockbench.setup_successful');
    if (ready) break;
    await pause(500);
  }
  assert.ok(ready, 'Blockbench 初始化超时');
  const info = await evaluate(`(async () => {
    Codecs.project.load(${JSON.stringify(model)}, {path: ${JSON.stringify(source)}});
    for (let i = 0; i < 100; i++) {
      if (Texture.all.length === ${model.textures.length} && Texture.all.every(t => t.img.complete && t.img.naturalWidth)) break;
      await new Promise(r => setTimeout(r, 100));
    }
    Canvas.updateAll(); Canvas.scene.updateMatrixWorld(true); Modes.options.animate.select();
    return {bones: ArmatureBone.all.length, clips: Animation.all.map(a => a.name)};
  })()`);
  assert.equal(info.bones, bones.length);
  assert.deepEqual(info.clips, model.animations.map(a => a.name));
  const textures = await evaluate(`Texture.all.map(t => {
    const canvas = document.createElement('canvas'); canvas.width = t.img.naturalWidth; canvas.height = t.img.naturalHeight;
    canvas.getContext('2d').drawImage(t.img, 0, 0); return canvas.toDataURL('image/png');
  })`);
  const files = {};
  async function write(relative, bytes) {
    const file = path.join(output, relative); await fs.mkdir(path.dirname(file), {recursive: true});
    await fs.writeFile(file, bytes); files[relative] = createHash('sha256').update(bytes).digest('hex');
  }
  for (let i = 0; i < textures.length; i++) await write('assets/naiwa/textures/entity/naiwa_' + i + '.png', Buffer.from(textures[i].split(',')[1], 'base64'));
  const fps = 50, clips = [], ids = bones.map(b => b.uuid);
  // 由原生编辑器处理 Bezier、Catmull-Rom 和根轴点补偿，避免在游戏内再实现解释器。
  for (const clip of model.animations) {
    const frameCount = Math.round(clip.length * fps) + 1;
    const matrices = await evaluate(`(async () => {
      const clip = Animation.all.find(a => a.name === ${JSON.stringify(clip.name)});
      Animation.all.forEach(a => {a.playing = false;}); clip.select(); clip.playing = true;
      const bones = ${JSON.stringify(ids)}.map(id => ArmatureBone.all.find(b => b.uuid === id)), result = [];
      for (let frame = 0; frame < ${frameCount}; frame++) {
        // 采样仅需要骨矩阵，跳过每帧网格重建和界面刷新；仍使用原生动画叠加及插值。
        Timeline.time = frame === ${frameCount - 1} ? clip.length : frame * clip.length / (${frameCount} - 1);
        Animator.showDefaultPose(true); Animator.stackAnimations([clip], false); Canvas.scene.updateMatrixWorld(true);
        for (const bone of bones) {
          const m = bone.scene_object.matrixWorld.clone().multiply(bone.scene_object.inverse_bind_matrix).elements;
          result.push(m[0],m[4],m[8],m[12],m[1],m[5],m[9],m[13],m[2],m[6],m[10],m[14]);
        }
        if (frame % 20 === 0) await new Promise(r => setTimeout(r, 0));
      }
      return result;
    })()`);
    assert.equal(matrices.length, frameCount * bones.length * 12); assert.ok(matrices.every(Number.isFinite));
    clips.push({name: clip.name, length: clip.length, loop: clip.loop === 'loop', frameCount, matrices});
    console.log('已采样 ' + clip.name + '：' + frameCount + ' 帧');
  }
  // 使用编辑器实际计算的顶点作为独立对照，测试游戏端矩阵布局、权重及采样时间。
  // 手指和新增脸骨分别采最高权重点，覆盖飞升手势及眨眼、张嘴的变形。
  const checkIndices = [...new Set([0,100,500,1000,2000,vertexKeys.length-1,...bones.filter((b,i)=>b.name.startsWith('finger_relax_')||(i>=38&&i<=51)).map(bone=>{
    const weight=key=>bone.vertex_weights?.[mesh.uuid.slice(0,6)+':'+key]??bone.vertex_weights?.[key]??0;
    return vertexKeys.reduce((best,key,index)=>weight(key)>weight(vertexKeys[best])?index:best,0);
  })])];
  const checks = await evaluate(`(() => {
    const mesh = Mesh.all[0], keys = ${JSON.stringify(checkIndices.map(i => vertexKeys[i]))}, rows = [];
    for (const clip of Animation.all) {
      Animation.all.forEach(a => {a.playing = false;}); clip.select(); clip.playing = true;
      for (const time of [0, Math.floor(clip.length * ${fps} * .37) / ${fps}, clip.length - 1 / ${fps}]) {
        Timeline.setTime(time); Animator.preview(false); Canvas.scene.updateMatrixWorld(true);
        const offsets = mesh.getArmature().calculateVertexDeformation(mesh);
        rows.push({clip: clip.name, time, points: keys.map(key => mesh.vertices[key].map((x, i) => x + (offsets[key]?.[i] || 0)))});
      }
    }
    return rows;
  })()`);
  await write('native-pose-checks.json', Buffer.from(JSON.stringify({indices: checkIndices, rows: checks})));
  const influences = vertexKeys.map(key => {
    const weights = bones.map((bone, index) => [index, bone.vertex_weights?.[mesh.uuid.slice(0, 6) + ':' + key] ?? bone.vertex_weights?.[key] ?? 0]).filter(([, w]) => w > 0);
    const sum = weights.reduce((total, [, w]) => total + w, 0);
    assert.ok(weights.length > 0 && weights.length <= 4 && Math.abs(sum - 1) < 0.0001, '无效蒙皮权重：' + key);
    return weights.map(([i, w]) => [i, w / sum]);
  });
  const positions = vertexKeys.flatMap(key => mesh.vertices[key]);
  const normals = positions.map(() => 0), faces = [];
  for (const face of Object.values(mesh.faces)) {
    assert.equal(face.vertices.length, 3, '当前资产应为已三角化网格');
    const indices = face.vertices.map(key => vertexIndex.get(key)); assert.ok(indices.every(Number.isInteger));
    const points = indices.map(i => positions.slice(i * 3, i * 3 + 3));
    const a = points[1].map((v, i) => v - points[0][i]), b = points[2].map((v, i) => v - points[0][i]);
    const n = [a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]];
    for (const index of indices) for (let axis = 0; axis < 3; axis++) normals[index * 3 + axis] += n[axis];
    const texture = model.textures[face.texture]; assert.ok(texture);
    faces.push({material: face.texture, indices, uv: face.vertices.flatMap(key => {
      assert.ok(face.uv[key]?.every(Number.isFinite));
      return [face.uv[key][0] / texture.uv_width, face.uv[key][1] / texture.uv_height];
    })});
  }
  const referencedVertices=new Set(faces.flatMap(face=>face.indices));
  for (let i = 0; i < normals.length; i += 3) {
    const length = Math.hypot(...normals.slice(i, i + 3));
    if(length===0) {
      // 精修源文件保留了未参与面片的顶点；保留索引及蒙皮，仅给不渲染的顶点补单位法线。
      assert.ok(!referencedVertices.has(i/3),'可见面顶点法线无效：'+vertexKeys[i/3]);normals[i+1]=1;continue;
    }
    for (let j = 0; j < 3; j++) normals[i + j] /= length;
  }
  // 只替换修形区明确提供的单位法线。其余顶点沿用邻接面的面积加权法线。
  for (const [key, vector] of Object.entries(normalOverrides?.normals ?? {})) {
    const index = vertexIndex.get(key);
    assert.ok(referencedVertices.has(index) && Array.isArray(vector) && vector.length === 3 &&
      vector.every(Number.isFinite) && Math.abs(Math.hypot(...vector) - 1) < 1e-6,
      '局部法线对应的顶点或向量无效：' + key);
    for (let axis = 0; axis < 3; axis++) normals[index * 3 + axis] = vector[axis];
  }
  // NAIWA001 使用大端 int32/float32 和行主序 3×4 骨矩阵；字段顺序须与 NaiwaMesh 读取器同步。
  const chunks = [];
  const int = value => {const b = Buffer.allocUnsafe(4); b.writeInt32BE(value); chunks.push(b);};
  const float = value => {assert.ok(Number.isFinite(value)); const b = Buffer.allocUnsafe(4); b.writeFloatBE(value); chunks.push(b);};
  chunks.push(Buffer.from('NAIWA001'));
  int(vertexKeys.length); int(bones.length); int(textures.length); int(faces.length); int(clips.length);
  positions.forEach(float); normals.forEach(float);
  for (const weights of influences) {int(weights.length); for (const [bone, weight] of weights) {int(bone); float(weight);}}
  for (const face of faces) {int(face.material); face.indices.forEach(int); face.uv.forEach(float);}
  for (const clip of clips) {
    const name = Buffer.from(clip.name, 'utf8'); int(name.length); chunks.push(name);
    float(clip.length); int(clip.loop ? 1 : 0); int(clip.frameCount); clip.matrices.forEach(float);
  }
  const highModel = Buffer.concat(chunks);
  const {bytes: lowModel, ...lod} = await simplifyModel(highModel);
  await write(modelPath, highModel);
  await write(lodPath, lowModel);
  // 材质减少时只清理本生成器的过期编号贴图，避免旧贴图继续被打进成品。
  const textureDir = 'assets/naiwa/textures/entity';
  for (const name of await fs.readdir(path.join(output, textureDir))) {
    const relative = textureDir + '/' + name;
    if (/^naiwa_\d+\.png$/.test(name) && !(relative in files)) await fs.unlink(path.join(output, relative));
  }
  await fs.writeFile(manifestPath, JSON.stringify({sourceSha256: sha256, normalSha256, fps, bones: bones.length, vertices: vertexKeys.length, triangles: faces.length,
    clips: clips.map(({matrices, ...clip}) => clip), lod, files}, null, 2) + '\n');
  assert.equal(createHash('sha256').update(await fs.readFile(source)).digest('hex'), sha256, '原模型被修改');
  console.log('采样完成：' + vertexKeys.length + ' 顶点、' + faces.length + ' 三角面、' + clips.length + ' 段动画；原文件未修改');
} finally {
  // 便携启动器会再派生 Electron；退出时清理本脚本启动的进程树，不能只结束启动器。
  if (child.pid && child.exitCode === null) await new Promise(resolve => {
    const cleanup = spawn('taskkill.exe', ['/PID', String(child.pid), '/T', '/F'], {windowsHide: true, stdio: 'ignore'});
    cleanup.once('error', resolve); cleanup.once('exit', resolve);
  });
  socket?.close(); child.kill(); await log.close();
}
