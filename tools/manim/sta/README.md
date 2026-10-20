# STA 图形学：Wolfram Cl(1,3) → manimgl

用 Wolfram Language 15 内置的 `CliffordAlgebra` 做时空代数（STA, Cl(1,3)）计算，
把数值导出为 JSON，再由 3b1b 版 manim（manimgl）渲染成动画。Python 侧不实现
Clifford 代数，只做插值与绘图。

## 文件

| 文件 | 作用 |
|---|---|
| `sta.wl` | 构造 Cl(1,3)，计算 boost 转子、reversion、Wigner 旋转，校验后导出 `data/sta.json` |
| `data/sta.json` | 61 个 rapidity 采样点上的向量分量、收缩因子、旋转矩阵/角度 |
| `sta_scene.py` | `STASpacetime`（2D：转子 + 双曲轨迹 + 尺缩）、`STAGraphics`（3D：线框立方体 boost 收缩、Wigner 旋转） |

## 数学（全部由 Wolfram 计算并校验）

- 生成元：$\gamma_0^2=+1,\ \gamma_1^2=\gamma_2^2=\gamma_3^2=-1$，互反交换。
- Boost 转子：$R=e^{-\frac{\phi}{2}\gamma_0\gamma_1}=\cosh\frac{\phi}{2}-\sinh\frac{\phi}{2}\gamma_0\gamma_1$，
  其 reversion $\tilde R=R^{-1}$，且 $R\tilde R=1$。
- 夹心变换：$v'=R\,v\,\tilde R$，得到
  $\gamma_1\mapsto \cosh\phi\,\gamma_1+\sinh\phi\,\gamma_0$，
  $\gamma_0\mapsto \cosh\phi\,\gamma_0+\sinh\phi\,\gamma_1$。
  空间长度比 $1/\cosh\phi$ 即 Lorentz 收缩。
- 两个垂直 boost 复合 $R_2R_1$ 不是纯 boost：其空间部分 $A$ 做极分解
  $A=R_W S$（$S$ 对称正定），$R_W$ 即 Wigner/Thomas 旋转，角度由
  $\operatorname{tr}R_W$ 提取。

`sta.wl` 运行时输出的校验：signature 矩阵 `diag(1,-1,-1,-1)`、
`R Rev(R)=1`、$R_W$ 正交误差 $\sim10^{-16}$、$S$ 对称误差 $\sim10^{-15}$、
$\phi=0$ 时 Wigner 角为 0；$\phi=3$ 时约 $78.7^\circ$。

## 运行

```bash
# 1) 生成/刷新数据（约 1 秒；从 tools/manim 目录运行）
wolframscript -file sta/sta.wl

# 2) 渲染（从 tools/manim 目录运行，环境由 uv.lock 固定）
uv run manimgl sta/sta_scene.py STASpacetime -w --video_dir videos
uv run manimgl sta/sta_scene.py STAGraphics -w --video_dir videos
```

输出为 `videos/STASpacetime.mp4` 与 `videos/STAGraphics.mp4`；加 `-l` 可低分辨率快速预览。

## 环境

- Wolfram Engine 15.0（`CliffordAlgebra` 为 15.0 新增内置函数；14.x 无此函数）
- 环境固定在 `../pyproject.toml` 与 `../uv.lock`：Python 3.14、manimgl commit `fafa083a`、`av`
- `uv sync --frozen` 创建 `tools/manim/.venv`；不再依赖 `/tmp/manim-venv`
- `manimpango` 无 cp314 Linux wheel，uv 会源码编译（系统需 pango 头文件）
- 注意：上游 `setup.cfg` 的 `install_requires` 漏了 `av`（它在 `requirements.txt` 中），因此根目录 `pyproject.toml` 显式加入 `av`
- 渲染：wgpu/Vulkan（RTX 3060），LaTeX 经 `latex` + `dvisvgm`
