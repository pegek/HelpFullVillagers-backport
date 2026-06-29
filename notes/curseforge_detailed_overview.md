# Helpful Villagers (1.12.2)

Helpful Villagers completely revitalizes your Minecraft villages, turning them into fully autonomous, working communities! Originally created by WeaselNinja for 1.7.10, this mod has been faithfully backported and massively expanded. Villagers are no longer idle traders; they gather resources, construct buildings, fight monsters, and drive a dynamic local economy.

## 🛠️ 11 Unique Professions
Villagers can be assigned to specialized roles, each driven by intricate custom AI:
- **Regular:** General citizens who can be trained into other professions.
- **Lumberjack:** Seeks out trees, chops them down, and automatically replants saplings.
- **Miner:** Digs intelligent mineshafts and extracts valuable ores.
- **Farmer:** Tills soil, plants seeds, and harvests mature crops.
- **Fisherman:** Catches fish using a fully custom fishing hook and bobber mechanic.
- **Rancher:** Breeds and herds animals, gathering drops.
- **Builder:** Constructs new structures or demolishes old ones utilizing a unique construction fence boundary system.
- **Merchant:** Manages player bank accounts and handles bartering.
- **Soldier:** Melee defenders utilizing a redesigned combat system. They use shields to block attacks and intelligently ignore creepers to prevent village damage.
- **Archer:** Ranged defenders with proper bow-drawing animations and smart kiting mechanics.
- **Cleric *(New!)*:** The ultimate support unit. Clerics use brewing stands and enchanting tables to aid the village dynamically.

## ⚔️ Advanced Combat & Support AI
- **Smart Guard Resupply:** Guards will not fight to the death blindly. If their health drops below 50%, or if they lack a weapon, armor, or arrows, they will retreat to their Guild Hall. There, they deposit their gathered loot and intelligently equip gear directly from the guild chests (even knowing which armor slots to fill).
- **Cleric Mechanics:** Clerics convert mob drops into "essence". They use this essence to:
  - **Heal:** Throw Instant Health & Regen splash potions at villagers below 40% health (Costs 2 essence).
  - **Cleanse:** Strip negative potion effects from villagers, accompanied by happy particles (Costs 3 essence).
  - **Bless:** Once the village guards achieve kill milestones (15, 50, 100 kills), the Cleric will fully repair and enchant the guards' weapons and armor up to power level 30!

## 🏘️ Village Infrastructure & Economy
- **Guild Halls:** Assign a profession to a building by placing an item frame next to a door with the respective tool (e.g., an Axe for Lumberjacks, an Experience Bottle for Clerics). The mod uses a sophisticated 6-neighbor flood-fill algorithm to scan the room's interior, automatically linking all chests, furnaces, and workbenches to that guild for the villagers to use.
- **Dynamic Economy:** The village features a fully dynamic, localized economy. Upon initialization, the mod scans the village bounds to estimate local resource abundance. It then cross-references this with crafting recipes to dynamically generate fair prices for every item, which fluctuate based on supply and demand. Players can even deposit and withdraw money from their personal village bank accounts!

## ⚙️ Quality of Life Improvements
- **Anti-Stuck Watchdog:** Villagers track their positions; if they are stuck in the same small area for too long, they will safely teleport back to the village center.
- **Smooth Pathfinding:** Villagers intelligently navigate through doors, avoid hazardous terrain, and seek shelter efficiently.
- **Multiplayer Ready:** Fully optimized and synchronized for 1.12.2 dedicated servers.
