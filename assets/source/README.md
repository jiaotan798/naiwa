# 当前模型来源

奶蛙 0.5.0 的可编辑模型为 [nailong_origin_zero.bbmodel](nailong_origin_zero.bbmodel)，包含 52 根骨骼、18,078 个顶点、36,001 个三角面、一张 2048×2048 贴图及 11 段动画。新增面部骨骼让大笑时的嘴部和脸颊随动作变形。原有手指骨仍参与整蛊姿势和道具定位。

[nailong_face_normals.json](nailong_face_normals.json) 保存鼻梁到嘴角修形所用的法线，必须与当前源模型配套。运行时的低模由高模派生，包含 9,999 个三角面，用于远景及采用该路径的实体阴影通道。

[模型接入与采样](../../docs/模型接入说明.md) · [素材指纹](../../third-party/asset-sha256.json)
