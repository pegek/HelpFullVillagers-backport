package com.spege.helpfulvillagers.gui;

import com.spege.helpfulvillagers.crafting.CraftItem;
import com.spege.helpfulvillagers.entity.AbstractVillager;
import com.spege.helpfulvillagers.main.HelpfulVillagers;
import com.spege.helpfulvillagers.network.CraftQueueServerPacket;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import java.io.IOException;
import java.util.List;

/**
 * Craft statistics / economy screen. Shows three scrollable rows:
 * village craft queue, materials needed, and materials collected.
 */
@SuppressWarnings({ "null", "deprecation" })
public class GuiCraftStats extends GuiContainer {
    private static final int MAX_SLOTS = 9;
    public final int xSizeOfTexture = 195;
    public final int ySizeOfTexture = 136;
    private final EntityPlayer player;
    private static AbstractVillager villager;
    private static InventoryBasic craftQueueInv = new InventoryBasic("Craft Queue", true, 9);
    private static InventoryBasic materialsNeededInv = new InventoryBasic("Materials Needed", true, 9);
    private static InventoryBasic materialsCollectedInv = new InventoryBasic("Materials Collected", true, 9);
    private static int index1;
    private static int index2;
    private static int index3;
    private ScrollButton leftButton1;
    private ScrollButton leftButton2;
    private ScrollButton leftButton3;
    private ScrollButton rightButton1;
    private ScrollButton rightButton2;
    private ScrollButton rightButton3;
    private GuiButton backButton;
    private GuiButton removeButton;
    private static int selectedCraftIndex;
    private static CraftItem selectedCraftItem;

    public GuiCraftStats(EntityPlayer player, AbstractVillager villager) {
        super(new StatsContainer());
        this.player = player;
        GuiCraftStats.villager = villager;
        index1 = 0;
        index2 = 0;
        index3 = 0;
        selectedCraftIndex = -1;
    }

    @Override
    public void initGui() {
        super.initGui();
        int posX = (this.width - 195) / 2;
        int posY = (this.height - 136) / 2;

        this.leftButton1 = new ScrollButton(0, posX + 4, posY + 22, true);
        this.rightButton1 = new ScrollButton(1, posX + 180, posY + 22, false);
        this.buttonList.add(this.leftButton1);
        this.buttonList.add(this.rightButton1);
        this.leftButton1.enabled = false;
        this.rightButton1.enabled = false;

        this.leftButton2 = new ScrollButton(2, posX + 4, posY + 58, true);
        this.rightButton2 = new ScrollButton(3, posX + 180, posY + 58, false);
        this.buttonList.add(this.leftButton2);
        this.buttonList.add(this.rightButton2);
        this.leftButton2.enabled = false;
        this.rightButton2.enabled = false;

        this.leftButton3 = new ScrollButton(4, posX + 4, posY + 93, true);
        this.rightButton3 = new ScrollButton(5, posX + 180, posY + 93, false);
        this.buttonList.add(this.leftButton3);
        this.buttonList.add(this.rightButton3);
        this.leftButton3.enabled = false;
        this.rightButton3.enabled = false;

        this.backButton = new GuiButton(6, posX + 20, posY + 112, 60, 20, "Back");
        this.buttonList.add(this.backButton);

        this.removeButton = new GuiButton(7, posX + 120, posY + 112, 60, 20, "Remove");
        this.buttonList.add(this.removeButton);
        this.removeButton.enabled = false;
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        this.drawDefaultBackground();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        this.mc.getTextureManager().bindTexture(new ResourceLocation("helpfulvillagers", "textures/gui/craft_details.png"));
        int posX = (this.width - 195) / 2;
        int posY = (this.height - 136) / 2;
        this.drawTexturedModalRect(posX, posY, 0, 0, 195, 136);

        this.drawString(this.fontRenderer, "Village Craft Queue:", posX + 18, posY + 10, 0xFFFFFF);
        this.drawString(this.fontRenderer, "Materials Needed:", posX + 18, posY + 45, 0xFFFFFF);
        this.drawString(this.fontRenderer, "Materials Collected:", posX + 18, posY + 80, 0xFFFFFF);

        // Selection highlight
        this.mc.getTextureManager().bindTexture(new ResourceLocation("helpfulvillagers", "textures/gui/craft_details.png"));
        int indexDiff = selectedCraftIndex - index1;
        if (indexDiff >= 0 && indexDiff <= 8) {
            this.drawTexturedModalRect(indexDiff * 18 + posX + 16, posY + 17, 232, 0, 24, 22);
        }
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        this.leftButton1.enabled = index1 > 0;
        this.rightButton1.enabled = index1 < GuiCraftStats.villager.homeVillage.craftQueue.getSize() - 9;
        this.leftButton2.enabled = index2 > 0;
        this.rightButton2.enabled = index2 < GuiCraftStats.villager.materialsNeeded.size() - 9;
        this.leftButton3.enabled = index3 > 0;
        this.rightButton3.enabled = index3 < GuiCraftStats.villager.inventory.materialsCollected.size() - 9;
        int indexDiff = selectedCraftIndex - index1;
        this.removeButton.enabled = selectedCraftItem != null
                && (selectedCraftItem.getPriority() <= 0 || selectedCraftItem.getName().equals(this.player.getName()))
                && indexDiff >= 0 && indexDiff <= 8;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        StatsContainer.scrollTo(index1, index2, index3);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void renderHoveredToolTip(int mouseX, int mouseY) {
        Slot slot = this.getSlotUnderMouse();
        if (slot != null && slot.getHasStack()) {
            CraftItem craftItem = this.getCraftItemAt(slot.getStack(), mouseX, mouseY);
            if (craftItem != null) {
                List<String> list = craftItem.getTooltip();
                this.drawHoveringText(list, mouseX, mouseY);
                return;
            }
        }
        super.renderHoveredToolTip(mouseX, mouseY);
    }

    private CraftItem getCraftItemAt(ItemStack item, int x, int y) {
        int posX = (this.width - 195) / 2;
        int posY = (this.height - 136) / 2;
        if (y > posY + 40) {
            return null;
        }
        int n = x - posX;
        n = (n + 4) / 5 * 5;
        n /= 17;
        if (--n > index1 + 9) {
            n = index1 + 9;
        }
        try {
            CraftItem craftItem = GuiCraftStats.villager.homeVillage.craftQueue.getItemStackAt(index1 + n);
            if (craftItem.getItem().getDisplayName().equals(item.getDisplayName())) {
                return craftItem;
            }
            craftItem = GuiCraftStats.villager.homeVillage.craftQueue.getItemStackAt(index1 + n - 1);
            return craftItem;
        } catch (NullPointerException e) {
            return null;
        }
    }

    public static void setSelectedCraftItem(int slot) {
        if (slot >= 0 && slot <= 8) {
            selectedCraftIndex = index1 + slot;
            selectedCraftItem = selectedCraftIndex >= 0
                    && selectedCraftIndex < GuiCraftStats.villager.homeVillage.craftQueue.getSize()
                    ? GuiCraftStats.villager.homeVillage.craftQueue.getItemStackAt(selectedCraftIndex) : null;
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case 0:
                if (--index1 < 0) index1 = 0;
                break;
            case 1:
                if (++index1 > GuiCraftStats.villager.homeVillage.craftQueue.getSize())
                    index1 = GuiCraftStats.villager.homeVillage.craftQueue.getSize();
                break;
            case 2:
                if (--index2 < 0) index2 = 0;
                break;
            case 3:
                if (++index2 > GuiCraftStats.villager.materialsNeeded.size())
                    index2 = GuiCraftStats.villager.materialsNeeded.size();
                break;
            case 4:
                if (--index3 < 0) index3 = 0;
                break;
            case 5:
                if (++index3 > GuiCraftStats.villager.inventory.materialsCollected.size())
                    index3 = GuiCraftStats.villager.inventory.materialsCollected.size();
                break;
            case 6: // Back
                this.player.openGui(HelpfulVillagers.instance, 4, GuiCraftStats.villager.world,
                        villager.getEntityId(), 0, 0);
                break;
            case 7: // Remove
                GuiCraftStats.villager.homeVillage.craftQueue.removeItemStackAt(selectedCraftIndex);
                HelpfulVillagers.network.sendToServer(new CraftQueueServerPacket(
                        villager.getEntityId(), GuiCraftStats.villager.homeVillage.craftQueue.getAll()));
                break;
        }
    }

    // ----- Inner: ScrollButton -----

    private static class ScrollButton extends GuiButton {
        private final boolean left;

        public ScrollButton(int id, int x, int y, boolean flip) {
            super(id, x, y, 12, 19, "");
            this.left = flip;
        }

        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
            if (this.visible) {
                mc.getTextureManager().bindTexture(new ResourceLocation("helpfulvillagers", "textures/gui/craft_details.png"));
                GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
                boolean hovered = mouseX >= this.x && mouseY >= this.y
                        && mouseX < this.x + this.width && mouseY < this.y + this.height;
                int u = 195;
                int v = 0;
                if (this.left) {
                    v += this.height;
                }
                if (!this.enabled) {
                    u += this.width * 2;
                } else if (hovered) {
                    u += this.width;
                }
                this.drawTexturedModalRect(this.x, this.y, u, v, this.width, this.height);
            }
        }
    }

    // ----- Inner: StatsContainer -----

    private static class StatsContainer extends Container {
        public StatsContainer() {
            for (int i = 0; i < 9; ++i) {
                this.addSlotToContainer(new Slot(craftQueueInv, i, i * 18 + 9, 36));
            }
            for (int i = 0; i < 9; ++i) {
                this.addSlotToContainer(new Slot(materialsNeededInv, i, i * 18 + 9, 72));
            }
            for (int i = 0; i < 9; ++i) {
                this.addSlotToContainer(new Slot(materialsCollectedInv, i, i * 18 + 9, 108));
            }
        }

        public static void scrollTo(int idx1, int idx2, int idx3) {
            for (int i = 0; i < 9; ++i) {
                if (i + idx1 >= villager.homeVillage.craftQueue.getSize()) {
                    craftQueueInv.setInventorySlotContents(i, ItemStack.EMPTY);
                } else {
                    craftQueueInv.setInventorySlotContents(i,
                            villager.homeVillage.craftQueue.getItemStackAt(i + idx1).getItem());
                }
                if (i + idx2 >= villager.materialsNeeded.size()) {
                    materialsNeededInv.setInventorySlotContents(i, ItemStack.EMPTY);
                } else {
                    materialsNeededInv.setInventorySlotContents(i, villager.materialsNeeded.get(i + idx2));
                }
                if (i + idx3 >= villager.inventory.materialsCollected.size()) {
                    materialsCollectedInv.setInventorySlotContents(i, ItemStack.EMPTY);
                } else {
                    materialsCollectedInv.setInventorySlotContents(i,
                            villager.inventory.materialsCollected.get(i + idx3));
                }
            }
        }

        @Override
        public boolean canInteractWith(EntityPlayer player) {
            return true;
        }

        @Override
        public ItemStack slotClick(int slotId, int dragType, ClickType clickTypeIn, EntityPlayer player) {
            try {
                Slot slot = this.getSlot(slotId);
                if (slot.inventory.getName().equals("Craft Queue")) {
                    GuiCraftStats.setSelectedCraftItem(slotId);
                }
                return ItemStack.EMPTY;
            } catch (Exception e) {
                return ItemStack.EMPTY;
            }
        }

        @Override
        public ItemStack transferStackInSlot(EntityPlayer player, int index) {
            return ItemStack.EMPTY;
        }
    }
}
