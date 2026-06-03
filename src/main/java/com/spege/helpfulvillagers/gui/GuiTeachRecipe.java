package com.spege.helpfulvillagers.gui;

import com.spege.helpfulvillagers.crafting.VillagerRecipe;
import com.spege.helpfulvillagers.entity.AbstractVillager;
import com.spege.helpfulvillagers.main.HelpfulVillagers;
import com.spege.helpfulvillagers.network.AddRecipePacket;
import com.spege.helpfulvillagers.network.ResetRecipesPacket;
import com.spege.helpfulvillagers.util.AIHelper;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerWorkbench;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * Teach-custom-recipe screen. A crafting grid backed by {@link VillagerContainerWorkbench}
 * plus Teach/Reset/Back buttons and a popup confirmation system.
 */
@SuppressWarnings({ "null", "deprecation" })
public class GuiTeachRecipe extends GuiContainer {
    private static final ResourceLocation CRAFTING_TABLE_GUI =
            new ResourceLocation("textures/gui/container/crafting_table.png");
    private static final ResourceLocation DIALOG_BG =
            new ResourceLocation("helpfulvillagers", "textures/gui/dialog_background.png");

    private final int xSizeOfTexture = 176;
    private final int ySizeOfTexture = 166;
    private static final int MAX_DISPLAY_TIME = 120;

    private GuiButton teachButton;
    private GuiButton resetButton;
    private GuiButton backButton;
    private GuiButton yesButton;
    private GuiButton noButton;
    private GuiButton replaceButton;
    private GuiButton deleteButton;
    private GuiButton cancelButton;

    private final EntityPlayer player;
    private final AbstractVillager villager;
    private String displayText;
    private int displayTime;
    private static int popupNum;
    private List<Slot> tempSlots = new ArrayList<Slot>();

    public GuiTeachRecipe(EntityPlayer player, AbstractVillager villager) {
        super(new VillagerContainerWorkbench(player));
        this.player = player;
        this.villager = villager;
        this.displayText = null;
        this.displayTime = 0;
        popupNum = -1;
    }

    @Override
    public void initGui() {
        super.initGui();
        int posX = (this.width - this.xSizeOfTexture) / 2;
        int posY = (this.height - this.ySizeOfTexture) / 2;

        this.teachButton = new GuiButton(0, posX + 90, posY + 60, 80, 20, "Teach Recipe");
        this.buttonList.add(this.teachButton);
        this.teachButton.enabled = false;

        this.resetButton = new GuiButton(1, posX + 90, posY + 5, 80, 20, "Reset Recipes");
        this.buttonList.add(this.resetButton);

        this.backButton = new GuiButton(2, posX + 6, posY + 33, 20, 20, "<-");
        this.buttonList.add(this.backButton);

        this.yesButton = new GuiButton(3, posX + 45, posY + 115, 40, 20, "Yes");
        this.buttonList.add(this.yesButton);
        this.yesButton.visible = false;
        this.yesButton.enabled = false;

        this.noButton = new GuiButton(4, posX + 95, posY + 115, 40, 20, "No");
        this.buttonList.add(this.noButton);
        this.noButton.visible = false;
        this.noButton.enabled = false;

        this.replaceButton = new GuiButton(5, posX + 15, posY + 115, 45, 20, "Replace");
        this.buttonList.add(this.replaceButton);
        this.replaceButton.visible = false;
        this.replaceButton.enabled = false;

        this.deleteButton = new GuiButton(6, posX + 65, posY + 115, 45, 20, "Delete");
        this.buttonList.add(this.deleteButton);
        this.deleteButton.visible = false;
        this.deleteButton.enabled = false;

        this.cancelButton = new GuiButton(7, posX + 115, posY + 115, 45, 20, "Cancel");
        this.buttonList.add(this.cancelButton);
        this.cancelButton.visible = false;
        this.cancelButton.enabled = false;
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (VillagerContainerWorkbench.output != null && !VillagerContainerWorkbench.output.isEmpty()) {
            VillagerRecipe newRecipe = new VillagerRecipe(VillagerContainerWorkbench.inputs,
                    VillagerContainerWorkbench.output, false);
            boolean isKnownNonCustom = this.villager.knownRecipes.contains(newRecipe)
                    && !this.villager.customRecipes.contains(newRecipe);
            this.teachButton.enabled = isKnownNonCustom ? false : popupNum < 0;
        } else {
            this.teachButton.enabled = false;
        }
        this.resetButton.enabled = popupNum < 0 && this.villager.customRecipes.size() > 0;
        this.backButton.enabled = popupNum < 0;

        this.yesButton.visible = popupNum == 0;
        this.yesButton.enabled = popupNum == 0;
        this.noButton.visible = popupNum == 0;
        this.noButton.enabled = popupNum == 0;

        this.replaceButton.visible = popupNum == 1;
        this.replaceButton.enabled = popupNum == 1;
        this.deleteButton.visible = popupNum == 1;
        this.deleteButton.enabled = popupNum == 1;
        this.cancelButton.visible = popupNum == 1;
        this.cancelButton.enabled = popupNum == 1;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);

        // Tooltips on disabled buttons
        for (int i = 0; i < this.buttonList.size(); ++i) {
            GuiButton btn = this.buttonList.get(i);
            if (btn.isMouseOver() && !btn.enabled) {
                if (btn.id == 0 && VillagerContainerWorkbench.output != null
                        && !VillagerContainerWorkbench.output.isEmpty() && popupNum < 0) {
                    this.drawHoveringText(Arrays.asList("Cannot Change Profession Recipe"), mouseX, mouseY);
                } else if (btn.id == 1 && popupNum < 0) {
                    this.drawHoveringText(Arrays.asList("No Custom Recipes Found"), mouseX, mouseY);
                }
            }
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case 0: { // Teach
                VillagerRecipe newRecipe = new VillagerRecipe(VillagerContainerWorkbench.inputs,
                        VillagerContainerWorkbench.output, false);
                if (!this.villager.canCraft(VillagerContainerWorkbench.output)) {
                    this.villager.addCustomRecipe(newRecipe);
                    HelpfulVillagers.network.sendToServer(
                            new AddRecipePacket(this.villager.getEntityId(), newRecipe, 0));
                    this.displayText("Recipe Added");
                } else {
                    popupNum = 1;
                    this.tempSlots.clear();
                    this.tempSlots.addAll(this.inventorySlots.inventorySlots);
                    Iterator<Slot> iter = this.inventorySlots.inventorySlots.iterator();
                    while (iter.hasNext()) {
                        Slot slot = iter.next();
                        if (slot.slotNumber >= 10 && slot.slotNumber <= 36) {
                            iter.remove();
                        }
                    }
                }
                break;
            }
            case 1: { // Reset
                popupNum = 0;
                this.tempSlots.clear();
                this.tempSlots.addAll(this.inventorySlots.inventorySlots);
                Iterator<Slot> iter = this.inventorySlots.inventorySlots.iterator();
                while (iter.hasNext()) {
                    Slot slot = iter.next();
                    if (slot.slotNumber >= 10 && slot.slotNumber <= 36) {
                        iter.remove();
                    }
                }
                break;
            }
            case 2: { // Back
                this.player.openGui(HelpfulVillagers.instance, 4, this.villager.world,
                        this.villager.getEntityId(), 0, 0);
                break;
            }
            case 3: { // Yes (confirm reset)
                this.villager.resetRecipes();
                HelpfulVillagers.network.sendToServer(
                        new ResetRecipesPacket(this.villager.getEntityId()));
                this.displayText("Recipes Reset");
                popupNum = -1;
                this.inventorySlots.inventorySlots.clear();
                this.inventorySlots.inventorySlots.addAll(this.tempSlots);
                break;
            }
            case 4: { // No (cancel reset)
                popupNum = -1;
                this.inventorySlots.inventorySlots.clear();
                this.inventorySlots.inventorySlots.addAll(this.tempSlots);
                break;
            }
            case 5: { // Replace
                VillagerRecipe newRecipe = new VillagerRecipe(VillagerContainerWorkbench.inputs,
                        VillagerContainerWorkbench.output, false);
                this.villager.replaceCustomRecipe(newRecipe);
                HelpfulVillagers.network.sendToServer(
                        new AddRecipePacket(this.villager.getEntityId(), newRecipe, 1));
                this.displayText("Recipe Replaced");
                popupNum = -1;
                this.inventorySlots.inventorySlots.clear();
                this.inventorySlots.inventorySlots.addAll(this.tempSlots);
                break;
            }
            case 6: { // Delete
                VillagerRecipe newRecipe = new VillagerRecipe(VillagerContainerWorkbench.inputs,
                        VillagerContainerWorkbench.output, false);
                this.villager.deleteCustomRecipe(newRecipe);
                HelpfulVillagers.network.sendToServer(
                        new AddRecipePacket(this.villager.getEntityId(), newRecipe, 2));
                this.displayText("Recipe Deleted");
                popupNum = -1;
                this.inventorySlots.inventorySlots.clear();
                this.inventorySlots.inventorySlots.addAll(this.tempSlots);
                break;
            }
            case 7: { // Cancel
                popupNum = -1;
                this.inventorySlots.inventorySlots.clear();
                this.inventorySlots.inventorySlots.addAll(this.tempSlots);
                break;
            }
        }
    }

    private void displayText(String text) {
        this.displayText = text;
        this.displayTime = 0;
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        this.fontRenderer.drawString(I18n.format("container.crafting"), 28, 6, 0x404040);
        this.fontRenderer.drawString(I18n.format("container.inventory"), 8, this.ySize - 96 + 2, 0x404040);

        if (this.displayText != null) {
            this.drawCenteredString(this.fontRenderer, this.displayText, 90, -10, 0xFFFFFF);
            ++this.displayTime;
            if (this.displayTime > MAX_DISPLAY_TIME) {
                this.displayText = null;
                this.displayTime = 0;
            }
        }

        if (popupNum == 0) {
            this.drawCenteredString(this.fontRenderer, "Delete All Custom Recipes?", 90, 90, 0xFFFFFF);
        } else if (popupNum == 1) {
            this.drawCenteredString(this.fontRenderer, "Recipe Already Known", 90, 90, 0xFFFFFF);
            this.drawCenteredString(this.fontRenderer, "Replace Recipe?", 90, 100, 0xFFFFFF);
        }
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        this.mc.getTextureManager().bindTexture(CRAFTING_TABLE_GUI);
        int posX = (this.width - this.xSize) / 2;
        int posY = (this.height - this.ySize) / 2;
        this.drawTexturedModalRect(posX, posY, 0, 0, this.xSize, this.ySize);

        if (popupNum >= 0) {
            this.mc.getTextureManager().bindTexture(DIALOG_BG);
            this.drawTexturedModalRect(posX + 7, posY + 83, 10, 10, 162, 54);
        }
    }

    // ------------------------------------------------------------------
    // Server-side container returned by GuiHandler for GUI id 6.
    // ------------------------------------------------------------------
    public static class VillagerContainerWorkbench extends ContainerWorkbench {
        private final World worldObj;
        private static ItemStack output;
        private static ArrayList<ItemStack> inputs = new ArrayList<ItemStack>();

        public VillagerContainerWorkbench(EntityPlayer player) {
            super(player.inventory, player.world,
                    new BlockPos((int) player.posX, (int) player.posY, (int) player.posZ));
            this.worldObj = player.world;
        }

        @Override
        public boolean canInteractWith(EntityPlayer player) {
            return true;
        }

        @Override
        public void onCraftMatrixChanged(IInventory inventoryIn) {
            // Find matching recipe and set output
            IRecipe recipe = CraftingManager.findMatchingRecipe(this.craftMatrix, this.worldObj);
            if (recipe != null) {
                this.craftResult.setInventorySlotContents(0, recipe.getCraftingResult(this.craftMatrix));
            } else {
                this.craftResult.setInventorySlotContents(0, ItemStack.EMPTY);
            }
            output = this.craftResult.getStackInSlot(0);

            // Collect inputs
            inputs.clear();
            for (int i = 0; i < 3; ++i) {
                for (int j = 0; j < 3; ++j) {
                    ItemStack item = this.craftMatrix.getStackInRowAndColumn(j, i);
                    if (!item.isEmpty()) {
                        AIHelper.mergeItemStackArrays(new ItemStack(item.getItem(), 1), inputs);
                    }
                }
            }
        }

        @Override
        public ItemStack slotClick(int slotId, int dragType, ClickType clickTypeIn, EntityPlayer player) {
            if (popupNum >= 0) {
                return ItemStack.EMPTY;
            }
            return super.slotClick(slotId, dragType, clickTypeIn, player);
        }

        @Override
        public ItemStack transferStackInSlot(EntityPlayer playerIn, int index) {
            return ItemStack.EMPTY;
        }
    }
}
