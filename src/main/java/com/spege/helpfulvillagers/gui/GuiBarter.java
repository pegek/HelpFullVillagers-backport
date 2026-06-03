package com.spege.helpfulvillagers.gui;

import com.spege.helpfulvillagers.entity.AbstractVillager;
import com.spege.helpfulvillagers.main.HelpfulVillagers;
import com.spege.helpfulvillagers.network.PlayerAccountServerPacket;
import com.spege.helpfulvillagers.network.PlayerCraftMatrixResetPacket;
import com.spege.helpfulvillagers.network.PlayerInventoryPacket;
import com.spege.helpfulvillagers.network.PlayerItemStackPacket;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Items;

import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * Barter/economy exchange screen. Uses a creative-tab-style item browser with a
 * buy/sell/deposit/withdraw credit system backed by the village economy.
 */
@SuppressWarnings({ "null", "deprecation" })
@SideOnly(Side.CLIENT)
public class GuiBarter extends GuiContainer {
    private static final ResourceLocation TABS_TEXTURE =
            new ResourceLocation("helpfulvillagers", "textures/gui/barter_inventory/tabs.png");
    private static InventoryBasic barterItems = new InventoryBasic("Barter", true, 45);
    private static InventoryBasic buyItemInv = new InventoryBasic("Buy Item", true, 1);
    private static InventoryBasic sellItemInv = new InventoryBasic("Sell Item", true, 1);
    private static InventoryBasic currencyInputInv = new InventoryBasic("Currency Input", true, 1);
    private static InventoryBasic currencyOutputInv = new InventoryBasic("Currency Output", true, 1);
    private Slot sellItem;
    private Slot currencyOutput;
    private static int selectedTabIndex = CreativeTabs.BUILDING_BLOCKS.getTabIndex();
    private float currentScroll;
    private boolean isScrolling;
    private boolean wasClicking;
    private GuiTextField searchField;
    private List<Slot> originalSlots;
    private boolean clearSearch;
    private static int tabPage = 0;
    private int maxPages = 0;
    private final EntityPlayer player;
    private final AbstractVillager villager;
    private GuiButton creditBuyButton;
    private GuiButton creditSellButton;
    private GuiButton creditWithdrawButton;
    private ItemStack dragItem;

    public GuiBarter(EntityPlayer player, AbstractVillager villager) {
        super(new ContainerBarter(player, villager));
        this.ySize = 136;
        this.xSize = 195;
        this.player = player;
        this.villager = villager;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void initGui() {
        super.initGui();
        this.buttonList.clear();
        Keyboard.enableRepeatEvents(true);
        this.searchField = new GuiTextField(0, this.fontRenderer,
                this.guiLeft + 82, this.guiTop + 6, 89, this.fontRenderer.FONT_HEIGHT);
        this.searchField.setMaxStringLength(15);
        this.searchField.setEnableBackgroundDrawing(false);
        this.searchField.setVisible(false);
        this.searchField.setTextColor(0xFFFFFF);

        int prevTab = selectedTabIndex;
        selectedTabIndex = -1;
        this.setCurrentCreativeTab(CreativeTabs.CREATIVE_TAB_ARRAY[prevTab]);

        int tabCount = CreativeTabs.CREATIVE_TAB_ARRAY.length;
        if (tabCount > 12) {
            this.buttonList.add(new GuiButton(101, this.guiLeft, this.guiTop - 50, 20, 20, "<"));
            this.buttonList.add(new GuiButton(102, this.guiLeft + this.xSize - 20, this.guiTop - 50, 20, 20, ">"));
            this.maxPages = (tabCount - 12) / 10 + 1;
        }
        this.creditBuyButton = new GuiButton(10, this.guiLeft + 10, this.guiTop + 110, 40, 20, "Buy");
        this.buttonList.add(this.creditBuyButton);
        this.creditBuyButton.enabled = false;
        this.creditBuyButton.visible = selectedTabIndex != CreativeTabs.INVENTORY.getTabIndex();

        this.creditSellButton = new GuiButton(11, this.guiLeft + 85, this.guiTop + 32, 42, 20, "Sell");
        this.buttonList.add(this.creditSellButton);
        this.creditSellButton.enabled = false;
        this.creditSellButton.visible = selectedTabIndex == CreativeTabs.INVENTORY.getTabIndex();

        this.creditWithdrawButton = new GuiButton(12, this.guiLeft + 135, this.guiTop + 32, 50, 20, "Withdraw");
        this.buttonList.add(this.creditWithdrawButton);
        this.creditWithdrawButton.enabled = false;
        this.creditWithdrawButton.visible = selectedTabIndex == CreativeTabs.INVENTORY.getTabIndex();
    }

    @Override
    public void updateScreen() {
        HelpfulVillagers.network.sendToServer(
                new PlayerCraftMatrixResetPacket(this.player.getEntityId()));
        HelpfulVillagers.network.sendToServer(
                new PlayerInventoryPacket(this.player.getEntityId(),
                        this.player.inventory.mainInventory.toArray(new ItemStack[0]),
                        this.player.inventory.armorInventory.toArray(new ItemStack[0])));
        this.creditBuyButton.visible = selectedTabIndex != CreativeTabs.INVENTORY.getTabIndex();
        this.creditSellButton.visible = selectedTabIndex == CreativeTabs.INVENTORY.getTabIndex();
        this.creditWithdrawButton.visible = selectedTabIndex == CreativeTabs.INVENTORY.getTabIndex();
        this.dragItem = this.player.inventory.getItemStack();
    }

    @Override
    protected void handleMouseClick(Slot slot, int slotId, int mouseButton, ClickType type) {
        boolean isInventoryTab = this.creditSellButton.visible;
        InventoryPlayer inventoryplayer = this.player.inventory;

        if (slot == null) {
            super.handleMouseClick(slot, slotId, mouseButton, type);
            return;
        }
        if (isInventoryTab) {
            if (slot == this.sellItem) {
                if (slot.getHasStack()) {
                    if (inventoryplayer.getItemStack().isEmpty()) {
                        ItemStack temp = slot.getStack().copy();
                        inventoryplayer.setItemStack(temp);
                        HelpfulVillagers.network.sendToServer(
                                new PlayerItemStackPacket(this.player.getEntityId(), temp));
                        slot.putStack(ItemStack.EMPTY);
                    }
                } else if (!inventoryplayer.getItemStack().isEmpty()) {
                    ItemStack temp = inventoryplayer.getItemStack();
                    inventoryplayer.setItemStack(ItemStack.EMPTY);
                    slot.putStack(temp);
                    HelpfulVillagers.network.sendToServer(
                            new PlayerItemStackPacket(this.player.getEntityId(), ItemStack.EMPTY));
                }
                this.calculateCurrencyOutput();
            } else if (slot == this.currencyOutput) {
                if (slot.getHasStack() && inventoryplayer.getItemStack().isEmpty()) {
                    int amount = slot.getStack().getCount();
                    inventoryplayer.setItemStack(slot.getStack());
                    slot.putStack(ItemStack.EMPTY);
                    HelpfulVillagers.network.sendToServer(
                            new PlayerItemStackPacket(this.player.getEntityId(), slot.getStack()));
                    HelpfulVillagers.network.sendToServer(
                            new PlayerCraftMatrixResetPacket(this.player.getEntityId()));
                    ItemStack sellStack = sellItemInv.getStackInSlot(0);
                    if (!sellStack.isEmpty()) {
                        this.villager.homeVillage.economy.getItemPrice(sellStack.getDisplayName()).increaseSupply(sellStack.getCount());
                        this.villager.homeVillage.economy.itemSyncServer(this.villager, sellStack);
                        sellItemInv.setInventorySlotContents(0, ItemStack.EMPTY);
                    } else {
                        this.villager.homeVillage.economy.accountWithdraw(this.player, amount);
                    }
                }
            } else {
                super.handleMouseClick(slot, slotId, mouseButton, type);
            }
        } else {
            this.inventorySlots.slotClick(slot.slotNumber, slotId, mouseButton == 1 ? ClickType.QUICK_MOVE : ClickType.PICKUP, this.player);
        }
    }

    private void calculateCurrencyOutput() {
        ItemStack item = sellItemInv.getStackInSlot(0);
        if (item.isEmpty()) {
            currencyOutputInv.setInventorySlotContents(0, ItemStack.EMPTY);
        } else {
            int price = this.villager.homeVillage.economy.getPrice(item.getDisplayName()) * item.getCount();
            if (price > 0 && price <= 64) {
                currencyOutputInv.setInventorySlotContents(0, new ItemStack(Items.EMERALD, price));
            }
        }
    }

    @Override
    public void onGuiClosed() {
        HelpfulVillagers.network.sendToServer(
                new PlayerInventoryPacket(this.player.getEntityId(),
                        this.player.inventory.mainInventory.toArray(new ItemStack[0]),
                        this.player.inventory.armorInventory.toArray(new ItemStack[0])));
        this.player.inventoryContainer = new ContainerPlayer(this.player.inventory, true, this.player);
        buyItemInv.setInventorySlotContents(0, ItemStack.EMPTY);
        ItemStack stack = currencyInputInv.getStackInSlot(0);
        if (!stack.isEmpty()) {
            this.player.dropItem(stack, true);
            this.mc.playerController.sendSlotPacket(this.dragItem, -1);
            this.player.inventory.setItemStack(ItemStack.EMPTY);
            this.dragItem = ItemStack.EMPTY;
        }
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (!CreativeTabs.CREATIVE_TAB_ARRAY[selectedTabIndex].hasSearchBar()) {
            if (net.minecraft.client.settings.GameSettings.isKeyDown(this.mc.gameSettings.keyBindChat)) {
                this.setCurrentCreativeTab(CreativeTabs.SEARCH);
            } else {
                super.keyTyped(typedChar, keyCode);
            }
        } else {
            if (this.clearSearch) {
                this.clearSearch = false;
                this.searchField.setText("");
            }
            if (!this.checkHotbarKeys(keyCode)) {
                if (this.searchField.textboxKeyTyped(typedChar, keyCode)) {
                    this.updateCreativeSearch();
                } else {
                    super.keyTyped(typedChar, keyCode);
                }
            }
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void updateCreativeSearch() {
        ContainerBarter container = (ContainerBarter) this.inventorySlots;
        container.itemList.clear();
        CreativeTabs tab = CreativeTabs.CREATIVE_TAB_ARRAY[selectedTabIndex];
        if (tab.hasSearchBar() && tab != CreativeTabs.SEARCH) {
            tab.displayAllRelevantItems(container.itemList);
            this.updateFilteredItems(container);
            return;
        }
        for (Item item : Item.REGISTRY) {
            if (item == null || item.getCreativeTab() == null) continue;
            item.getSubItems(tab, container.itemList);
        }
        // Note: getSubItems/displayAllRelevantItems both take NonNullList<ItemStack> in 1.12.2
        this.updateFilteredItems(container);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void updateFilteredItems(ContainerBarter container) {
        // 1.12.2: enchanted books are populated by the creative tab system — no manual getAll needed
        Iterator<ItemStack> iterator = container.itemList.iterator();
        String s1 = this.searchField.getText().toLowerCase();
        while (iterator.hasNext()) {
            ItemStack itemstack = iterator.next();
            boolean flag = false;
            for (String s : itemstack.getTooltip(this.mc.player,
                    this.mc.gameSettings.advancedItemTooltips
                            ? net.minecraft.client.util.ITooltipFlag.TooltipFlags.ADVANCED
                            : net.minecraft.client.util.ITooltipFlag.TooltipFlags.NORMAL)) {
                if (s.toLowerCase().contains(s1)) {
                    flag = true;
                    break;
                }
            }
            if (!flag) {
                iterator.remove();
            }
        }
        this.currentScroll = 0.0f;
        container.scrollTo(0.0f);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        CreativeTabs tab = CreativeTabs.CREATIVE_TAB_ARRAY[selectedTabIndex];
        if (tab != null && tab.drawInForegroundOfTab()) {
            GlStateManager.disableBlend();
            this.fontRenderer.drawString(
                    I18n.format(tab.getTranslatedTabLabel()), 8, 6, 0x404040);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton == 0) {
            int l = mouseX - this.guiLeft;
            int i1 = mouseY - this.guiTop;
            for (CreativeTabs tab : CreativeTabs.CREATIVE_TAB_ARRAY) {
                if (tab == null || !this.isMouseOverTab(tab, l, i1)) continue;
                return;
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        if (state == 0) {
            int l = mouseX - this.guiLeft;
            int i1 = mouseY - this.guiTop;
            for (CreativeTabs tab : CreativeTabs.CREATIVE_TAB_ARRAY) {
                if (tab == null || !this.isMouseOverTab(tab, l, i1)) continue;
                this.setCurrentCreativeTab(tab);
                return;
            }
        }
        super.mouseReleased(mouseX, mouseY, state);
    }

    private boolean needsScrollBars() {
        if (CreativeTabs.CREATIVE_TAB_ARRAY[selectedTabIndex] == null) {
            return false;
        }
        return selectedTabIndex != CreativeTabs.INVENTORY.getTabIndex()
                && CreativeTabs.CREATIVE_TAB_ARRAY[selectedTabIndex].shouldHidePlayerInventory()
                && ((ContainerBarter) this.inventorySlots).hasScrollbar();
    }

    @SuppressWarnings("unchecked")
    private void setCurrentCreativeTab(CreativeTabs tab) {
        if (tab == null) {
            return;
        }
        int prev = selectedTabIndex;
        selectedTabIndex = tab.getTabIndex();
        ContainerBarter container = (ContainerBarter) this.inventorySlots;
        container.itemList.clear();
        tab.displayAllRelevantItems(container.itemList);

        if (tab == CreativeTabs.INVENTORY) {
            Container playerContainer = this.mc.player.inventoryContainer;
            if (this.originalSlots == null) {
                this.originalSlots = container.inventorySlots;
            }
            container.inventorySlots = new ArrayList<Slot>();
            for (int j = 0; j < playerContainer.inventorySlots.size(); ++j) {
                Slot slot = playerContainer.inventorySlots.get(j);
                container.inventorySlots.add(slot);
                if (j >= 5 && j < 9) {
                    int k = j - 5;
                    int row = k / 2;
                    int col = k % 2;
                    slot.xPos = 9 + row * 54;
                    slot.yPos = 6 + col * 27;
                } else if (j >= 0 && j < 5) {
                    slot.yPos = -2000;
                    slot.xPos = -2000;
                } else if (j < playerContainer.inventorySlots.size()) {
                    int k = j - 9;
                    int col = k % 9;
                    int row = k / 9;
                    slot.xPos = 9 + col * 18;
                    slot.yPos = j >= 36 ? 112 : 54 + row * 18;
                }
            }
            this.sellItem = new Slot(sellItemInv, 0, 99, 12);
            container.inventorySlots.add(this.sellItem);
            this.currencyOutput = new Slot(currencyOutputInv, 0, 153, 12);
            container.inventorySlots.add(this.currencyOutput);
        } else if (prev == CreativeTabs.INVENTORY.getTabIndex()) {
            container.inventorySlots = this.originalSlots;
            this.originalSlots = null;
        }

        if (this.searchField != null) {
            if (tab.hasSearchBar()) {
                this.searchField.setVisible(true);
                this.searchField.setCanLoseFocus(false);
                this.searchField.setFocused(true);
                this.searchField.setText("");
                this.updateCreativeSearch();
            } else {
                this.searchField.setVisible(false);
                this.searchField.setCanLoseFocus(true);
                this.searchField.setFocused(false);
            }
        }
        this.currentScroll = 0.0f;
        container.scrollTo(0.0f);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int i = Mouse.getEventDWheel();
        if (i != 0 && this.needsScrollBars()) {
            int j = ((ContainerBarter) this.inventorySlots).itemList.size() / 9 - 5 + 1;
            if (i > 0) i = 1;
            if (i < 0) i = -1;
            this.currentScroll = (float) ((double) this.currentScroll - (double) i / (double) j);
            if (this.currentScroll < 0.0f) this.currentScroll = 0.0f;
            if (this.currentScroll > 1.0f) this.currentScroll = 1.0f;
            ((ContainerBarter) this.inventorySlots).scrollTo(this.currentScroll);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (this.villager.homeVillage == null || this.villager.homeVillage.economy == null
                || !this.villager.homeVillage.pricesCalculated
                || this.villager.homeVillage.economy.getItemPrices().isEmpty()) {
            int bgW = 140;
            int bgH = 40;
            this.drawDefaultBackground();
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
            this.mc.getTextureManager().bindTexture(
                    new ResourceLocation("helpfulvillagers", "textures/gui/dialog_background.png"));
            int posX = (this.width - bgW) / 2;
            int posY = (this.height - bgH) / 2;
            this.drawString(this.fontRenderer, "Calculating Village Prices...",
                    posX + 5, posY + 15, 0xFFFFFF);
        } else {
            boolean flag = Mouse.isButtonDown(0);
            int k = this.guiLeft;
            int l = this.guiTop;
            int i1 = k + 175;
            int j1 = l + 18;
            int k1 = i1 + 14;
            int l1 = j1 + 112;
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
                ((ContainerBarter) this.inventorySlots).scrollTo(this.currentScroll);
            }
            super.drawScreen(mouseX, mouseY, partialTicks);

            // Tab hover tooltips
            CreativeTabs[] tabs = CreativeTabs.CREATIVE_TAB_ARRAY;
            int start = tabPage * 10;
            int end = Math.min(tabs.length, (tabPage + 1) * 10 + 2);
            if (tabPage != 0) {
                start += 2;
            }
            boolean rendered = false;
            for (int j = start; j < end; ++j) {
                CreativeTabs tab = tabs[j];
                if (tab == null || !this.renderTabHoverText(tab, mouseX, mouseY)) continue;
                rendered = true;
                break;
            }
            if (!rendered) {
                this.renderTabHoverText(CreativeTabs.SEARCH, mouseX, mouseY);
                this.renderTabHoverText(CreativeTabs.INVENTORY, mouseX, mouseY);
            }
            if (this.maxPages != 0) {
                String page = String.format("%d / %d", tabPage + 1, this.maxPages + 1);
                int textWidth = this.fontRenderer.getStringWidth(page);
                GlStateManager.disableLighting();
                this.zLevel = 300.0f;
                itemRender.zLevel = 300.0f;
                this.fontRenderer.drawString(page,
                        this.guiLeft + this.xSize / 2 - textWidth / 2, this.guiTop - 44, -1);
                this.zLevel = 0.0f;
                itemRender.zLevel = 0.0f;
            }
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
            GlStateManager.disableLighting();
        }

        // Credits line — always shown
        int accountVal = 0;
        try {
            accountVal = this.villager.homeVillage.economy.getAccount(this.player);
            if (accountVal < 0) accountVal = 0;
        } catch (Exception e) {
            // economy not initialized yet
        }
        this.drawString(this.fontRenderer, "Credits: $" + accountVal,
                this.guiLeft, this.guiTop + 165, 0xFFFFFF);

        // Buy button logic
        if (this.creditBuyButton.visible) {
            int price = -1;
            if (!buyItemInv.getStackInSlot(0).isEmpty()) {
                ItemStack buyItem = buyItemInv.getStackInSlot(0);
                price = this.villager.homeVillage.economy.getPrice(buyItem.getDisplayName()) * buyItem.getCount();
                this.drawString(this.fontRenderer, "$" + price,
                        this.guiLeft + 145, this.guiTop + 115, 0xFFFFFF);
                if (price >= 0 && this.villager.homeVillage.economy.getAccount(this.player) >= price) {
                    this.creditBuyButton.enabled = true;
                } else {
                    this.creditBuyButton.enabled = false;
                    if (this.creditBuyButton.isMouseOver()) {
                        String msg = price < 0 ? "ERROR: Invalid Item Price" : "Insufficient Credits";
                        this.drawHoveringText(Arrays.asList(msg), mouseX, mouseY);
                    }
                }
            } else {
                this.creditBuyButton.enabled = false;
                if (this.creditBuyButton.isMouseOver()) {
                    this.drawHoveringText(Arrays.asList("No Item Selected"), mouseX, mouseY);
                }
            }
        } else {
            this.creditBuyButton.enabled = false;
        }

        // Sell button logic
        this.creditSellButton.displayString = "Sell";
        if (this.creditSellButton.visible) {
            if (!sellItemInv.getStackInSlot(0).isEmpty()) {
                ItemStack sellStack = sellItemInv.getStackInSlot(0);
                if (sellStack.getItem().equals(Items.EMERALD)) {
                    this.drawString(this.fontRenderer, "$" + sellStack.getCount(),
                            this.guiLeft + 175, this.guiTop + 15, 0xFFFFFF);
                    this.creditSellButton.enabled = true;
                    this.creditSellButton.displayString = "Deposit";
                } else if (this.villager.homeVillage.economy.getPrice(sellStack.getDisplayName()) > 0) {
                    int price = this.villager.homeVillage.economy.getPrice(sellStack.getDisplayName())
                            * sellStack.getCount();
                    this.drawString(this.fontRenderer, "$" + price,
                            this.guiLeft + 175, this.guiTop + 15, 0xFFFFFF);
                    this.creditSellButton.enabled = true;
                } else {
                    this.creditSellButton.enabled = false;
                    if (this.creditSellButton.isMouseOver()) {
                        this.drawHoveringText(Arrays.asList("Item Has No Price"), mouseX, mouseY);
                    }
                }
            } else {
                this.creditSellButton.enabled = false;
                if (this.creditSellButton.isMouseOver()) {
                    this.drawHoveringText(Arrays.asList("No Item Selected"), mouseX, mouseY);
                }
            }
            // Withdraw button logic
            if (this.villager.homeVillage.economy.getAccount(this.player) > 0) {
                if (!this.creditSellButton.enabled && sellItemInv.getStackInSlot(0).isEmpty()) {
                    this.creditWithdrawButton.enabled = true;
                } else {
                    this.creditWithdrawButton.enabled = false;
                    if (this.creditWithdrawButton.isMouseOver()) {
                        this.drawHoveringText(Arrays.asList("Sell Item Selected"), mouseX, mouseY);
                    }
                }
            } else {
                this.creditWithdrawButton.enabled = false;
                if (this.creditWithdrawButton.isMouseOver()) {
                    this.drawHoveringText(Arrays.asList("Insufficient Credits"), mouseX, mouseY);
                }
            }
        } else {
            this.creditSellButton.enabled = false;
        }
    }

    @Override
    protected void renderHoveredToolTip(int mouseX, int mouseY) {
        Slot slot = this.getSlotUnderMouse();
        if (slot != null && slot.getHasStack()) {
            ItemStack inputItem = slot.getStack();
            if (this.villager.homeVillage != null && this.villager.homeVillage.economy != null
                    && !this.villager.homeVillage.economy.getItemPrices().isEmpty()) {
                int price = this.villager.homeVillage.economy.getPrice(inputItem.getDisplayName());
                if (price > 0) {
                    ArrayList<String> list = new ArrayList<String>();
                    list.add("$" + price + " - " + inputItem.getDisplayName());
                    this.drawHoveringText(list, mouseX, mouseY);
                    return;
                }
            }
        }
        super.renderHoveredToolTip(mouseX, mouseY);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        RenderHelper.enableGUIStandardItemLighting();
        CreativeTabs currentTab = CreativeTabs.CREATIVE_TAB_ARRAY[selectedTabIndex];
        CreativeTabs[] tabs = CreativeTabs.CREATIVE_TAB_ARRAY;

        int start = tabPage * 10;
        int end = Math.min(tabs.length, (tabPage + 1) * 10 + 2);
        if (tabPage != 0) {
            start += 2;
        }
        for (int l = start; l < end; ++l) {
            CreativeTabs tab = tabs[l];
            this.mc.getTextureManager().bindTexture(TABS_TEXTURE);
            if (tab == null || tab.getTabIndex() == selectedTabIndex) continue;
            this.drawTab(tab);
        }
        if (tabPage != 0) {
            if (currentTab != CreativeTabs.SEARCH) {
                this.mc.getTextureManager().bindTexture(TABS_TEXTURE);
                this.drawTab(CreativeTabs.SEARCH);
            }
            if (currentTab != CreativeTabs.INVENTORY) {
                this.mc.getTextureManager().bindTexture(TABS_TEXTURE);
                this.drawTab(CreativeTabs.INVENTORY);
            }
        }

        this.mc.getTextureManager().bindTexture(
                new ResourceLocation("helpfulvillagers",
                        "textures/gui/barter_inventory/tab_" + currentTab.getBackgroundImageName()));
        this.drawTexturedModalRect(this.guiLeft, this.guiTop, 0, 0, this.xSize, this.ySize);
        this.searchField.drawTextBox();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);

        // Scrollbar
        int scrollX = this.guiLeft + 175;
        int scrollTop = this.guiTop + 18;
        int scrollBottom = scrollTop + 112;
        this.mc.getTextureManager().bindTexture(TABS_TEXTURE);
        if (currentTab.shouldHidePlayerInventory()) {
            this.drawTexturedModalRect(scrollX,
                    scrollTop + (int) ((float) (scrollBottom - scrollTop - 17) * this.currentScroll),
                    232 + (this.needsScrollBars() ? 0 : 12), 0, 12, 15);
        }

        // Don't render tab that's on wrong page
        if ((currentTab == null || currentTab.getTabPage() != tabPage)
                && currentTab != CreativeTabs.SEARCH && currentTab != CreativeTabs.INVENTORY) {
            return;
        }
        this.drawTab(currentTab);

        if (currentTab == CreativeTabs.INVENTORY) {
            GuiInventory.drawEntityOnScreen(
                    this.guiLeft + 43, this.guiTop + 45, 20,
                    (float) (this.guiLeft + 43 - mouseX),
                    (float) (this.guiTop + 45 - 30 - mouseY),
                    this.mc.player);
        }
    }

    private boolean isMouseOverTab(CreativeTabs tab, int relX, int relY) {
        if (tab.getTabPage() != tabPage && tab != CreativeTabs.SEARCH && tab != CreativeTabs.INVENTORY) {
            return false;
        }
        int col = tab.getTabColumn();
        int tabX = 28 * col;
        int tabY = 0;
        if (col == 5) {
            tabX = this.xSize - 28 + 2;
        } else if (col > 0) {
            tabX += col;
        }
        tabY = tab.isTabInFirstRow() ? tabY - 32 : tabY + this.ySize;
        return relX >= tabX && relX <= tabX + 28 && relY >= tabY && relY <= tabY + 32;
    }

    private boolean renderTabHoverText(CreativeTabs tab, int mouseX, int mouseY) {
        int col = tab.getTabColumn();
        int tabX = 28 * col;
        int tabY = 0;
        if (col == 5) {
            tabX = this.xSize - 28 + 2;
        } else if (col > 0) {
            tabX += col;
        }
        tabY = tab.isTabInFirstRow() ? tabY - 32 : tabY + this.ySize;
        if (this.isPointInRegion(tabX + 3, tabY + 3, 23, 27, mouseX, mouseY)) {
            this.drawHoveringText(I18n.format(tab.getTranslatedTabLabel()), mouseX, mouseY);
            return true;
        }
        return false;
    }

    private void drawTab(CreativeTabs tab) {
        boolean selected = tab.getTabIndex() == selectedTabIndex;
        boolean topRow = tab.isTabInFirstRow();
        int col = tab.getTabColumn();
        int tabU = col * 28;
        int tabV = 0;
        int tabX = this.guiLeft + 28 * col;
        int tabY = this.guiTop;
        int tabH = 32;

        if (selected) {
            tabV += 32;
        }
        if (col == 5) {
            tabX = this.guiLeft + this.xSize - 28;
        } else if (col > 0) {
            tabX += col;
        }
        if (topRow) {
            tabY -= 28;
        } else {
            tabV += 64;
            tabY += this.ySize - 4;
        }

        GlStateManager.disableLighting();
        GlStateManager.color(1.0f, 1.0f, 1.0f);
        GlStateManager.enableBlend();
        this.drawTexturedModalRect(tabX, tabY, tabU, tabV, 28, tabH);

        this.zLevel = 100.0f;
        itemRender.zLevel = 100.0f;
        int iconOffset = topRow ? 1 : -1;
        GlStateManager.enableLighting();
        GlStateManager.enableRescaleNormal();
        ItemStack iconStack = tab.getTabIconItem();
        itemRender.renderItemAndEffectIntoGUI(iconStack, tabX + 6, tabY + 8 + iconOffset);
        itemRender.renderItemOverlays(this.fontRenderer, iconStack, tabX + 6, tabY + 8 + iconOffset);
        GlStateManager.disableLighting();
        itemRender.zLevel = 0.0f;
        this.zLevel = 0.0f;
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 101) {
            tabPage = Math.max(tabPage - 1, 0);
        } else if (button.id == 102) {
            tabPage = Math.min(tabPage + 1, this.maxPages);
        }
        if (button.id == 10) { // Buy with credits
            InventoryPlayer inv = this.player.inventory;
            ItemStack buyItem = buyItemInv.getStackInSlot(0);
            if (!buyItem.isEmpty()) {
                inv.setItemStack(buyItem);
                buyItemInv.setInventorySlotContents(0, ItemStack.EMPTY);
                int amount = this.villager.homeVillage.economy.getPrice(buyItem.getDisplayName())
                        * buyItem.getCount();
                this.villager.homeVillage.economy.accountWithdraw(this.player, amount);
                HelpfulVillagers.network.sendToServer(
                        new PlayerAccountServerPacket(this.player, this.villager));
                this.villager.homeVillage.economy.getItemPrice(buyItem.getDisplayName())
                        .increaseDemand(buyItem.getCount());
                this.villager.homeVillage.economy.itemSyncServer(this.villager, buyItem);
            }
        }
        if (button.id == 11) { // Sell
            ItemStack sellStack = sellItemInv.getStackInSlot(0);
            if (!sellStack.isEmpty()) {
                if (this.creditSellButton.displayString.equals("Deposit")) {
                    int amount = sellStack.getCount();
                    this.villager.homeVillage.economy.accountDeposit(this.player, amount);
                    sellItemInv.setInventorySlotContents(0, ItemStack.EMPTY);
                } else if (this.creditSellButton.displayString.equals("Sell")) {
                    ItemStack currOut = currencyOutputInv.getStackInSlot(0);
                    int amount;
                    if (!currOut.isEmpty()) {
                        amount = currOut.getCount();
                        this.villager.homeVillage.economy.accountDeposit(this.player, amount);
                        currencyOutputInv.setInventorySlotContents(0, ItemStack.EMPTY);
                        sellItemInv.setInventorySlotContents(0, ItemStack.EMPTY);
                    } else {
                        amount = this.villager.homeVillage.economy.getPrice(sellStack.getDisplayName())
                                * sellStack.getCount();
                        this.villager.homeVillage.economy.accountDeposit(this.player, amount);
                        sellItemInv.setInventorySlotContents(0, ItemStack.EMPTY);
                    }
                    this.villager.homeVillage.economy.getItemPrice(sellStack.getDisplayName())
                            .increaseSupply(sellStack.getCount());
                    this.villager.homeVillage.economy.itemSyncServer(this.villager, sellStack);
                }
            }
            HelpfulVillagers.network.sendToServer(
                    new PlayerAccountServerPacket(this.player, this.villager));
        }
        if (button.id == 12) { // Withdraw
            if (this.currencyOutput != null && this.currencyOutput.getHasStack()
                    && this.currencyOutput.getStack().getCount() < this.currencyOutput.getStack().getMaxStackSize()
                    && this.currencyOutput.getStack().getCount() < this.villager.homeVillage.economy.getAccount(this.player)) {
                this.currencyOutput.getStack().grow(1);
            } else if (this.currencyOutput != null && !this.currencyOutput.getHasStack()) {
                this.currencyOutput.putStack(new ItemStack(Items.EMERALD));
            }
        }
    }

    // ----- Inner: ContainerBarter -----

    @SideOnly(Side.CLIENT)
    static class ContainerBarter extends Container {
        public NonNullList<ItemStack> itemList = NonNullList.create();
        private final AbstractVillager villager;
        private final EntityPlayer player;

        @SuppressWarnings("unchecked")
        public ContainerBarter(EntityPlayer player, AbstractVillager villager) {
            this.villager = villager;
            this.player = player;
            this.addSlotToContainer(new Slot(buyItemInv, 0, 117, 113));
            this.addSlotToContainer(new Slot(currencyInputInv, 0, 63, 113));
            for (int i = 0; i < 5; ++i) {
                for (int j = 0; j < 9; ++j) {
                    this.addSlotToContainer(new Slot(barterItems, i * 9 + j, 9 + j * 18, 18 + i * 18));
                }
            }
            this.scrollTo(0.0f);
        }

        @Override
        public boolean canInteractWith(EntityPlayer player) {
            return true;
        }

        @SuppressWarnings("unchecked")
        public void scrollTo(float position) {
            Iterator<ItemStack> iterator = this.itemList.iterator();
            while (iterator.hasNext()) {
                ItemStack item = iterator.next();
                if (this.villager.homeVillage.economy.hasItem(item)
                        && this.villager.homeVillage.economy.getPrice(item.getDisplayName()) >= 0) {
                    continue;
                }
                iterator.remove();
            }
            int rows = this.itemList.size() / 9 - 5 + 1;
            int j = (int) ((double) (position * (float) rows) + 0.5);
            if (j < 0) j = 0;
            for (int k = 0; k < 5; ++k) {
                for (int l = 0; l < 9; ++l) {
                    int idx = l + (k + j) * 9;
                    if (idx >= 0 && idx < this.itemList.size()) {
                        barterItems.setInventorySlotContents(l + k * 9, this.itemList.get(idx));
                    } else {
                        barterItems.setInventorySlotContents(l + k * 9, ItemStack.EMPTY);
                    }
                }
            }
        }

        public boolean hasScrollbar() {
            return this.itemList.size() > 45;
        }

        @Override
        public ItemStack slotClick(int slotId, int dragType, ClickType clickType, EntityPlayer player) {
            InventoryPlayer inventoryplayer = player.inventory;
            try {
                Slot slot = this.getSlot(slotId);
                if (slot.inventory.getName().equals(barterItems.getName())) {
                    if (!buyItemInv.getStackInSlot(0).isEmpty()
                            && buyItemInv.getStackInSlot(0).getDisplayName().equals(slot.getStack().getDisplayName())) {
                        ItemStack selectedStack = buyItemInv.getStackInSlot(0);
                        if (selectedStack.getCount() < selectedStack.getMaxStackSize()) {
                            this.putStackInSlot(0, new ItemStack(selectedStack.getItem(),
                                    selectedStack.getCount() + 1, selectedStack.getMetadata()));
                        }
                    } else {
                        this.putStackInSlot(0, new ItemStack(slot.getStack().getItem(),
                                1, slot.getStack().getMetadata()));
                    }
                } else {
                    if (slot.inventory.equals(player.inventory)) {
                        return super.slotClick(slotId, dragType, clickType, player);
                    }
                    if (slot.inventory.equals(currencyInputInv)) {
                        if (slot.getHasStack()) {
                            if (inventoryplayer.getItemStack().isEmpty()) {
                                ItemStack temp = slot.getStack().copy();
                                inventoryplayer.setItemStack(temp);
                                HelpfulVillagers.network.sendToServer(
                                        new PlayerItemStackPacket(player.getEntityId(), temp));
                                slot.putStack(ItemStack.EMPTY);
                            }
                        } else if (!inventoryplayer.getItemStack().isEmpty()) {
                            ItemStack temp = inventoryplayer.getItemStack().copy();
                            slot.putStack(temp);
                            inventoryplayer.setItemStack(ItemStack.EMPTY);
                            HelpfulVillagers.network.sendToServer(
                                    new PlayerItemStackPacket(player.getEntityId(), ItemStack.EMPTY));
                        }
                    } else if (slot.inventory.equals(buyItemInv)) {
                        ItemStack buyStack = buyItemInv.getStackInSlot(0);
                        if (!buyStack.isEmpty()) {
                            int price = this.villager.homeVillage.economy.getPrice(buyStack.getDisplayName());
                            ItemStack currInput = currencyInputInv.getStackInSlot(0);
                            if (!currInput.isEmpty() && currInput.getItem().equals(Items.EMERALD)
                                    && currInput.getCount() >= (price *= buyStack.getCount())) {
                                int remaining = currInput.getCount() - price;
                                if (remaining <= 0) {
                                    this.putStackInSlot(1, ItemStack.EMPTY);
                                } else {
                                    this.putStackInSlot(1, new ItemStack(currInput.getItem(),
                                            remaining, currInput.getMetadata()));
                                }
                                this.villager.homeVillage.economy.getItemPrice(buyStack.getDisplayName())
                                        .increaseDemand(buyStack.getCount());
                                this.villager.homeVillage.economy.itemSyncServer(this.villager, buyStack);
                                super.slotClick(slotId, dragType, clickType, player);
                            }
                        }
                    }
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

        @Override
        public boolean canMergeSlot(ItemStack stack, Slot slot) {
            return slot.yPos > 90;
        }

        @Override
        public boolean canDragIntoSlot(Slot slot) {
            return false;
        }

        @Override
        public void onContainerClosed(EntityPlayer player) {
            // no-op
        }
    }
}
