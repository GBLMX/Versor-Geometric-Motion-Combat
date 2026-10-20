# 数学与物理模型

本文说明 `versor-java` 与 `versor-geometric-motion-combat` 实际使用的几何代数、
刚体运动、场论和战斗/流体模型。所有公式都能在对应源码中找到实现；这里给出推导、
约定和数值边界。

符号约定：

- 三维欧氏空间用 `Cl(3,0)`，基矢 $e_1,e_2,e_3$，$e_i^2=1$。
- 投影几何代数 `PGA3` 用 `Cl(3,0,1)`，基矢 $e_0,e_1,e_2,e_3$，其中
  $e_0^2=0$，$e_i^2=1\ (i=1,2,3)$。
- 共形几何代数 `CGA3` 用 `Cl(4,1)`，$e_1,\dots,e_4$ 平方为 $+1$，
  $e_5$ 平方为 $-1$。
- $AB$ 表示几何积，$A\wedge B$ 表示外积，$A\rfloor B$ 表示左缩并；
  $\tilde A$ 表示 reverse，$A^*$ 表示 Poincaré dual。

## 1. 几何代数核心

一个多向量写成基刃的线性组合：

$$
A=\sum_{I\subseteq\{0,\dots,n-1\}} a_I e_I,\qquad
e_I=e_{i_1}\cdots e_{i_k},\quad i_1<\cdots<i_k .
$$

几何积同时编码内积和外积：

$$
e_i e_j =
\begin{cases}
+1,& i=j,\ \text{且 }e_i^2=+1,\\
-1,& i=j,\ \text{且 }e_i^2=-1,\\
0,& i=j,\ \text{且 }e_i^2=0,\\
e_{ij},& i\ne j.
\end{cases}
$$

代码中的 `Signature` 保存每个生成元的度量，`Multivector` 保存 $2^n$ 个稠密系数。
外积和缩并按子掩码枚举，避免在热路径里构造临时对象。

转子的指数映射由 `Gen.expBivector` 实现。设简单双向量（或 null 双向量）$B$
满足 $B^2=\lambda$：

$$
\exp B =
\begin{cases}
\cos r + \dfrac{\sin r}{r}B, & \lambda<0,\quad r=\sqrt{-\lambda},\\
\cosh r + \dfrac{\sinh r}{r}B, & \lambda>0,\quad r=\sqrt{\lambda},\\
1+B, & \lambda=0.
\end{cases}
$$

`Gen.log` 是其逆映射；`Gen.slerp` 通过

$$
a\exp\!\left(t\log(\tilde a b)\right)
$$

在单位 versor 之间插值。这里不把角度存成额外状态，插值对象本身就是代数元素。

`Multivector` 还提供 `isFinite`、`isScalar`、`isHomogeneous`、`isBlade`、
`isVersor`。前三个检查系数的有限性和齐次性；`isBlade` 对 $k\ge 2$ 使用
缩并映射

$$
L_A:\ V^*\to \bigwedge^{k-1}V,\qquad \alpha\mapsto \alpha\rfloor A
$$

的秩判定：$A$ 是可分解 $k$-刃当且仅当 $\operatorname{rank}L_A=k$。二重向量
（grade 2）只需 $A\wedge A=0$，但三阶及以上会有反例，因此不能只检查外积。

## 2. 欧氏转子与三维旋转

`E3` 把轴 $\hat a=(a_x,a_y,a_z)$ 转成双向量

$$
B=a_x e_{23}-a_y e_{13}+a_z e_{12},
$$

然后构造

$$
R=\exp\!\left(-\frac{\theta}{2}B\right)
 =\cos\frac{\theta}{2}-B\sin\frac{\theta}{2}.
$$

向量（以及任意多向量）的旋转是 sandwich：

$$
x' = R x \tilde R.
$$

这与四元数同构；区别是这里没有把 $R$ 的四个系数解释成隐藏的“角度/轴”，
而是直接把 $R$ 当作偶 versor 参与乘法和插值。

## 3. PGA3：点、面、线、motor

### 3.1 编码

PGA3 的点是三重向量：

$$
P(x,y,z)=e_{123}-z e_{012}+y e_{013}-x e_{023}.
$$

平面是向量，采用与 `P3.plane` 一致的法向/偏移约定：

$$
\pi=n_x e_1+n_y e_2+n_z e_3+d e_0,\qquad
n\cdot x+d=0.
$$

直线由两点 join 得到：

$$
L=P_1\vee P_2.
$$

从直线的六个系数可以恢复：

$$
d=(L_{23},-L_{13},L_{12}),\qquad
m=(L_{01},L_{02},L_{03})=p\times d,
$$

其中 $p$ 是直线上任意一点。离原点最近的锚点为

$$
p_0=\frac{d\times m}{\lVert d\rVert^2}.
$$

点到直线的最近点和距离分别是

$$
p_\perp=p_0+\frac{(p-p_0)\cdot d}{\lVert d\rVert^2}d,\qquad
\operatorname{dist}(p,L)=\lVert p-p_\perp\rVert .
$$

这些就是 `P3.lineDirection`、`P3.lineMoment`、`P3.lineAnchor`、
`P3.closestPointOnLine` 和 `P3.distancePointLine` 的实现。

### 3.2 平移、旋转与 motor

平移器使用 null 双向量：

$$
T(t)=1-\frac12\left(t_x e_{01}+t_y e_{02}+t_z e_{03}\right).
$$

绕轴 $\hat a$ 的 PGA 转子与 E3 相同，只是双向量改用 PGA 基矢：

$$
R=\cos\frac{\theta}{2}-B\sin\frac{\theta}{2}.
$$

刚体运动是“先旋转、再平移”的 motor：

$$
M=T R,
$$

作用在几何元素上仍然是

$$
X'=M X \tilde M.
$$

`P3.motor`、`P3.apply`、`P3.translationOf`、`P3.rotationMatrix` 和
`P3.rotationOf` 都建立在这个 sandwich 上。`P3.slerp` 对旋转部分做转子插值，
对平移部分做线性插值。

### 3.3 求交与盾面

两平面求交是外积：

$$
L=\pi_1\wedge\pi_2.
$$

平面与直线求交：

$$
P=\pi\wedge L.
$$

盾反判定用的就是这两个操作：先由防守者眼位和视线构造盾面
$\pi(x)=n\cdot x+d=0$，再把刀弧的每一段变成 PGA 直线，求
$\pi\wedge L$，最后检查交点是否落在盾牌半径内。详见
`src/main/java/com/gblmx/versormotion/combat/GuardPlane.java`。

## 4. 共形模型（CGA3）

`Conformal` 使用 `Cl(4,1)`。定义零向量

$$
n_0=\frac{e_5-e_4}{2},\qquad n_\infty=e_4+e_5,
\qquad n_0\cdot n_\infty=-1.
$$

三维点嵌入为

$$
P(x)=x+\frac{\lVert x\rVert^2}{2}n_\infty+n_0,
$$

因此 $P(x)^2=0$，且

$$
P(x)\cdot P(y)=-\frac12\lVert x-y\rVert^2.
$$

球面和平面在代码中都是 IPNS 向量：

$$
S=P(c)-\frac{r^2}{2}n_\infty,\qquad
\pi=n-d\,n_\infty,
$$

其中平面仍采用 $n\cdot x+d=0$ 的约定。圆则由圆上三点构造为 OPNS 三重向量：

$$
C=P_1\wedge P_2\wedge P_3.
$$

点是否在球面/平面上检查 $P\cdot S=0$、$P\cdot\pi=0$；点在圆上检查
$P\wedge C=0$。`Conformal` 的 `dot`、`sphere`、`plane`、`circle` 就建立
在这些关系上。

从编码恢复球心/半径时，令 $w=S_{e_5}-S_{e_4}$：

$$
c=\frac{(S_{e_1},S_{e_2},S_{e_3})}{w},\qquad
r^2=\lVert c\rVert^2-\frac{S_{e_4}+S_{e_5}}{w}.
$$

平面的坐标由

$$
d=-\frac{\pi_{e_4}+\pi_{e_5}}{2}
$$

恢复。`Conformal.distancePointSphere` 计算
$\bigl|\lVert p-c\rVert-r\bigr|$，`Conformal.closestPointOnSphere` 返回
球面上沿 $p-c$ 方向的最近点；平面版本与 PGA 的点-面公式一致：

$$
\operatorname{dist}(p,\pi)=
\frac{n\cdot p+d}{\lVert n\rVert},\qquad
p_\perp=p-\frac{n\cdot p+d}{\lVert n\rVert^2}n.
$$

## 5. 刚体运动学：twist 与精确导数

`Motion` 把刚体运动写成单参数 versor：

$$
M(t)=\exp(tW),\qquad W\in\bigwedge^2\mathbb R^{3,0,1}.
$$

$W$ 是常量 twist。点在时刻 $t$ 的位置是

$$
P(t)=M(t)p\tilde M(t).
$$

对 $t$ 求导，利用 $\dot M=WM=MW$ 和 $\tilde W=-W$：

$$
\begin{aligned}
P'(t) &= W P-P W,\\
P''(t) &= W^2P+P W^2-2WPW,\\
P'''(t) &= W^3P-3W^2PW+3WPW^2-P W^3.
\end{aligned}
$$

`Motion.velocity`、`Motion.acceleration`、`Motion.jerk` 直接计算这三条式子，
不做有限差分。`Motion.rotation` 和 `Motion.screw` 通过
`Gen.log` 把旋转/平移生成元放进同一个 twist；`Motion.translation` 使用
纯平移生成元。

在 PGA 中，点的“向量部分”可以从点 trivector 的 $e_{012},e_{013},e_{023}$
系数恢复，运动学导数则读取 point-at-infinity 的方向部分。`GaTest` 用有限差分
交叉检查了 $P',P'',P'''$，对应容差分别为 $10^{-5}$、$10^{-4}$、$10^{-5}$。

## 6. 场论与流线烘焙

`Calculus` 在 `Cl(3,0)` 上做数值几何微积分。对多向量场 $F(x)$，向量导数为

$$
\nabla F=e_1\frac{\partial F}{\partial x}
      +e_2\frac{\partial F}{\partial y}
      +e_3\frac{\partial F}{\partial z}.
$$

它的标量部分是散度：

$$
\nabla\cdot F=\langle \nabla F\rangle_0 .
$$

它的二重向量部分与旋度对偶：

$$
\nabla\wedge F=-I\,\operatorname{curl}F,\qquad
\operatorname{curl}F=-I(\nabla\wedge F),
$$

其中 $I=e_1e_2e_3$。`Calculus.curl` 正是先取二重向量部分，再乘 $-I$。
偏导使用中心差分，步长 `Calculus.STEP = 1e-4`；通量使用参数曲面上的中点
求积，并乘以 `Manifold.jacobian`。

### 6.1 无散度涡旋

`StreamBake` 取标量流函数

$$
\psi(x,z)=-\frac12\ln(1+x^2+z^2),
$$

并令势为 $A=(0,\psi,0)$。于是

$$
v=\nabla\times A
 =\left(-\frac{\partial\psi}{\partial z},0,
        \frac{\partial\psi}{\partial x}\right)
 =\left(\frac{z}{1+r^2},0,-\frac{x}{1+r^2}\right),
\qquad r^2=x^2+z^2.
$$

因为旋度的散度为零，$\nabla\cdot v=0$；粒子沿速度方向做弧长步进，
不会在源/汇处堆积。代码把 $v$ 归一化后前进固定
`STEP_LENGTH=0.35`，所以速度大小只影响方向，不影响步长。

### 6.2 Rankine 龙卷风

`TornadoBake` 使用解析速度场。以漏斗轴为 $y$ 轴，$r=\sqrt{x^2+z^2}$，
半径随高度变化：

$$
r_0(y)=r_{\text{base}}+(r_{\text{top}}-r_{\text{base}})
\left(\frac{y}{H}\right)^2,
$$

并在 $y\in[0,H]$ 截断。速度分解为切向、径向和轴向：

$$
\begin{aligned}
v_t &= V_{\text{core}}\min\left(1,\frac{r}{r_0}\right),\\
v_r &= -\text{INFLOW}\cdot v_t,\\
v_y &= U_{\text{up}}\exp\left(-\frac{r^2}{r_0^2}\right)+V_{\text{rise}}.
\end{aligned}
$$

向量形式为

$$
v=v_t\hat\theta+v_r\hat r+v_y e_y,
$$

其中 $\hat\theta=(-z,x,0)/r$，$\hat r=(x,0,z)/r$。内核近似刚体旋转，
外侧是 $1/r$ 衰减的 Rankine 涡；径向入流和轴心上升气流把流线拉成漏斗。
烘焙时同样归一化速度并做弧长步进，且把点限制在
`CORE_FLOOR * r0(y)` 之外，避免流线穿过轴心。

## 7. 战斗几何

### 7.1 刀弧参数化

`ArcSweep.bake` 从眼睛位置出发，先构造水平 reach 向量：

$$
q_0=(-r\sin\psi,0,r\cos\psi),
$$

其中 $\psi$ 是 Minecraft yaw（0 面向 $+Z$，$+90^\circ$ 面向 $-X$）。
起始方向再把 $q_0$ 绕 $+Y$ 旋转 $-\theta_{\text{half}}$：

$$
q_{\text{start}}=\exp(-\theta_{\text{half}}B_y)q_0,
$$

其中 $B_y=-e_{13}$ 是绕 $+Y$ 的单位双向量。

然后用 `Motion.rotation(0,1,0,2\theta_{\text{half}})` 在 $t\in[0,1]$ 上
扫过整个 $2\theta_{\text{half}}$。默认

$$
\theta_{\text{half}}=65^\circ,\qquad N=48.
$$

第 $i$ 个采样点为

$$
q_i=\exp\left(\frac{i}{N-1}W\right)q_{\text{start}},\qquad
W=\text{绕 }+Y\text{ 的 }130^\circ\text{ 旋转生成元}.
$$

加上眼位后得到客户端和服务器共用的弧点。客户端只回放这些点，不重新判定。

### 7.2 弧段与 AABB 的距离

对弧的每一段 $s(t)=a+t(b-a)$，$t\in[0,1]$，计算其到轴对齐盒
$B=[\min,\max]$ 的平方距离：

$$
d^2(t)=\left\lVert s(t)-\operatorname{clamp}(s(t),B)\right\rVert^2 .
$$

$\operatorname{dist}(\cdot,B)$ 是凸函数，因此 $d^2(t)$ 在区间上也是凸的。
`ArcSweep.segmentDistanceSq` 用三分搜索迭代 32 次求最小值；这比只检查采样点
更可靠，能避免目标从两个采样点之间“穿过”而漏判。命中条件是

$$
\min_t d^2(t)\le r_{\text{blade}}^2,\qquad r_{\text{blade}}=0.55.
$$

### 7.3 盾反

防守者格挡且面向攻击者时，由眼位 $o$ 和视线单位向量 $n$ 构造盾面：

$$
\pi(x)=n\cdot(x-o)=0.
$$

`GuardPlane.intersect` 对每段弧先算端点的符号距离 $s_a=\pi(a)$、
$s_b=\pi(b)$；只有 $s_as_b<0$ 才可能与平面相交。然后构造 PGA 直线
$L=a\vee b$，求 $P=\pi\wedge L$，并把交点限制在盾牌半径
`REACH=1.15` 内。若命中，整次攻击取消，攻击者被击退。

服务器还会检查 `blocks_attacks` 组件、`bypassed_by` 规则和防守者朝向
（视线与“指向攻击者”的单位向量点积 $>0.45$）。这些判定不依赖客户端。

### 7.4 伤害与击退

`CombatManager` 在服务端把一次挥击的弧与附近实体的 AABB 做距离测试。
直击目标从弧判定中排除，避免原版直击和弧线各造成一次伤害。弧伤害为

$$
D=D_{\text{arc}}\left(0.2+0.8\,s^2\right),
$$

其中 $s$ 是 `getAttackStrengthScale(0.5f)`。击退只取水平方向：

$$
\Delta v=\text{knockback}\cdot
\frac{(x_{\text{target}}-x_{\text{attacker}},0,
z_{\text{target}}-z_{\text{attacker}})}
{\lVert (x_{\text{target}}-x_{\text{attacker}},0,
z_{\text{target}}-z_{\text{attacker}})\rVert}
+(0,0.15,0).
$$

## 8. 流体阻力与重力

### 8.1 二次阻力

原版水阻力是每 tick 乘固定系数。`FluidDrag` 把基础模型改为二次阻力，
水平方向再乘一个增益系数：

$$
\frac{dv}{dt}=-k\lVert v\rVert v.
$$

其中

$$
k=\frac12\frac{\rho C_d A}{m}\cdot\text{submerged},
$$

$A=w h$、$m=\rho w^2 h$。一个隐式步进近似为

$$
v_{\text{new}}=\frac{v}{1+k\lVert v\rVert\Delta t},
$$

代码把速度单位取为 blocks/tick，因此 $\Delta t=1$。水平方向额外乘
`dragScale`，用来保留原版冲刺/海豚恩惠的相对收益；垂直方向保持完整
$k$。气泡柱不适用自由流阻力，继续走原版阻尼。

### 8.2 重力与跳跃

取 1 block = 1 m、20 tick/s，则

$$
g=10\ \text{m/s}^2=\frac{10}{400}\ \text{blocks/tick}^2=0.025.
$$

水下净重力按浸没比例线性削减：

$$
a_y=-0.025(1-\text{submerged}).
$$

原版跳跃初速度约为 0.42、空中重力约为 0.08；代码在 0.025 的重力下把跳跃
初速度乘 `JUMP_SCALE=0.6` 得到约 0.252，以保持接近原版的跳跃高度
（源码注释给出的目标约为 1.25 格）。

### 8.3 翻滚

`/versor roll` 取视线水平方向，施加 0.6 blocks/tick 的冲量，保留非负的
垂直速度，并给出 10 tick 无敌与 20 tick 冷却。状态由服务器广播，
客户端只播放 rotor 翻转和四肢收拢动画。

## 9. 数值与验证

- **逆与条件数**：`Multivector.inverseGeneral` 带均衡化、残差和条件数检查；
  近奇异矩阵不会被静默当成可逆。
- **批量**：`MultivectorBatch` 用 SoA 存储，`gp/gpRight/op/ip/dual/undual`
  按 blade 连续计算；`sandwich(versor, out)` 复用输出缓冲。
- **容差**：标量/齐次/刃/versor 判定使用相对容差，避免把 $10^{-20}$ 量级的
  非简单刃误判为简单刃。
- **测试**：`gaTest` 覆盖产品、dual、逆、batch、P3/Conformal 查询和验证谓词；
  `combatTest` 覆盖刀弧、盾面、场烘焙和规则；`weTest` 覆盖选区/形体/剪贴板。

运行：

```bash
JAVA_HOME=~/.jdks/jbrsdk-25.0.3 ./gradlew gaTest
JAVA_HOME=~/.jdks/jbrsdk-25.0.3 ./gradlew combatTest
JAVA_HOME=~/.jdks/jbrsdk-25.0.3 ./gradlew weTest
```
