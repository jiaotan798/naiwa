/** 从高模采样生成低模，保留头部面片、现有顶点属性及原生动画。 */
import assert from 'node:assert/strict';

export const lodPath = 'assets/naiwa/models/entity/naiwa_lod.nwm';

/** 复用高模材质和蒙皮。UV 缝两侧先拆成独立角点，简化后仍按源顶点合并几何。 */
export async function simplifyModel(source) {
  const {MeshoptSimplifier} = await import('./vendor/meshoptimizer/simplifier.mjs');
  await MeshoptSimplifier.ready;
  assert.ok(MeshoptSimplifier.supported, '网格简化需要支持 WebAssembly 的 Node.js');
  assert.equal(source.toString('ascii', 0, 8), 'NAIWA001');
  const count = source.readInt32BE(8), materials = source.readInt32BE(16), faces = source.readInt32BE(20);
  assert.ok(count > 0 && materials > 0 && faces > 0, '模型数据无效');
  // 小网格同样按比例派生，避免换回七千面模型后仍使用一万面的简化目标。
  const targetFaces = Math.min(10000, Math.max(1, Math.floor(faces / 3)));
  const header = 28, normals = header + count * 12;
  const weights = [];
  let cursor = normals + count * 12;
  for (let vertex = 0; vertex < count; vertex++) {
    const end = cursor + 4 + source.readInt32BE(cursor) * 8;
    weights.push(source.subarray(cursor, end));
    cursor = end;
  }

  const groups = Array.from({length: materials}, () => []);
  const corners = [], cornerIndex = new Map();
  const headFaces = new Set(), protectedVertices = new Set();
  for (let face = 0; face < faces; face++, cursor += 40) {
    const material = source.readInt32BE(cursor);
    const ids = [];
    for (let corner = 0; corner < 3; corner++) {
      const vertex = source.readInt32BE(cursor + 4 + corner * 4);
      const uv = source.subarray(cursor + 16 + corner * 8, cursor + 24 + corner * 8);
      const key = `${material}:${vertex}:${uv.toString('hex')}`;
      if (!cornerIndex.has(key)) {
        cornerIndex.set(key, corners.length);
        corners.push({vertex, uv});
      }
      ids.push(cornerIndex.get(key));
    }
    const vertices = ids.map(i => corners[i].vertex);
    const headCorners = vertices.filter(v => source.readFloatBE(header + v * 12 + 4) >= 26).length;
    if (headCorners === 3) headFaces.add(ids.join(':'));
    // 连同颈部切面外沿的一圈锁定，避免身体的三角面被简化进眼睑、唇线所在的头部。
    if (headCorners > 0) for (const vertex of vertices) protectedVertices.add(vertex);
    groups[material].push(...ids);
  }
  const animation = source.subarray(cursor);
  const positions = new Float32Array(corners.length * 3), attributes = new Float32Array(corners.length * 5);
  const lock = new Uint8Array(corners.length);
  for (const [i, {vertex, uv}] of corners.entries()) {
    for (let axis = 0; axis < 3; axis++) {
      positions[i * 3 + axis] = source.readFloatBE(header + vertex * 12 + axis * 4);
      attributes[i * 5 + axis] = source.readFloatBE(normals + vertex * 12 + axis * 4);
    }
    attributes[i * 5 + 3] = uv.readFloatBE(0);
    attributes[i * 5 + 4] = uv.readFloatBE(4);
    if (protectedVertices.has(vertex)) lock[i] = 1;
  }

  const remap = new Map(), indexGroups = [];
  const retainedHead = new Set();
  let triangles = 0, error = 0;
  for (const indices of groups) {
    if (!indices.length) { indexGroups.push(new Uint32Array()); continue; }
    // 在 UV 缝边和外边界保形，优先减少身体上轮廓变化较小的三角面。
    const budget = 3 * Math.max(1, Math.round(indices.length / 3 * targetFaces / faces));
    const [reduced, appearanceError] = MeshoptSimplifier.simplifyWithAttributes(
      Uint32Array.from(indices), positions, 3, attributes, 5, [.3, .3, .3, 1, 1], lock, budget, .012, ['LockBorder']);
    indexGroups.push(reduced);
    triangles += reduced.length / 3;
    error = Math.max(error, appearanceError);
    for (let i = 0; i < reduced.length; i += 3) {
      const ids = reduced.subarray(i, i + 3);
      const vertices = Array.from(ids, id => corners[id].vertex);
      assert.equal(new Set(vertices).size, 3, '低模包含退化三角面');
      const key = ids.join(':');
      if (ids.every(id => positions[id * 3 + 1] >= 26)) retainedHead.add(key);
      for (const vertex of vertices) if (!remap.has(vertex)) remap.set(vertex, remap.size);
    }
  }
  assert.ok(retainedHead.size === headFaces.size && [...retainedHead].every(key => headFaces.has(key)),
    '低模改变了受保护的头部面片');
  assert.ok(triangles < faces && triangles * 2 < faces, '低模的几何量下降不足');

  // NAIWA001 用大端数值。只重排被引用的顶点，并原样复制骨骼权重与动画段。
  const metadata = Buffer.from(source.subarray(0, header));
  metadata.writeInt32BE(remap.size, 8);
  metadata.writeInt32BE(triangles, 20);
  const faceData = Buffer.alloc(triangles * 40);
  cursor = 0;
  for (const [material, indices] of indexGroups.entries()) {
    for (let i = 0; i < indices.length; i += 3, cursor += 40) {
      faceData.writeInt32BE(material, cursor);
      for (let corner = 0; corner < 3; corner++) {
        const {vertex, uv} = corners[indices[i + corner]];
        faceData.writeInt32BE(remap.get(vertex), cursor + 4 + corner * 4);
        uv.copy(faceData, cursor + 16 + corner * 8);
      }
    }
  }
  const kept = [...remap.keys()];
  const stream = offset => kept.map(vertex => source.subarray(offset + vertex * 12, offset + vertex * 12 + 12));
  const bytes = Buffer.concat([metadata, ...stream(header), ...stream(normals), ...kept.map(v => weights[v]), faceData, animation]);
  return {bytes, vertices: kept.length, triangles, protectedFaces: headFaces.size, error};
}
