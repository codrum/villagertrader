/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client/).
 * Copyright (c) 2021 Meteor Development.
 */

package meteordevelopment.addons.template.mixin;

import meteordevelopment.addons.template.modules.villager_trader.VillagerTrader;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.InventoryTweaks;

import meteordevelopment.meteorclient.utils.render.ContainerButtonWidget;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.ScreenHandlerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GenericContainerScreen.class)
public abstract class GenericContainerScreenMixin extends HandledScreen<GenericContainerScreenHandler> implements ScreenHandlerProvider<GenericContainerScreenHandler> {
    public GenericContainerScreenMixin(GenericContainerScreenHandler container, PlayerInventory playerInventory, Text name) {
        super(container, playerInventory, name);
    }
    private final InventoryTweaks invTweaks = Modules.get().get(InventoryTweaks.class);
    private final VillagerTrader villagerTrader = Modules.get().get(VillagerTrader.class);

    // Break
    private int count = 0;

    @Inject(method="render", at=@At("RETURN"))
    private void onRender(MatrixStack matrices, int mouseX, int mouseY, float delta, CallbackInfo ci){
        if(villagerTrader.isActive() && count < 1){
            System.out.println("Attempting to steal via render");
            invTweaks.steal(handler);

            count++;
        }
    }

    @Override
    protected void init() {
        super.init();



        if (invTweaks.isActive() && invTweaks.showButtons()) {
            addDrawableChild(new ContainerButtonWidget(
                x + backgroundWidth - 88,
                y + 3,
                40,
                12,
                new LiteralText("Steal"),
                button -> invTweaks.steal(handler))
            );

            addDrawableChild(new ContainerButtonWidget(
                x + backgroundWidth - 46,
                y + 3,
                40,
                12,
                new LiteralText("Dump"),
                button -> invTweaks.dump(handler))
            );

        }




        if (invTweaks.autoSteal()) invTweaks.steal(handler);
        if (invTweaks.autoDump()) invTweaks.dump(handler);
    }
}
