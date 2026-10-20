# Versor Geometric Motion Combat

Fabric mod for Minecraft **26.3** built on geometric algebra: rigid-body motion and
multivector fields from the [versor-java](https://github.com/GBLMX/versor-java) core.

## 当前状态

- 数学核心以 git submodule 引入（`lib/versor-java`），167 项断言通过。
- 客户端渲染 spike：
  - `Motion.rotation` 驱动的 PGA motor 立方体（玩家前方 3 格）；
  - 由多重向量场 `F(x) = (-y, x, 0)` 积分出的场流线；
  - 走 26.x 的 extraction / drawing 两阶段，经 `StagedVertexBuffer` 提交；
  - 绘制注册在 `LevelRenderEvents.END_MAIN`：`AFTER_TRANSLUCENT_TERRAIN` 位于主
    render pass 内部，`StagedVertexBuffer.upload()` 会在那里抛
    “Close the existing render pass before performing additional commands”。
- 服务端权威判定、姿态同步、粒子烘焙尚未接入。

## 环境

| 项 | 版本 |
|---|---|
| Minecraft | 26.3 |
| Java | 25 |
| Fabric Loader | 0.19.5 |
| Fabric API | 0.163.0+26.3 |
| Loom | 1.18-SNAPSHOT |

## 构建与运行

```bash
git clone --recursive git@github.com:GBLMX/Versor-Geometric-Motion-Combat.git
cd Versor-Geometric-Motion-Combat

JAVA_HOME=~/.jdks/jbrsdk-25.0.3 ./gradlew build
JAVA_HOME=~/.jdks/jbrsdk-25.0.3 ./gradlew runClient
```

数学检查：

```bash
./gradlew gaTest        # 167 checks
```

产物：`build/libs/versor-geometric-motion-combat-0.1.0.jar`。

## 结构

```
lib/versor-java/            纯数学核心 + JOML 适配（git submodule）
src/main/java/com/...       公共入口
src/client/java/com/...     客户端渲染与 mixin
src/main/resources/         fabric.mod.json
```

## 游戏内命令

WorldEdit 风格子命令，全部为只读查询，不需要玩家实体，因此可直接放进
命令方块 / 数据包函数 / 服务器控制台；坐标支持 `~`。

```text
/versor field at <pos>
/versor motion rotation <axis> <omega> <time> <point>
/versor sweep rotation <axis> <omega> <t0> <t1> <samples> <point>
/vm ...                                # 别名
```

- `field`：输出 `F`、散度、旋度；
- `motion`：输出位置、速度、加速度；
- `sweep`：输出扫掠 AABB 与路径长度，并返回采样数，可配合
  `execute store result ... run versor sweep ...` 使用。

示例（服务器控制台实测）：

```text
F(1.000, 2.000, 3.000) = (-2.000, 1.000, 0.000); div = 0.000; curl = (-0.000, -0.000, 2.000)
motion t=0.500: pos = (0.878, 0.479, 0.000); vel = (-0.479, 0.878, 0.000); acc = (-0.878, -0.479, 0.000)
sweep 4 samples: aabb = [(0.540, 0.000, 0.000), (1.000, 0.841, 0.000)]; length = 0.995
```

## 26.3 渲染 API 注意

26.3 把渲染栈从 `com.mojang.blaze3d.*` 迁到了 `com.mojang.renderpearl.*`：

```java
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
```

`RenderPass.setPipeline` 接收 `CompiledRenderPipeline`，需经
`RenderSystem.getCompiledPipeline(pipeline)` 取得。26.2 的文档仍用旧包名。

## 参考资料

| 来源 | 用途 |
|---|---|
| [versor-java](https://github.com/GBLMX/versor-java) | 核心库 |
| Macdonald, *Linear and Geometric Algebra* | 转子约定与插值 |
| Macdonald, *Vector and Geometric Calculus* | ∇F、通量积分 |
| [Klein](https://github.com/jeremyong/klein) | PGA 标量参考、符号表思路 |
| [Aerodynamics4MC-Core](https://github.com/MozillaFiredoge/Aerodynamics4MC-Core) | 薄入口、服务端权威、量化同步 |
