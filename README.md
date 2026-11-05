# ConstructionWand-Plus

[Construction Wand](https://github.com/Theta-Dev/ConstructionWand) 的增强分支合集：在原版手杖的基础上补齐了大量模组联动与操作优化。
本仓库用**两个分支**分别维护两个 Minecraft 版本，**1.21.1 为当前主力版本（默认分支）**。

| 分支 | 游戏版本 / 加载器 | 状态 | 说明 |
|---|---|---|---|
| **main**（默认） | **1.21.1 / NeoForge** | 主力维护 | 基于 [ConstructionWand-KOTS](https://github.com/GLDYM/ConstructionWand-KOTS)，Plus 内容重写实现，详见 [README-PLUS.md](README-PLUS.md) |
| [1.20](https://github.com/yutian3939/ConstructionWand-Plus/tree/1.20) | 1.20.1 / Forge | 维护中 | 早期的 1.20.1 移植版，功能与 1.21.1 基本对齐 |
| legacy-main | 历史 | 归档 | 本仓库最初的内容，仅作留档 |

## 1.21.1（NeoForge）概要

- **环境**：JDK 21、NeoForge **21.1.235+**；编译 `./gradlew build`，产物在 `build/libs/`。
- **核心玩法**：一次放置 / 破坏最多 1024 个方块；方向、替换、随机、锁定、匹配等选项全部在法杖界面里调整。
- **Plus 行为核心**：建筑、时间、秒杀、挖掘、神化，以及可拆卸的核心系统（左键选核心、Shift+左键卸下）。
- **物品核心**：储存（绑定容器后从容器取放方块）、ME 网络、ProjectE（消耗 EMC）。
- **强化升级**：强化时运（连续滑条，按品质设上限）、自动熔炼、防御、飞行等。
- **时间核心**：右键方块加速，倍率与持续时间在选项页设置，可批量解除；大型机器点任意一格即可整机加速。
- **秒杀核心**：左键秒杀单体、潜行右键范围击杀；支持必定掉落。
- **神化核心**：与 Apotheosis / Apothic Enchanting 联动，右键附魔台取消点击并固定数值。
- **兼容**：Curios、L2 Backpacks、AE2、Tom's Simple Storage、Refined Storage、Sable、ProjectE、Mekanism、
  Immersive Engineering，以及潜影盒/精妙背包的嵌套读取。

## 依赖（1.21.1）

必需：NeoForge 21.1.235+。
可选联动，装了就启用对应核心 / 行为：`apotheosis`、`apothic_enchanting`（+ `placebo`、`apothic_attributes`）、
`enchantinginfuser`（+ `puzzleslib`）、`ae2`、`projecte`、`curios`、`botania`（编译期）、`mekanism`、
`immersiveengineering`。

## 文档与历史

- 详细改动、每个版本的实现理由与实测记录：[**README-PLUS.md**](README-PLUS.md)（中文，按版本倒序）。
- 截图：`docs/`（法杖界面、核心列表、神化核心选项页等）。
- 版本：当前 `2.16.85`（`v2.16.73` 之后尚未打标签）；`1.20` 分支的标签见该分支。

## 许可证

沿用上游 Construction Wand 的许可（见 [LICENSE](LICENSE)）。
