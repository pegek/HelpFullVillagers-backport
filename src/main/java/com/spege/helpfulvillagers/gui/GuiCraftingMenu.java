package com.spege.helpfulvillagers.gui;

import com.spege.helpfulvillagers.crafting.CraftItem;
import com.spege.helpfulvillagers.crafting.VillagerRecipe;
import com.spege.helpfulvillagers.entity.AbstractVillager;
import com.spege.helpfulvillagers.main.HelpfulVillagers;
import com.spege.helpfulvillagers.network.CraftItemServerPacket;
import com.spege.helpfulvillagers.network.GUICommandPacket;

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

import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Crafting job queue menu. Displays a scrollable 3×9 grid of craftable recipes,
 * a selected-item slot, a current-craft slot, and amount +/- buttons.
 */
@SuppressWarnings({ "null", "deprecation" })
public class GuiCraftingMenu extends GuiContainer {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation("helpfulvillagers", "textures/gui/craft_menu.png");

    public final int xSizeOfTexture = 195;
    public final int ySizeOfTexture = 136;
    public static final int MAX_ROWS = 3;
    public static final int MAX_COLS = 9;
    private final EntityPlayer player;
    private static AbstractVillager villager;
    private static InventoryBasic selectedItemInv = new InventoryBasic("Selected Item", true, 1);
    private static InventoryBasic currentCraftInv = new InventoryBasic("Current Craft", true, 1);
    private static InventoryBasic recipesInv = new InventoryBasic("Recipes", true, 27);
    private AmountButton addButton;
    private AmountButton subButton;
    private static AmountButton trigger;
    private GuiButton addCraftButton;
    private TeachButton teachButton;
    private StatsButton statsButton;
    @SuppressWarnings("rawtypes")
    private static List itemList;
    private float currentScroll;
    private boolean wasClicking;
    private boolean isScrolling;
    private static int lowestStackSize;

    @SuppressWarnings("rawtypes")
    public GuiCraftingMenu(EntityPlayer player, AbstractVillager villager) {
        super(new CraftingContainer());
        this.player = player;
        GuiCraftingMenu.villager = villager;
        this.currentScroll = 0.0f;
        this.initItemList();
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void initItemList() {
        itemList = new ArrayList();
        for (VillagerRecipe i : GuiCraftingMenu.villager.knownRecipes) {
            itemList.add(i.getOutput());
        }
    }

    @Override
    public void initGui() {
        super.initGui();
        int posX = (this.width - 195) / 2;
        int posY = (this.height - 136) / 2;

        this.addButton = new AmountButton(0, posX + 58, posY + 70, true);
        this.subButton = new AmountButton(1, posX + 58, posY + 102, false);
        this.buttonList.add(this.addButton);
        this.buttonList.add(this.subButton);
        this.addButton.enabled = false;
        this.subButton.enabled = false;

        this.addCraftButton = new GuiButton(3, posX + 83, posY + 110, 60, 20, "");
        this.buttonList.add(this.addCraftButton);
        this.addCraftButton.enabled = false;

        this.teachButton = new TeachButton(4, posX + 173, posY + 90);
        this.buttonList.add(this.teachButton);

        this.statsButton = new StatsButton(5, posX + 172, posY + 110);
        this.buttonList.add(this.statsButton);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        ItemStack selected = selectedItemInv.getStackInSlot(0);
        this.addButton.enabled = !selected.isEmpty() && selected.getCount() < selected.getMaxStackSize();
        this.subButton.enabled = !selected.isEmpty() && selected.getCount() > lowestStackSize;
        this.addCraftButton.enabled = !selected.isEmpty();
        this.addCraftButton.displayString = GuiCraftingMenu.villager.currentCraftItem == null
                ? "Craft Item" : "Queue Item";
        this.triggerButton();
    }

    private void triggerButton() {
        if (trigger != null) {
            try {
                this.actionPerformed(trigger);
            } catch (IOException e) {
                // ignore
            }
            trigger = null;
        }
    }

    @SuppressWarnings("rawtypes")
    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        boolean flag = Mouse.isButtonDown(0);
        int k = (this.width - 195) / 2;
        int l = (this.height - 136) / 2;
        int i1 = k + 174;
        int j1 = l + 8;
        int k1 = i1 + 14;
        int l1 = j1 + 52;

        if (!this.wasClicking && flag && mouseX >= i1 && mouseY >= j1 && mouseX < k1 && mouseY < l1) {
            this.isScrolling = this.needsScrollBars();
        }
        if (!flag) {
            this.isScrolling = false;
        }
        this.wasClicking = flag;
        if (this.isScrolling) {
            this.currentScroll = ((float) (mouseY - j1) - 7.5f) / ((float) (l1 - j1) - 15.0f);
            if (this.currentScroll < 0.0f) this.currentScroll = 0.0f;
            if (this.currentScroll > 1.0f) this.currentScroll = 1.0f;
        }
        CraftingContainer.scrollTo(this.currentScroll);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private boolean needsScrollBars() {
        return itemList.size() > 27;
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        this.drawDefaultBackground();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        this.mc.getTextureManager().bindTexture(TEXTURE);
        int posX = (this.width - 195) / 2;
        int posY = (this.height - 136) / 2;
        this.drawTexturedModalRect(posX, posY, 0, 0, 195, 136);
        // Scrollbar knob
        this.drawTexturedModalRect(posX + 174,
                (int) ((float) posY + 37.0f * this.currentScroll + 8.0f),
                this.needsScrollBars() ? 0 : 12, 241, 12, 15);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int i = Mouse.getEventDWheel();
        if (i != 0 && this.needsScrollBars()) {
            int j = itemList.size() / 9 - 5 + 1;
            if (i > 0) i = 1;
            if (i < 0) i = -1;
            this.currentScroll = (float) ((double) this.currentScroll - (double) i / (double) j);
            if (this.currentScroll < 0.0f) this.currentScroll = 0.0f;
            if (this.currentScroll > 1.0f) this.currentScroll = 1.0f;
        }
    }

    @Override
    protected void renderHoveredToolTip(int mouseX, int mouseY) {
        Slot slot = this.getSlotUnderMouse();
        if (slot != null && slot.getHasStack()) {
            ItemStack item = slot.getStack();
            VillagerRecipe recipe = villager.getRecipe(item);
            if (recipe != null) {
                List<String> list = recipe.getTooltip();
                this.drawHoveringText(list, mouseX, mouseY);
                return;
            }
        }
        super.renderHoveredToolTip(mouseX, mouseY);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case 0: { // Add amount
                ItemStack item = selectedItemInv.getStackInSlot(0);
                int newCount = item.getCount() + lowestStackSize;
                if (newCount > item.getMaxStackSize()) newCount = item.getMaxStackSize();
                item.setCount(newCount);
                selectedItemInv.setInventorySlotContents(0, item);
                break;
            }
            case 1: { // Sub amount
                ItemStack item = selectedItemInv.getStackInSlot(0);
                int newCount = item.getCount() - lowestStackSize;
                if (newCount < lowestStackSize) newCount = lowestStackSize;
                item.setCount(newCount);
                selectedItemInv.setInventorySlotContents(0, item);
                break;
            }
            case 3: { // Craft / Queue
                ItemStack item = selectedItemInv.getStackInSlot(0).copy();
                CraftItem craftItem = new CraftItem(item, this.player);
                if (GuiCraftingMenu.villager.currentCraftItem == null) {
                    GuiCraftingMenu.villager.currentCraftItem = craftItem;
                    HelpfulVillagers.network.sendToServer(
                            new CraftItemServerPacket(villager.getEntityId(), craftItem, true));
                } else {
                    HelpfulVillagers.network.sendToServer(
                            new CraftItemServerPacket(villager.getEntityId(), craftItem, false));
                }
                selectedItemInv.setInventorySlotContents(0, ItemStack.EMPTY);
                break;
            }
            case 4: { // Teach
                this.mc.player.closeScreen();
                if (GuiCraftingMenu.villager.world.isRemote) {
                    GuiCraftingMenu.villager.guiCommand = 7;
                    HelpfulVillagers.network.sendToServer(
                            new GUICommandPacket(villager.getEntityId(), 7));
                }
                break;
            }
            case 5: { // Stats
                this.player.openGui(HelpfulVillagers.instance, 5, GuiCraftingMenu.villager.world,
                        villager.getEntityId(), 0, 0);
                break;
            }
        }
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        selectedItemInv.setInventorySlotContents(0, ItemStack.EMPTY);
    }

    // ----- Inner: StatsButton -----

    private static class StatsButton extends GuiButton {
        public StatsButton(int id, int x, int y) {
            super(id, x, y, 16, 15, "");
        }

        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
            mc.getTextureManager().bindTexture(TEXTURE);
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
            boolean hovered = mouseX >= this.x && mouseY >= this.y
                    && mouseX < this.x + this.width && mouseY < this.y + this.height;
            int u, v;
            if (hovered) {
                this.width = 18;
                this.height = 17;
                u = 17;
                v = 189;
            } else {
                this.width = 16;
                this.height = 15;
                u = 18;
                v = 190;
            }
            this.drawTexturedModalRect(this.x, this.y, u, v, this.width, this.height);
        }
    }

    // ----- Inner: TeachButton -----

    private static class TeachButton extends GuiButton {
        public TeachButton(int id, int x, int y) {
            super(id, x, y, 14, 16, "");
        }

        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
            mc.getTextureManager().bindTexture(TEXTURE);
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
            boolean hovered = mouseX >= this.x && mouseY >= this.y
                    && mouseX < this.x + this.width && mouseY < this.y + this.height;
            int u, v;
            if (hovered) {
                this.width = 16;
                this.height = 18;
                u = 0;
                v = 188;
            } else {
                this.width = 14;
                this.height = 16;
                u = 1;
                v = 189;
            }
            this.drawTexturedModalRect(this.x, this.y, u, v, this.width, this.height);
        }
    }

    // ----- Inner: AmountButton -----

    private static class AmountButton extends GuiButton {
        private static final int MAX_PRESS = 10;
        private final boolean up;
        private boolean beingPressed;
        private int pressCount;

        public AmountButton(int id, int x, int y, boolean flip) {
            super(id, x, y, 19, 12, "");
            this.up = flip;
            this.beingPressed = false;
            this.pressCount = 0;
        }

        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
            if (this.visible) {
                mc.getTextureManager().bindTexture(TEXTURE);
                GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
                boolean hovered = mouseX >= this.x && mouseY >= this.y
                        && mouseX < this.x + this.width && mouseY < this.y + this.height;
                int u = 0;
                int v = 206;
                if (!this.up) {
                    u += this.width;
                }
                if (!this.enabled) {
                    v += this.height * 2;
                    this.beingPressed = false;
                    this.pressCount = 0;
                } else if (hovered) {
                    v += this.height;
                } else {
                    this.beingPressed = false;
                    this.pressCount = 0;
                }
                this.drawTexturedModalRect(this.x, this.y, u, v, this.width, this.height);
                if (this.beingPressed) {
                    if (this.pressCount >= MAX_PRESS) {
                        trigger = this;
                        this.pressCount = 0;
                    } else {
                        ++this.pressCount;
                    }
                }
            }
        }

        @Override
        public void mouseReleased(int mouseX, int mouseY) {
            this.beingPressed = false;
            this.pressCount = 0;
            super.mouseReleased(mouseX, mouseY);
        }

        @Override
        public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
            if (this.enabled) {
                this.beingPressed = true;
            }
            return super.mousePressed(mc, mouseX, mouseY);
        }
    }

    // ----- Inner: CraftingContainer -----

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static class CraftingContainer extends Container {
        public CraftingContainer() {
            this.addSlotToContainer(new Slot(selectedItemInv, 0, 49, 99));
            this.addSlotToContainer(new Slot(currentCraftInv, 0, 107, 99));
            int slotIndex = 0;
            for (int i = 0; i < 3; ++i) {
                for (int j = 0; j < 9; ++j) {
                    this.addSlotToContainer(new Slot(recipesInv, slotIndex, j * 18 - 2, i * 18 + 23));
                    ++slotIndex;
                }
            }
        }

        public static void scrollTo(float amount) {
            int index = (int) ((float) itemList.size() * amount);
            if (index > itemList.size() - 27) {
                index = itemList.size() - 27;
            }
            if (index < 0) {
                index = 0;
            }
            if (villager.currentCraftItem != null) {
                currentCraftInv.setInventorySlotContents(0, villager.currentCraftItem.getItem());
            } else {
                currentCraftInv.setInventorySlotContents(0, ItemStack.EMPTY);
            }
            int slotIndex = 0;
            for (int i = 0; i < 3; ++i) {
                for (int j = 0; j < 9; ++j) {
                    if (index >= itemList.size()) {
                        recipesInv.setInventorySlotContents(slotIndex, ItemStack.EMPTY);
                    } else {
                        recipesInv.setInventorySlotContents(slotIndex, (ItemStack) itemList.get(index));
                    }
                    ++slotIndex;
                    ++index;
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
                if (slot == null || !slot.getHasStack()) {
                    return ItemStack.EMPTY;
                }
                if (slot.inventory.getName().equals("Recipes")) {
                    lowestStackSize = slot.getStack().getCount();
                    selectedItemInv.setInventorySlotContents(0, slot.getStack().copy());
                }
            } catch (Exception e) {
                // ignore
            }
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack transferStackInSlot(EntityPlayer player, int index) {
            return ItemStack.EMPTY;
        }
    }
}
