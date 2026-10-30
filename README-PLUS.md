# ConstructionWand-Plus for 1.20.1 (Forge)

本目录（`ConstructionWand-1.20`）原本是 [ConstructionWand](https://github.com/Theta-Dev/ConstructionWand) 的 1.20.1 Forge 上游工程，现已在其基础上**按功能重实现**了
[ConstructionWand-Plus](../../ConstructionWand-Plus-main)（1.12.2 的魔改版，基于 ConstructionWandLegacy）的全部魔改内容。

- modid / 包名沿用 `constructionwand` / `thetadev.constructionwand`，因此它是 1.20.1 建筑手杖的**直接增强版**，物品与配方体系与原版一致。
- 移植物到独立包 `thetadev.constructionwand.plus.*`，上游代码保持可辨识，便于后续跟随上游更新。
- 1.20.1 机制差异处做了等价重写（见「与 1.12.2 版的差异」）。

## 新增内容

### 新增核心

| 核心 | 物品 id | 说明 |
|---|---|---|
| 挖掘核心 | `constructionwand:core_digging` | 挖掉方块但**保留掉落物**（破坏核心是直接销毁），支持精准采集、时运、自动冶炼、掉落去向 |
| 仓储核心 | `constructionwand:core_storage` | 以「绑定的容器」作为建筑材料来源，也可回存掉落物与流体 |
| 秒杀核心 | `constructionwand:core_slay` | 秒杀生物，掉落物与经验直接归玩家，支持时运（=抢夺）、精准采集刷怪蛋 |
| ME 核心 | `constructionwand:core_ae` | 仅在安装 **AE2** 时注册；以绑定的 ME 控制器所属网络供材/存料（物品与流体） |
| 转化核心 | `constructionwand:core_projecte` | 仅在安装 **ProjectE** 时注册；用玩家 EMC 供材，掉落物可转成 EMC |

> 核心分两类：**行为核心**（默认/天使/破坏/挖掘/秒杀）决定「手杖做什么」，**材料核心**（无/仓储/ME/转化）决定「材料从哪来」。两者在法杖配置界面分别切换，互不影响。

### 新增升级（被动，装上即常驻生效）

| 升级 | 物品 id | 效果 |
|---|---|---|
| 时运升级 | `constructionwand:upgrade_fortune` | 给挖掘核心附加时运等级（配置可调），对秒杀核心则作为抢夺等级 |
| 自动冶炼升级 | `constructionwand:upgrade_auto_smelt` | 挖掘掉落自动熔炼（矿石直接变锭），并正确结算熔炼经验 |
| 飞行升级 | `constructionwand:upgrade_flight` | 背包（含副手）里带着装有该升级的法杖即获得创造飞行，法杖离身即失去（离身瞬间有摔落保护） |
| 防御升级 | `constructionwand:upgrade_defense` | 背包（含副手）里带着装有该升级的法杖即获得无敌：免疫伤害与死亡、保持满血并清除负面效果 |

升级安装沿用原版动态配方：**法杖 + 核心/升级** 在合成栏合成即可安装；已安装的不会重复合成。

### 额外法杖选项

这些设置存在法杖 NBT 的独立根标签 `wand_extra_options`，不与原版 `wand_options` 混用：

1. `silk_touch`（否/是）——挖掘/秒杀核心是否精准采集；
2. `drops_destination`（背包/材料核心/地面）——掉落物去向（材料核心优先 = 先存核心再进背包，反之亦然，装不下才落地）；
3. `fluid_removal`（忽略/智能/当作方块）——流体处理：
   - `忽略`：完全不处理流体；
   - `智能`：只清除「被挖掉方块所围住的流体」（BFS 找围合口袋，上限 64 格，避免误清海洋）；
   - `当作方块`：流体作为普通方块参与匹配与破坏。
4. `fluid_storage`（否/是）——挖掘核心是否把清除的**源流体**存入材料核心（每格 1 桶，规划期按容量预扣，存不下就留在世界里）。

另有每根法杖单独的方块上限（`block_limit`）：配置界面滑条可调低单次操作上限，永不高于服务器配置，且只对这根法杖生效。

### 仓储核心与容器绑定

- 潜行右键容器即可绑定到法杖（也接受纯流体容器），按顺序追加；**对已绑定的容器再次潜行右键即解绑**；
- 容器被破坏/区块未加载后绑定不会崩溃，解析时静默跳过；该位置换成别的容器会被自动识别并使用；
- 绑定列表统一提供物品/流体的 insert/count/take/fill/drain/capacity 接口；
- 材料来源：绑定容器优先、玩家背包兜底（选了仓储核心时同样是这个顺序）；
- 兼容 Mekanism 流体储罐（流体 capability 按 `UP 面 → 其他面 → null 面` 顺序探测，并读取真实剩余容量而不是用 1 桶探测封顶）。

### 撤销系统增强

- 挖掘撤销「全有或全无」回收掉落物（背包 + 材料核心），缺失则拒绝撤销并保留历史以便重试；
- 撤销挖掘时扣回已发放经验（含等级精确计算）；
- 一次撤销里多个方块缺失的物品/流体合并成一条聊天提示，避免刷屏；
- 撤销/放回时清除侵入的流动液体（覆盖破坏、挖掘、流体放回等全部路径）。

### 配置界面增强

- 左栏：核心/材料核心/锁定/方向/替换/匹配/随机/精准采集/掉落去向/流体处理/流体储存 + 方块上限滑条；
- **SHIFT+左键点击核心**即可卸下核心（返回物品）；
- 右栏：升级列表——**左键点击切换启用/禁用**（禁用后仍安装但效果失效，图标变暗），**SHIFT+左键卸下**；
- 右栏：绑定容器列表——`↑`/`↓` 调整优先级，`✕` 解绑；选中 ME/转化核心时显示 ME 绑定状态与玩家 EMC。

### 创造物品栏

所有物品归入独立创造标签页（图标为无尽手杖），顺序：法杖（弱→强）→ 行为核心 → 材料核心 → 升级。

### 指令

- `/constructionwand limit`（别名：无）：查看仓储核心绑定容器上限；
- `/constructionwand limit <值>`：设置上限，`-1` 表示无限制（权限等级 2，改动写回配置并同步）。

## 按键与操作

原版：

- `Shift + Ctrl + 鼠标滚轮`：切换锁定模式；
- `Shift + Ctrl + 左键`（对空气）：切换行为核心；
- `Shift + Ctrl + 右键`（对空气或方块）：打开法杖配置 GUI；
- 按住 `Shift + Ctrl`：显示撤销预览；`Shift + Ctrl` + 右键点击高亮方块：执行撤销。

魔改新增：

- 撤销按键（默认 `Z`，可在按键设置里改）：按住 `Shift + Ctrl` 时按下即可撤销，不必再瞄准方块；
- 挖掘核心：右键挖方块（保留掉落）；流体处理设为「当作方块」时，可直接瞄准水面/岩浆面；
- 秒杀核心：左键单体秒杀，潜行右键范围秒杀；
- 仓储核心：潜行右键容器绑定（再次潜行右键解绑）；
- ME 核心：潜行右键 ME 控制器绑定网络；
- 配置界面：点击升级图标切换启用/禁用，`Shift`+点击卸下；绑定容器列表显示方块名，未加载区块也能显示。

## 配置文件

原版配置节（`wandLimits`、`placement`、`matching`、`performance` 等）保持不变，新增：

```toml
[wandUpgrades]
	# Fortune level the fortune upgrade component gives the digging core
	fortuneLevel = 3

[storage]
	# Max containers one wand may bind for the storage core. Set -1 for unlimited.
	maxBoundContainers = 8

[digging]
	breakUnbreakable = false   # 挖掘核心能否破坏基岩等不可破坏方块
	dropUnbreakable = false    # 破坏的不可破坏方块是否掉落（仅 breakUnbreakable 开启时生效）

[placement]
	# 放置白名单，格式 modid:block，留空表示不限制
	BlockWhitelist = []
	# 放置黑名单，格式 modid:block
	BlockBlacklist = []
	# TARGET 模式下允许复制的方块属性关键字（属性名包含任一关键字即复制）
	PropertyCopyWhitelist = ["facing", "axis", "rotation", "half", "hinge", "shape", "part", "face"]

[slay]
	affectPlayers = true       # 秒杀是否也针对玩家（永不针对使用者本人）
	stoneSlayRadius = 16       # 各法杖秒杀半径（曼哈顿距离）
	ironSlayRadius = 32
	diamondSlayRadius = 64
	infinitySlayRadius = 128
```

> 每根法杖的 `destruction` 项现在只作为「该等级是否启用破坏核心」的开关（`0` = 禁用）；
> 破坏核心可破坏的方块数与法杖的 `limit` 一致（与建筑、挖掘相同，和 1.12.2 版的单一上限一致）。

## 合成配方

| 物品 | 配方 |
|---|---|
| 挖掘核心 | 破坏核心 + 钻石镐/铲/锄/斧（环绕） |
| 仓储核心 | 玻璃板 + 末影珍珠 + 箱子 |
| 秒杀核心 | 破坏核心 + 下界之星×4 + 钻石剑×4 |
| ME 核心 | 玻璃板 + 下界石英 + 箱子（需 AE2） |
| 转化核心 | 玻璃板 + 荧石粉 + 绿宝石（需 ProjectE） |
| 防御升级 | 钻石×4 + 黑曜石×4 + 不死图腾 |
| 时运升级 | 钻石 + 青金石 |
| 自动冶炼升级 | 熔炉 + 烈焰粉 |
| 飞行升级 | 下界之星 + 鞘翅×2 + 羽毛×2 |

## 与 1.12.2 版的差异（等价重实现说明）

1. **底层架构**：未搬运 Plus 的 `WandOperation / MaterialSource / WandTransaction` 事务框架，而是把魔改功能实现在 1.20.1 上游的 `IWandAction / IWandSupplier / ISnapshot` 抽象上；预留-提交-退款语义由 `DropReclaim`（全有或全无回收）与 `FluidSnapshot/StoredFluid` 的退还逻辑承担。
2. **挖掘掉落**：1.20.1 没有 `Block.getDrops(fortune)`，改为用「带精准采集/时运附魔的合成工具」走原版战利品表（`Block.getDrops`），自动冶炼走 `RecipeType.SMELTING` 并按结果数量结算经验，与 1.12.2 的语义一致。
3. **饰品栏**：Baubles 无 1.20.1 版本，改用 **Curios**（`CuriosApi`）提供饰品栏供材（只取不存，退款走背包）。
4. **AE2**：按 AE2 15.x 的 `IInWorldGridNodeHost → IGridNode → IGrid.getStorageService().getInventory()` / `MEStorage.insert/extract`（`Actionable.SIMULATE/MODULATE`）重写；AE2 1.20.1 已移除安全终端，因此不再做权限校验。
5. **ProjectE**：按 ProjectE 1.20.1 的 `IKnowledgeProvider`（EMC 为 `BigInteger`）与 `IEMCProxy` 重写，掉落转 EMC 时自动解锁知识，并在写入后 `syncEmc`。
6. **Coremod 光照优化**：1.12.2 的 `LightingCorePlugin`（延迟合并 chunk 光照更新）未移植——它依赖 1.12.2 的 coremod 机制，1.20.1 无对应实现，且默认关闭。
7. **便携容器**（背包内潜影盒/收纳袋供材）：由上游 1.20.1 的 `ContainerManager`（`HandlerShulkerbox` / `HandlerBundle` / `HandlerCapability` / Botania）承担，功能保留。
8. **服务端规则同步**：`PacketServerRules` 在玩家登录和配置重载时下发每根法杖的上限/天使/破坏/耐久/可升级、TE 黑白名单、放置白/黑名单、属性复制白名单与相似方块分组；客户端用 `ServerRuleCache` 缓存，未收到时回退本地配置，因此联机时界面、预览与匹配与服务器一致。
9. **流体瞄准**：`FluidAwareTrace` 用含流体的射线重打一遍，使「流体处理=当作方块」时能直接瞄准流体面（上游 1.20.1 没有该能力）。
10. **种植**：未单独实现——1.20 的种子/树苗本身就是 `BlockItem`，上游的副手方块覆盖路径已能种植，无需 Plus 的 `IPlantable` 特殊分支。

## 构建

```bash
cd ConstructionWand-1.20
./gradlew build          # 产物：build/libs/constructionwand-1.20.1-2.12-plus.jar
./gradlew runClient      # 开发环境客户端
```

可选兼容依赖（仅在编译期使用，未安装对应 mod 时对应核心不会注册）：

- Curios `5.4.7+1.20.1`（`https://maven.theillusivec4.top/`）
- AE2 `15.2.16`（`https://modmaven.dev/`）
- ProjectE `1.0.1`（CurseMaven，1.20.1 上 PE 的版本号就是 `1.0.1`）

> 这三个兼容 mod 的名称与版本号**不遵循 Minecraft 版本号**（ProjectE 在 1.20.1 上是 1.0.1，Curios 是 5.4.7+1.20.1），
> 因此在 `mods.toml` 里**不声明**它们的 dependency 与 versionRange——否则版本范围不匹配会让游戏直接拒绝加载，
> 即使这些集成本身是可选的。兼容性完全由运行时的 `ModList.isLoaded(...)` 判断，未安装时对应核心不注册、功能自动退化。

已验证：Forge `47.4.23` + ProjectE `1.0.1` 的无头服务端可正常启动，`constructionwand:core_projecte` 物品与配方均正常注册
（15 条 constructionwand 配方全部加载，无解析错误、无依赖版本报错）。

## 兼容性

- 新增内容完全复用上游的 `IWandAction` / `IWandSupplier` / `ISnapshot` / `MaterialSource` 抽象，撤销路径与耐久消耗对新增操作同样成立；
- AE2 / ProjectE / Curios 均为软依赖，未加载时不注册对应核心，供材逻辑自动退化；
- 仓储核心已适配 Mekanism 流体储罐；破坏/挖掘/流体放回均能正确处理流动液体侵入。
