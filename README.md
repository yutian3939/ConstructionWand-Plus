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
- **Plus 行为核心**：建筑、时间、秒杀、挖掘、神化（下述），以及可拆卸的核心系统（左键选核心、Shift+左键卸下）。
- **物品核心**：储存（绑定容器后从容器取放方块）、ME 网络、ProjectE（消耗 EMC）。
- **强化升级**：强化时运（连续滑条，按品质设上限）、自动熔炼、防御、飞行等。
- **时间核心**：右键方块加速，倍率与持续时间在选项页设置，可批量解除。
- **秒杀核心**：左键秒杀单体、潜行右键范围击杀；支持必定掉落（走战利品表遍历，不采样）。
- **神化核心**（2.16.50 起）：与 **Apotheosis / Apothic Enchanting** 联动
  - 选项页可调 **世界层级（Haven→Pinnacle）** 与 **位阶 / 量子化 / 阿卡那（0–100）**；
  - 右键附魔台 → **取消点击、固定该台的数值**，并按需授予对应层级进度真正解锁世界层级；
  - 兼容**原版附魔台、神化附魔台、乌鸦附魔台**，以及 **Enchanting Infuser 的附魔注入器**（注入器只认位阶）；
  - 灌注行按手杖数值判定是否满足配方条件，不满足时保留该行并标记不可用；
  - 配方：**宝石粉 ×4 + 附魔台**（`apotheosis:gem_dust`）。
- **兼容**：Curios、L2 Backpacks、AE2（便携元件/无线终端）、Tom's Simple Storage、Refined Storage、Sable、ProjectE，
  以及潜影盒/精妙背包的嵌套读取；无需潜行的功能键（默认左 Ctrl）。

## 依赖（1.21.1）

必需：NeoForge 21.1.235+。
以下为**可选**联动，装了就启用对应核心/行为：`apotheosis`、`apothic_enchanting`（+ `placebo`、`apothic_attributes`）、
`enchantinginfuser`（+ `puzzleslib`）、`ae2`、`projecte`、`curios`、`botania`（编译期）。

## 文档与历史

- 详细改动、每个版本的实现理由与实测记录：[**README-PLUS.md**](README-PLUS.md)（中文，按版本倒序）。
- 截图：`docs/`（法杖界面、核心列表、神化核心选项页等）。
- 版本标签：`v2.16.73` 对应 1.21.1 当前版本；`1.20` 分支的标签见该分支。

## 许可证

沿用上游 Construction Wand 的许可（见 [LICENSE](LICENSE)）。