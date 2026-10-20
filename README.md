# Versor Geometric Motion Combat

Fabric mod for Minecraft **26.3** built on geometric algebra: rigid-body motion and
multivector fields from the [versor-java](https://github.com/GBLMX/versor-java) core.

## 当前状态

- 数学核心以 git submodule 引入（`lib/versor-java`），236 项断言通过。
- 客户端渲染 spike：
  - `Motion.rotation` 驱动的 PGA motor 立方体（玩家前方 3 格）；
  - 由多重向量场 `F(x) = (-y, x, 0)` 积分出的场流线；
  - 走 26.x 的 extraction / drawing 两阶段，经 `StagedVertexBuffer` 提交；
  - 绘制注册在 `LevelRenderEvents.END_MAIN`：`AFTER_TRANSLUCENT_TERRAIN` 位于主
    render pass 内部，`StagedVertexBuffer.upload()` 会在那里抛
    “Close the existing render pass before performing additional commands”。
- 服务端权威翻滚：`/versor roll` 触发 0.6 b/t 冲量、10 tick 无敌、20 tick 冷却；
  状态经 S2C 同步，客户端用 `AvatarRenderer.setupRotations` 与 `PlayerModel.setupAnim`
  叠加转子翻转与四肢收拢。
- 服务端权威挥砍判定：`AttackEntityCallback` 在 `Player.attack` 入口读取服务端的
  眼位/朝向，用 PGA 烘焙 48 点刀弧，段-AABB 距离用三分搜索（不会在采样点之间
  穿透）；直击目标由原版处理，弧内其余目标受伤并被击退。弧在 tick 末尾统一结算，
  并排除本 tick 所有直击目标，因此 Better Combat 这类一次挥击多次调用
  `Player.attack` 的多目标处理不会让同一目标吃到“直击 + 弧线”两份伤害。
- 攻击动作与粒子：`SwingPayload` 把烘焙弧点一次性发给追踪者，客户端回放
  `versormotion:swing_spark` 自定义粒子并叠加 rotor 身体扭转；命中点由服务端
  `sendParticles(SWEEP_ATTACK)` 补火花。
- 盾反：防御者格挡且面向攻击者时，刀弧与盾面（`P3` 平面）求交；命中则取消
  整次攻击、播放 `SHIELD_BLOCK` 与火花，并把攻击者击退。判定读取
  `blocks_attacks` 组件而非盾牌物品（并遵守组件的 `bypassed_by` 规则），因此
  Sword Blocking Mechanics 之类给剑加上格挡组件的模组也能触发盾反。
- 场驱动粒子：`StreamBake` 用 `∇×A` 构造无散度涡旋，翻滚与
  `/versor field burst <pos>` 触发；客户端按烘焙点回放。
- 龙卷风：`TornadoBake` 用 Rankine 旋涡（内核刚体旋转、外侧 `1/r`）、径向入流
  和轴心上升气流积分出漏斗流线；`/versor field tornado <pos>` 一次广播
  `TornadoPayload`（352 点 / 200 tick），客户端绕轴旋转、淡入淡出，并从同一
  点云每 tick 发射 `CLOUD` 雾气与 `ASH` 地面碎屑。
- 镜头：翻滚时相机侧倾，受伤时按伤害量衰减抖动（`Camera.extractRenderState` 注入）。
- CGA：`vsr.Conformal` 提供共形点/球/平面/圆（Cl(4,1)），含点-球、点-圆关联检查。
- 创世神式编辑：`/we` 命令树 + `//` 拦截，提供选区、set/replace、球/圆柱、copy/cut/paste、
  undo/redo；单次编辑上限 32768 方块，详见下文。
- Display Entity 兼容：`DisplayTransforms` 读取/写回完整 `transformation`，
  `left_rotation`、`right_rotation`、`translation`、`scale` 互不覆盖；
  `/versor display rotate` 用 E3 转子修改任一侧。

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
./gradlew gaTest        # 201 checks
./gradlew combatTest    # 245 checks（115 刀弧 + 9 盾面 + 79 场烘焙 + 16 龙卷风 + 26 规则）
./gradlew weTest        # 38 checks（选区 / 形体 / 剪贴板 / undo）
```

产物：`build/libs/versor-geometric-motion-combat-0.1.0.jar`。

## 结构

```
lib/versor-java/            纯数学核心 + JOML 适配（git submodule）
src/main/java/com/...       公共入口、combat 判定、field 烘焙、wedit 编辑核心、Display 变换、网络包
src/client/java/com/...     客户端渲染、动画、镜头、自定义粒子与 mixin
src/main/resources/         fabric.mod.json、mixin 配置
src/client/resources/       assets（swing_spark 粒子定义与贴图）
```

## 游戏内命令

WorldEdit 风格子命令。查询类命令（`field` / `motion` / `sweep`）不需要玩家实体，
可直接放进命令方块 / 数据包函数 / 服务器控制台；动作类（`attack` / `roll`）需要
玩家执行者。坐标支持 `~`。

```text
/versor field at <pos>
/versor field burst <pos>
/versor field tornado <pos>
/versor motion rotation <axis> <omega> <time> <point>
/versor sweep rotation <axis> <omega> <t0> <t1> <samples> <point>
/versor display rotate <targets> <axis> <angle> [local]
/versor attack                         # 挥砍：服务端判定并广播烘焙弧（需玩家执行者）
/versor roll                           # 翻滚（需玩家执行者）
/vm ...                                # 别名
```

- `field at`：输出 `F`、散度、旋度；
- `field burst`：在坐标处烘焙一次无散度涡旋粒子，返回粒子采样数；
- `field tornado`：在坐标处生成龙卷风（352 点漏斗、200 tick、客户端粒子），返回采样数；
- `motion`：输出位置、速度、加速度；
- `sweep`：输出扫掠 AABB 与路径长度，并返回采样数，可配合
  `execute store result ... run versor sweep ...` 使用；
- `display rotate`：对目标 Display 实体叠加 E3 转子。默认写入
  `left_rotation`（实体原点处的世界旋转）；末尾加 `local` 写入
  `right_rotation`（局部旋转）；角度单位为度。`translation`、`scale` 与另一侧旋转保持不变；
- `attack`：按服务端眼位/朝向判定，返回本次弧线额外命中的目标数；
- `roll`：0.6 b/t 冲量 + 10 tick 无敌，20 tick 冷却。

### 创世神式编辑

`wedit` 包提供一套最小编辑核心，命令挂在 `/we` 下；同时 mixin 拦截
`Commands.performCommand`，把玩家输入的 `//command` 翻译成 `we command`
（原版会先剥掉一个 `/`，所以服务端看到的是 `/set`）。命令方块与控制台同样可用。

```text
//wand | //pos1 | //pos2            # 木斧左键 pos1、右键 pos2；无需选区即可 //pos1
//size                              # 选区尺寸与体积（超限也会照常报告）
//count <filter>                    # 统计匹配方块，如 //count #minecraft:logs
//set <block>                       # 填充，如 //set minecraft:stone
//replace <to>                      # 把非空气替换为 <to>
//replace <from> <to>               # 过滤替换，<from> 支持方块谓词/标签
//sphere <block> <r> | //hsphere ... # 实心 / 空心球（球心取选区中心）
//cyl <block> <r> [height] | //hcyl ... # 实心 / 空心圆柱（高度居中）
//copy | //cut | //paste            # 相对玩家位置粘贴
//undo | //redo                     # 每名玩家 5 步历史
```

限制与差异：

- 单次编辑最多 **32768** 方块，半径 ≤ 32，圆柱高 ≤ 64；超限直接报错，不会部分执行。
- `//pos1` / `//pos2` 取玩家脚下方块，不做视线目标选择（木斧才是选点方式）。
- 不支持 NBT/容器内容、schematic、区域文件、多选点、mask 组合与超限绕过。
- 方块状态参数支持 `oak_stairs[facing=north,half=top]` 这类写法（按括号深度切分参数）。
- `//` 前缀会拦截所有以 `/` 开头的命令字符串；若同时安装 WorldEdit 等同样占用
  `//` 的模组，二者会冲突，二选一。

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
