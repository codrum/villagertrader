/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client/).
 * Copyright (c) 2021 Meteor Development.
 */

package meteordevelopment.addons.template.mixin;

import meteordevelopment.addons.template.modules.villager_trader.AutoTrade;
import meteordevelopment.addons.template.modules.villager_trader.VillagerTrader;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.MerchantScreen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.village.TradeOfferList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;

@Mixin(MerchantScreen.class)

public abstract class MerchantScreenMixin extends HandledScreen<MerchantScreenHandler> {


    @Shadow private int selectedIndex;

    public MerchantScreenMixin(MerchantScreenHandler merchantContainer_1, PlayerInventory playerInventory_1, Text text_1) {
        super(merchantContainer_1, playerInventory_1, text_1);
    }

    @Inject(method="renderScrollbar", at=@At("RETURN")) // Trades are loaded on render, this is the method called directly after trades are loaded.
    private void onRenderScrollbar(MatrixStack matrices, int x, int y, TradeOfferList tradeOffers, CallbackInfo ci) {
        VillagerTrader villagerTrader = Modules.get().get(VillagerTrader.class);
        if (!villagerTrader.isActive() || !villagerTrader.isPresetTradeTicked()) {
            return;
        }
        MerchantScreenAccessor mrcntscrn = (MerchantScreenAccessor)this;

        BetterGuiMerchantAccessor bttrmrcnt = (BetterGuiMerchantAccessor)this;

        ArrayList<Item> wanted = villagerTrader.getWantedTradeItems();

        for(Item item : wanted){
            System.out.println("Wanted item: " + item.asItem());
            int tradeIndex = bttrmrcnt.invokeGetWantedItemIndex(item);
            if(tradeIndex != -1){
                mrcntscrn.setSelectedIndex(tradeIndex);
                System.out.println("trade index set to: " + tradeIndex);
                mrcntscrn.invokeSyncRecipeIndex();
            }
        }
        this.onClose();
    }

    @Inject(method="syncRecipeIndex", at=@At("RETURN")) //Called whenever a recipe is selected, or manually by us
    public void tradeOnSetRecipeIndex(CallbackInfo ci) {
        VillagerTrader villagerTrader = Modules.get().get(VillagerTrader.class);
        if (!villagerTrader.isActive()) {
            return;
        }
        this.onMouseClick(null, 0, 0, SlotActionType.QUICK_MOVE);
        this.onMouseClick(null, 1, 0, SlotActionType.QUICK_MOVE);

        ((AutoTrade)this).trade(selectedIndex);

    }

}
