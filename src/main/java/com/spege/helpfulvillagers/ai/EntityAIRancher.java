package com.spege.helpfulvillagers.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.spege.helpfulvillagers.entity.EntityRancher;
import com.spege.helpfulvillagers.util.AIHelper;

import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.IShearable;

/**
 * Rancher gathering AI: leads wild animals into the ranch pasture, then breeds, shears and culls the
 * herd. 1.12.2 migration: ChunkCoordinates -> BlockPos; IShearable/leash APIs take BlockPos; valid
 * resources come from {@link EntityRancher#getValidAnimals()} (entities, not block coords).
 */
@SuppressWarnings({ "null", "deprecation" })
public class EntityAIRancher extends EntityAIWorker {
    private final ItemStack[] breedingItems = new ItemStack[] {
            new ItemStack(Items.GOLDEN_APPLE), new ItemStack(Items.GOLDEN_CARROT), new ItemStack(Items.WHEAT),
            new ItemStack(Items.CARROT), new ItemStack(Items.WHEAT_SEEDS), new ItemStack(Items.ROTTEN_FLESH),
            new ItemStack(Items.PORKCHOP), new ItemStack(Items.COOKED_PORKCHOP), new ItemStack(Items.BEEF),
            new ItemStack(Items.COOKED_BEEF), new ItemStack(Items.CHICKEN), new ItemStack(Items.COOKED_CHICKEN),
            new ItemStack(Items.FISH), new ItemStack(Items.COOKED_FISH) };
    private Random rand;
    private EntityRancher rancher;
    private int searchLimit;
    private EntityAnimal foundAnimal;
    private boolean pastureChecked;

    public EntityAIRancher(EntityRancher rancher) {
        super(rancher);
        this.rancher = rancher;
        this.target = null;
        this.rand = new Random();
        this.foundAnimal = null;
        this.searchLimit = 20;
        this.pastureChecked = false;
    }

    @Override
    protected boolean gather() {
        if (this.rancher.homeGuildHall == null) {
            return this.idle();
        }
        if (this.rancher.insideHall()) {
            BlockPos exit = this.rancher.homeGuildHall.entranceCoords;
            if (exit == null) {
                exit = AIHelper.getRandInsideCoords(this.rancher);
            }
            this.rancher.moveTo(exit, this.speed);
        } else if (!this.rancher.searching && this.rancher.getRanch() != null && this.rancher.getRanch().herd.size() > 0
                && this.rancher.getRanch().checkedAnimals.size() < this.rancher.getRanch().herd.size()) {
            for (EntityAnimal animal : this.rancher.getRanch().herd) {
                if (animal == null || animal.isDead || this.rancher.getRanch().checkedAnimals.contains(animal)) {
                    continue;
                }
                int distX = AIHelper.findDistance((int) this.rancher.posX, (int) animal.posX);
                int distY = AIHelper.findDistance((int) this.rancher.posY, (int) animal.posY);
                int distZ = AIHelper.findDistance((int) this.rancher.posZ, (int) animal.posZ);
                this.target = new BlockPos((int) animal.posX, (int) animal.posY, (int) animal.posZ);
                if (distX > 3 || distY > 3 || distZ > 3) {
                    this.rancher.moveTo(this.target, this.speed);
                    continue;
                }
                this.checkAnimal(animal);
            }
        } else if (this.foundAnimal == null) {
            this.rancher.searching = true;
            this.findAnimal();
        } else {
            int distX = AIHelper.findDistance((int) this.rancher.posX, (int) this.foundAnimal.posX);
            int distY = AIHelper.findDistance((int) this.rancher.posY, (int) this.foundAnimal.posY);
            int distZ = AIHelper.findDistance((int) this.rancher.posZ, (int) this.foundAnimal.posZ);
            this.target = null;
            if (distX > 3 || distY > 3 || distZ > 3) {
                this.rancher.moveTo(
                        new BlockPos((int) this.foundAnimal.posX, (int) this.foundAnimal.posY, (int) this.foundAnimal.posZ),
                        this.speed);
            } else {
                this.retrieveAnimal();
            }
        }
        return this.idle();
    }

    private void checkAnimal(EntityAnimal animal) {
        if (!animal.isChild()) {
            if (animal.getGrowingAge() == 0) {
                this.breedAnimal(animal);
            }
            if (animal instanceof IShearable) {
                this.shearAnimal(animal);
            }
            this.rancher.setPickupRadius(3);
            this.slaughterAnimal(animal);
            this.rancher.setPickupRadius(1);
        }
        this.rancher.getRanch().checkedAnimals.add(animal);
    }

    private void breedAnimal(EntityAnimal animal) {
        TileEntityChest chest = null;
        for (ItemStack i : this.breedingItems) {
            int index;
            if (!animal.isBreedingItem(i)) {
                continue;
            }
            if (this.rancher.inventory.containsItem(i) < 0) {
                for (int j = 0; j < this.rancher.getRanch().guildChests.size(); ++j) {
                    TileEntityChest c = this.rancher.getRanch().guildChests.get(j);
                    if (AIHelper.chestContains(c, i) < 0) {
                        continue;
                    }
                    chest = c;
                }
                if (chest == null) {
                    chest = this.rancher.homeVillage.searchHallsForItem(i);
                }
                if (chest != null) {
                    AIHelper.takeItemFromChest(new ItemStack(i.getItem(), i.getMaxStackSize()), chest, this.rancher, false);
                }
            }
            if ((index = this.rancher.inventory.containsItem(i)) >= 0) {
                this.rancher.inventory.decrementSlot(index);
                animal.setInLove(null);
            }
            return;
        }
    }

    private void shearAnimal(EntityAnimal animal) {
        int index;
        IShearable shearable = (IShearable) animal;
        BlockPos animalPos = new BlockPos(animal.posX, animal.posY, animal.posZ);
        if (!shearable.isShearable(new ItemStack(Items.SHEARS), animal.world, animalPos)) {
            return;
        }
        TileEntityChest chest = null;
        ItemStack shears = new ItemStack(Items.SHEARS);
        if (this.rancher.inventory.containsItem(shears) < 0) {
            for (int j = 0; j < this.rancher.getRanch().guildChests.size(); ++j) {
                TileEntityChest c = this.rancher.getRanch().guildChests.get(j);
                if (AIHelper.chestContains(c, shears) < 0) {
                    continue;
                }
                chest = c;
            }
            if (chest == null) {
                chest = this.rancher.homeVillage.searchHallsForItem(shears);
            }
            if (chest != null) {
                AIHelper.takeItemFromChest(new ItemStack(shears.getItem(), shears.getMaxStackSize()), chest, this.rancher, false);
            }
        }
        if ((index = this.rancher.inventory.containsItem(shears)) >= 0) {
            shears = this.rancher.inventory.getStackInSlot(index);
            shears.damageItem(1, this.rancher);
            this.rancher.inventory.setInventorySlotContents(index, shears);
            List<ItemStack> drops = shearable.onSheared(new ItemStack(Items.SHEARS), animal.world, animalPos, 3);
            for (ItemStack item : drops) {
                this.rancher.inventory.addItem(item);
            }
        }
    }

    private void slaughterAnimal(EntityAnimal animal) {
        int count = 0;
        for (EntityAnimal otherAnimal : this.rancher.getRanch().herd) {
            if (animal.getClass() != otherAnimal.getClass() || ++count < 2 || animal.isInLove() && count < 4) {
                continue;
            }
            animal.attackEntityFrom(DamageSource.causeMobDamage(this.rancher), 20.0f);
            break;
        }
    }

    private void findAnimal() {
        if (this.target == null) {
            this.target = AIHelper.getRandOutsideCoords(this.rancher, this.searchLimit);
        }
        if (this.target != null) {
            this.rancher.moveTo(this.target, this.speed);
        }
        if (!AIHelper.isInRangeOfAnyVillage(this.rancher.posX, this.rancher.posY, this.rancher.posZ)) {
            this.rancher.updateBoxes();
            if (this.rancher.searchBox != null) {
                this.foundAnimal = this.getNewResource();
                if (this.foundAnimal != null) {
                    this.rancher.getNavigator().clearPath();
                }
            }
        }
        // Defensive null guard (1.7.10 dereferenced target unconditionally here and could NPE).
        if (this.target != null && Math.abs(this.rancher.posX - (double) this.target.getX()) <= 5.0
                && Math.abs(this.rancher.posZ - (double) this.target.getZ()) <= 5.0) {
            this.target = null;
        }
    }

    private void retrieveAnimal() {
        if (this.foundAnimal.getLeashed()) {
            if (this.foundAnimal.getLeashHolder().getEntityId() != this.rancher.getEntityId()) {
                this.foundAnimal.setLeashHolder(this.rancher, true);
            }
        } else {
            this.foundAnimal.setLeashHolder(this.rancher, true);
        }
        if (!this.pastureChecked) {
            this.rancher.getRanch().checkPasture();
            this.pastureChecked = true;
        }
        boolean hasPasture = false;
        if (this.target == null) {
            if (this.rancher.getRanch().pastureCoords.size() > 0) {
                int index = this.rand.nextInt(this.rancher.getRanch().pastureCoords.size() - 1);
                this.target = this.rancher.getRanch().pastureCoords.get(index);
                hasPasture = true;
            } else {
                int index = this.rand.nextInt(this.rancher.homeGuildHall.insideCoords.size() - 1);
                this.target = this.rancher.homeGuildHall.insideCoords.get(index);
            }
        }
        if (this.target != null) {
            this.rancher.moveTo(this.target, this.speed);
        }
        if (hasPasture) {
            if (this.rancher.nearPasture()) {
                this.storeAnimal();
            }
        } else if (this.rancher.nearHall()) {
            this.storeAnimal();
        }
    }

    private void storeAnimal() {
        this.foundAnimal.clearLeashed(true, false);
        this.foundAnimal.setLocationAndAngles((double) this.target.getX(), (double) this.target.getY(), (double) this.target.getZ(),
                this.foundAnimal.rotationPitch, this.foundAnimal.rotationYaw);
        this.foundAnimal.setHomePosAndDistance(new BlockPos(this.target.getX(), this.target.getY(), this.target.getZ()), 1);
        this.rancher.getRanch().herd.add(this.foundAnimal);
        this.foundAnimal = null;
        this.rancher.searching = false;
    }

    private EntityAnimal getNewResource() {
        ArrayList<EntityAnimal> animals = this.rancher.getValidAnimals();
        double closestDist = Double.MAX_VALUE;
        EntityAnimal closestAnimal = null;
        for (int i = 0; i < animals.size(); ++i) {
            EntityAnimal animal = animals.get(i);
            double dist = Math.sqrt(this.rancher.getDistanceSq((int) animal.posX, (int) animal.posY, (int) animal.posZ));
            if (this.rancher.getRanch().herd.contains(animal) || !(dist < closestDist)) {
                continue;
            }
            closestAnimal = animal;
            closestDist = dist;
        }
        return closestAnimal;
    }
}
