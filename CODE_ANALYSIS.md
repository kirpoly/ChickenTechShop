# ChickenTechShop Mod - Code Analysis Report

## Executive Summary
This analysis identifies design issues, performance problems, and configuration inconsistencies in the ChickenTechShop Starsector mod. The mod adds a special items trader (submarket) to the Prism Freeport station.

---

## Critical Issues

### 1. **Config Values Not Respected in Code**

#### Issue: Lucky Special Item Extra Picks Ignored
**Location:** `TechMarket.java#getSpecialItemPickCount()`
**Severity:** HIGH

The config values `luckySpecialExtraPicksLowLevel` and `luckySpecialExtraPicksHighLevel` are defined in both JSON and loaded into `CTS_Config`, but **never used** in the code.

```java
// Config defines these:
public int luckySpecialExtraPicksLowLevel = 1;
public int luckySpecialExtraPicksHighLevel = 2;

// But getSpecialItemPickCount() ignores them:
if (isLuckyCategory(LUCKY_CATEGORY_SPECIAL)) {
    picks = hardCap;  // Just sets to hardCap, ignores the extra picks config
}
```

**Expected behavior:** Lucky special restocks should add bonus picks based on tech level.

**Fix:** Implement the config values properly:
```java
if (isLuckyCategory(LUCKY_CATEGORY_SPECIAL)) {
    int bonus = techMarketLevel >= 4 ? cfg().luckySpecialExtraPicksHighLevel : cfg().luckySpecialExtraPicksLowLevel;
    picks = Math.min(poolSize, hardCap + bonus);
}
```

---

### 2. **Inconsistent Restock Logic Across Categories**

#### Issue: Different Quantity Randomization Approaches
**Location:** `TechMarket.java` - various methods
**Severity:** MEDIUM

The mod uses **three different approaches** for determining item quantities:

1. **Special Items** (`addSpecialTech`): Uses hardcoded probabilities (0.35f, 0.75f, 0.20f) mixed with config
2. **AI Cores** (`addAICores`): Uses `rollCoreQuantityAroundBase()` with `aiCoreRandomDelta` config
3. **Blueprints** (`addBlueprints`): Uses `applyBlueprintBonusPicks()` with level-based bonuses

**Problems:**
- No unified system for randomization
- Special items have hardcoded 0.35f probability at line 197 that should be configurable
- Inconsistent between lucky vs normal restocks

**Recommendation:** Create a unified `rollQuantityForCategory()` method that handles all categories consistently.

---

### 3. **Performance: Inefficient Item Filtering**

#### Issue: Multiple Full Iterations Over All Game Items
**Location:** `TechMarket.java#addSpecialTech()`, `addWeaponBlueprints()`, `addWingsBlueprints()`, `addShipsBlueprints()`, `addRandomModdedAICore()`
**Severity:** MEDIUM

Every restock (every 30 days) performs **5 separate full iterations** over all items in the game:

```java
// Line 160: Iterates ALL special items
for (SpecialItemSpecAPI spec : Global.getSettings().getAllSpecialItemSpecs()) { ... }

// Line 254: Iterates ALL weapon specs
for (WeaponSpecAPI spec : Global.getSettings().getAllWeaponSpecs()) { ... }

// Line 289: Iterates ALL fighter specs
for (FighterWingSpecAPI spec : Global.getSettings().getAllFighterWingSpecs()) { ... }

// Line 324: Iterates ALL ship hull specs
for (ShipHullSpecAPI spec : Global.getSettings().getAllShipHullSpecs()) { ... }

// Line 495: Iterates ALL commodity specs
for (CommoditySpecAPI spec : Global.getSettings().getAllCommoditySpecs()) { ... }
```

**Impact:** With large mod packs, this can cause noticeable lag. Each iteration could process hundreds or thousands of items.

**Fix:** Cache filtered item lists on first load:
```java
private static List<String> cachedRareBpWeapons = null;
private static List<String> cachedRareBpFighters = null;
// etc.

private void ensureCachesBuilt() {
    if (cachedRareBpWeapons == null) {
        // Build caches once
    }
}
```

---

### 4. **Hardcoded Values Not in Config**

#### Issue: Magic Numbers Throughout Code
**Location:** Multiple locations
**Severity:** MEDIUM

Several values are hardcoded that should be configurable:

1. **Line 109:** `sinceLastCargoUpdate < 30` - Restock interval hardcoded to 30 days
2. **Line 197:** `itemGenRandom.nextFloat() < 0.35f` - Special item quantity chance
3. **Line 450-452:** Level 4 blueprint bonus logic hardcoded
4. **Line 486:** `itemGenRandom.nextInt(5) + 1` - Lucky category count hardcoded to 5

**Recommendation:** Move these to config:
```json
"restock_interval_days": 30,
"special_item_qty2_chance_level4": 0.35,
"blueprint_bonus_min_level": 4
```

---

### 5. **Potential NullPointerException Risks**

#### Issue: Missing Null Checks
**Location:** Multiple locations
**Severity:** HIGH

Several methods don't check for null before accessing objects:

**Line 385-386:**
```java
RepLevel chicken_repLevel = Global.getSector().getImportantPeople()
    .getPerson(ChickenQuestUtils.PERSON_CHICKEN)
    .getRelToPlayer().getLevel();  // No null check!
```

If Chicken doesn't exist, this will crash.

**Line 47, 58, 98, 106 in missions:**
```java
TechMarket submarket = (TechMarket) market.getSubmarket("chicken_market").getPlugin();
submarket.addCreditsToTechMarket(creditReward);  // No null checks
```

**Fix:** Add defensive checks:
```java
PersonAPI chicken = Global.getSector().getImportantPeople().getPerson(ChickenQuestUtils.PERSON_CHICKEN);
if (chicken == null) {
    return cfg().tariffNeutral;  // Default fallback
}
RepLevel chicken_repLevel = chicken.getRelToPlayer().getLevel();
```

---

### 6. **Memory Leak Risk: Transient Field Not Reset**

#### Issue: `activeLuckyCategory` is Transient but Not Properly Managed
**Location:** `TechMarket.java#47`
**Severity:** LOW

```java
private transient int activeLuckyCategory = LUCKY_CATEGORY_NONE;
```

The `transient` keyword means this won't be saved, but it's set in `updateCargo()` and only reset at the end. If an exception occurs mid-update, it could remain in wrong state.

**Fix:** Use try-finally:
```java
public void updateCargo() {
    try {
        activeLuckyCategory = LUCKY_CATEGORY_NONE;
        if (luckyRestockCharges > 0) {
            activeLuckyCategory = queuedLuckyCategory;
            // ...
        }
        // ... rest of update logic
    } finally {
        activeLuckyCategory = LUCKY_CATEGORY_NONE;
    }
}
```

---

### 7. **Inefficient Cargo Clearing**

#### Issue: Unnecessary Copy and Iteration
**Location:** `TechMarket.java#134-138`
**Severity:** LOW

```java
for (CargoStackAPI s : cargo.getStacksCopy()) {
    float qty = s.getSize();
    cargo.removeItems(s.getType(), s.getData(), qty);
}
cargo.removeEmptyStacks();
```

**Problem:** `getStacksCopy()` creates a defensive copy, then we iterate and remove items one by one, then call `removeEmptyStacks()`.

**Better approach:**
```java
cargo.clear();  // If API supports it
// OR
List<CargoStackAPI> stacks = cargo.getStacksCopy();
for (CargoStackAPI s : stacks) {
    cargo.removeStack(s);  // Direct removal
}
```

---

### 8. **Incorrect String Comparison**

#### Issue: Using `==` Instead of `.equals()` for Strings
**Location:** `ChickenTechShop.java#65`
**Severity:** HIGH (Bug)

```java
if (m.getFactionId() == Factions.INDEPENDENT) {  // WRONG!
```

**Problem:** This compares object references, not string content. Should be:
```java
if (Factions.INDEPENDENT.equals(m.getFactionId())) {
```

This bug means Chicken might not spawn correctly at Independent markets.

---

### 9. **Redundant Config Reloads**

#### Issue: Config Reloaded Multiple Times
**Location:** `ChickenTechShop.java#91, 118`
**Severity:** LOW (Performance)

```java
public void onGameLoad(boolean newGame) {
    CTS_Config.reload();  // Reload 1
    // ...
}

public void onNewGameAfterTimePass() {
    CTS_Config.reload();  // Reload 2
    // ...
}
```

Config is reloaded on every game load. While not expensive, it's unnecessary since config is already cached.

**Fix:** Only reload when needed or on explicit user action.

---

### 10. **Duplicate Code in Mission Files**

#### Issue: Copy-Paste Code Across All Mission Types
**Location:** All `CTS_*Mission.java` files
**Severity:** MEDIUM (Maintainability)

All mission files have identical `notifyEnding()` implementations:
```java
protected void notifyEnding() {
    super.notifyEnding();
    MarketAPI market = ChickenQuestUtils.getChickenMarket();
    TechMarket submarket = (TechMarket) market.getSubmarket("chicken_market").getPlugin();
    submarket.addCreditsToTechMarket(creditReward);
    submarket.updateCargoForce();
}
```

**Fix:** Create a base class:
```java
public abstract class CTS_BaseMission extends [BaseClass] {
    @Override
    protected void notifyEnding() {
        super.notifyEnding();
        addCreditsToChickenMarket(creditReward);
    }
    
    protected void addCreditsToChickenMarket(int credits) {
        MarketAPI market = ChickenQuestUtils.getChickenMarket();
        if (market == null || !market.hasSubmarket("chicken_market")) {
            return;
        }
        TechMarket submarket = (TechMarket) market.getSubmarket("chicken_market").getPlugin();
        submarket.addCreditsToTechMarket(credits);
        submarket.updateCargoForce();
    }
}
```

---

### 11. **Questionable Design: Force Update After Every Mission**

#### Issue: `updateCargoForce()` Called After Every Mission Completion
**Location:** All mission `notifyEnding()` methods
**Severity:** MEDIUM (Design)

Every mission completion forces an immediate inventory refresh, bypassing the 30-day timer.

**Problems:**
- Inconsistent with normal restock behavior
- Could be exploited by players to farm restocks
- Not configurable

**Recommendation:** Either:
1. Remove forced updates (let normal timer handle it)
2. Add a cooldown/limit to forced updates
3. Make this behavior configurable

---

### 12. **Logging Left in Production Code**

#### Issue: Debug Logs Not Removed
**Location:** `TechMarket.java#200, 275, 276, 281, etc.`
**Severity:** LOW

```java
log.info("Trying to add " + itemID + " with quantity " + quantity);
// log.info("randomWeaponPicker has " + randomWeaponPicker.getItems().size());
// log.info("num picked for weapons is " + itemPickerNum);
```

Commented-out debug logs should be removed. Active `log.info()` calls should be `log.debug()` for performance.

---

### 13. **Inconsistent Null Handling in Config**

#### Issue: Some Config Values Have Null Checks, Others Don't
**Location:** `TechMarket.java#546-549`
**Severity:** LOW

```java
int[] configuredCosts = cfg().levelCosts;
if (configuredCosts == null || configuredCosts.length == 0) {
    configuredCosts = LEVEL_COSTS_BOOTSTRAP;
}
```

This is good defensive programming, but it's inconsistent - other config accesses don't check for null.

**Recommendation:** Either:
1. Ensure config always returns valid defaults (preferred)
2. Add null checks everywhere consistently

---

## Design Recommendations

### 1. **Unified Restock Strategy Pattern**
Create a `RestockStrategy` interface to handle different item categories uniformly:
```java
interface RestockStrategy {
    void restock(CargoAPI cargo, int techLevel, boolean isLucky, Random rng);
}
```

### 2. **Separate Configuration from Logic**
Move all hardcoded values to config files. Use a builder pattern for complex configurations.

### 3. **Add Restock Event System**
Instead of force-updating after missions, emit events that the market can listen to and decide when to restock.

### 4. **Cache Item Pools**
Build filtered item lists once on game load, not on every restock.

### 5. **Add Telemetry/Metrics**
Track restock frequency, item distribution, player spending to help balance the mod.

---

## Performance Optimization Summary

### Current Performance Issues:
1. **5 full iterations** over all game items every 30 days
2. **Defensive copies** of cargo stacks
3. **Repeated config access** via `cfg()` method calls
4. **String operations** in hot paths

### Recommended Optimizations:
1. Cache filtered item pools (estimated **80% reduction** in restock time)
2. Batch cargo operations
3. Cache config reference in local variable
4. Use StringBuilder for string concatenation in loops

---

## Configuration Coverage Analysis

### Config Values Properly Used: ✓
- `levelCosts`, `missionCreditContribution`, `spendingCreditContribution`
- `specialItemMaxTotal`, `blueprintPoolFraction`, `blueprintMax*`
- `ai*Base`, `aiCoreRandomDelta`, `moddedAiCore*`
- `tariff*`, `*PriceMult`
- `luckyRestockMinLevel`, `luckyPrice*`, `luckyCoreBonusPerUnlockedTier`, `luckyBpBonus*`

### Config Values NOT Used: ✗
- `luckySpecialExtraPicksLowLevel` - **NEVER USED**
- `luckySpecialExtraPicksHighLevel` - **NEVER USED**

### Missing Config Values (Should Be Added):
- `restockIntervalDays` (currently hardcoded to 30)
- `specialItemQty2ChanceLevel4` (currently hardcoded to 0.35f)
- `blueprintBonusMinLevel` (currently hardcoded to 4)
- `forceUpdateAfterMissions` (boolean, currently always true)

---

## Testing Recommendations

1. **Test with null Chicken** - Ensure graceful degradation
2. **Test with large mod packs** - Measure restock performance
3. **Test config changes mid-game** - Verify migration works
4. **Test lucky restock edge cases** - Multiple charges, level changes
5. **Test faction string comparison** - Verify Chicken spawns correctly

---

## Priority Fix List

### High Priority:
1. Fix `luckySpecialExtraPicksLowLevel/HighLevel` not being used
2. Fix string comparison bug in `ChickenTechShop.java#65`
3. Add null checks for Chicken person and submarket access
4. Cache item pools to fix performance

### Medium Priority:
5. Unify restock logic across categories
6. Move hardcoded values to config
7. Refactor duplicate mission code
8. Review force-update-after-mission design

### Low Priority:
9. Fix transient field management
10. Optimize cargo clearing
11. Remove debug logs
12. Reduce config reloads

---

## Conclusion

The mod is functional but has several design inconsistencies and performance issues. The most critical problems are:
1. **Config values not respected** (lucky special picks)
2. **String comparison bug** (faction check)
3. **Performance issues** (repeated full iterations)
4. **Missing null safety** (crash risks)

Addressing the high-priority issues will significantly improve stability and ensure config values work as intended.
