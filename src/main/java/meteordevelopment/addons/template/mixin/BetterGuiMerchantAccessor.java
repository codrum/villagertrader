package meteordevelopment.addons.template.mixin;

import meteordevelopment.addons.template.modules.villager_trader.BetterGuiMerchant;
import net.minecraft.item.Item;
import net.minecraft.village.TradeOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(BetterGuiMerchant.class)
public interface BetterGuiMerchantAccessor {

    @Invoker("getTradeRecipes")
    TradeOffer[] invokeGetTradeRecipes();

    @Invoker("getWantedItemIndex")
    int invokeGetWantedItemIndex(Item item);

}
