# Helpful Villagers — 1.12.2 Forward-Port

A Minecraft **Forge 1.12.2** forward-port of **WeaselNinja's "Helpful Villagers"** mod
(originally for 1.7.10), made with the original author's permission and extended with new
content. Put those lazy villagers to work: vanilla villagers are replaced with recruitable
profession villagers that gather resources, fight, craft, fish, ranch and trade.

> Original mod © WeaselNinja (MIT). 1.12.2 port and additions by Isuth. See [LICENSE](LICENSE).

## Professions

Regular, Lumberjack, Miner, Farmer, Soldier, Archer, Merchant, Fisherman, Rancher,
**Builder** (ported from upstream 1.4.0b5) and **Cleric** (new in this port).

Each village forms around guild halls (an item frame holding a profession marker next to a
door); villagers gather materials, return to store and craft, and defend the settlement.

## What this port adds beyond the original

- **Reworked guard AI (Soldier & Archer)** — vanilla-style target/attack task split: weapon-based
  damage with cooldowns, bow draw + strafing/kiting, creeper handling, village-door patrol routes,
  and a guild-hall resupply loop (tools, armour, arrows).
- **Shields** for soldiers (offhand slot, blocking, sourced from chests or the craft queue).
- **Cleric profession** — heals (splash potions) and cleanses villagers using *essence* brewed
  from mob drops at a brewing stand, shadows the guards, and enchants + repairs their gear at
  kill-count milestones.
- **General villager AI hardening** — door pathing fixes and an anti-stuck watchdog for all
  professions.

## Building

Requires **JDK 8** (ForgeGradle 3 / Gradle 4.9 do not run on newer JDKs).

```bash
./gradlew build        # Linux/macOS
.\gradlew.bat build    # Windows
```

The reobfuscated mod jar lands in `build/libs/`. Forge `1.12.2-14.23.5.2860`,
MCP mappings `snapshot 20171003-1.12`.

## Repository layout

```
src/main/java/com/spege/helpfulvillagers/   the 1.12.2 mod source (entity, ai, village, econ,
                                            crafting, inventory, network, gui, renderer, util)
src/main/resources/                          assets, lang, mcmod.info
docs/superpowers/                            design specs and implementation plans
notes/                                       development log (notes/CLAUDE.md) and tooling
```

## Credits

- **WeaselNinja** — original "Helpful Villagers" mod (MIT).
- **Isuth** — 1.12.2 forward-port and new content.
