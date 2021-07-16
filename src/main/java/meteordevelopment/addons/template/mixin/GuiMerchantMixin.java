/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client/).
 * Copyright (c) 2021 Meteor Development.
 */

package meteordevelopment.addons.template.mixin;


import meteordevelopment.addons.template.modules.villager_trader.BetterGuiMerchant;
import meteordevelopment.addons.template.modules.villager_trader.VillagerTrader;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HandledScreens.class)
public abstract class GuiMerchantMixin {

    @Inject(method = "open", at = @At("HEAD"), cancellable = true) //When a gui screen is opened
    private static void displayVillagerTradeGui(ScreenHandlerType type, MinecraftClient client,
                                                int any, Text component, CallbackInfo ci) throws InterruptedException {
        if (type == ScreenHandlerType.MERCHANT) {
            VillagerTrader villagerTrader = Modules.get().get(VillagerTrader.class);
            if(villagerTrader.isActive()) {
                MerchantScreenHandler container = ScreenHandlerType.MERCHANT.create(any, client.player.getInventory());
                BetterGuiMerchant screen = villagerTrader.initVillagerTrader(container, client.player.getInventory(), component);
                client.player.currentScreenHandler = container;
                client.openScreen(screen);

                ci.cancel();
            }
        }
    }

}
