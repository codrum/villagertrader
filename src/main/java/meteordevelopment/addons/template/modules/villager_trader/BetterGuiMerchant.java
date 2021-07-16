/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client/).
 * Copyright (c) 2021 Meteor Development.
 */

/*
 * Made by Fargendo :)
 */
package meteordevelopment.addons.template.modules.villager_trader;

import net.minecraft.client.gui.screen.ingame.MerchantScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;

import java.util.ArrayList;

public class BetterGuiMerchant extends MerchantScreen implements AutoTrade {

    private int frames;     //DEBUG
    private  ArrayList<Item> wantedTradeItems;

    public BetterGuiMerchant(MerchantScreenHandler handler, PlayerInventory inv, Text title,  ArrayList<Item> wantedTrades
    ){
        super(handler, inv, title);
        wantedTradeItems = wantedTrades;

        System.out.println("wanted items: " + wantedTradeItems);
        frames = 0;
    }

    private TradeOffer[] getTradeRecipes(){
        TradeOfferList trades=handler.getRecipes();
        TradeOffer[] list = new TradeOffer[trades.size()];
        for(int i = 0; i < list.length; i++){
            list[i] = trades.get(i);
        }
        return list;
    }

    private int getWantedItemIndex(Item item){
        System.out.println("Getting index for item: " + item);
        TradeOfferList trades=handler.getRecipes();
        for(int i = 0; i < trades.size(); i++){
            if(trades.get(i).getSellItem().getItem() == item){
                return i;
            }
        }
        return -1;
    }

    @Override
    public void trade(int tradeIndex) {
        System.out.println("trade index: " +tradeIndex);
        TradeOfferList trades=handler.getRecipes();
        System.out.println("wanted length: " + wantedTradeItems.size());
        System.out.println("trades size: "+trades.size());
        System.out.println("sell item: " + trades.get(tradeIndex).getSellItem().getItem());

        TradeOffer recipe = trades.get(tradeIndex);

        int safeguard = 0;
        while (!recipe.isDisabled()
                // TODO how do we check this now? &&  client.player.getInventory().getCursorStack().isEmpty()
                &&  inputSlotsAreEmpty()
                &&  hasWantedTradeItems()
                &&  hasEnoughItemsInInventory(recipe)
                &&  canReceiveOutput(recipe.getSellItem())) {
            System.out.println("transacting...");
            transact(recipe);
            if ( ++safeguard > 50) {
                break;
            }
        }
    }

    private boolean hasWantedTradeItems() {
        return (wantedTradeItems.size() > 0);
    }

    private boolean inputSlotsAreEmpty() {
        boolean result =
            handler.getSlot(0).getStack().isEmpty()
        &&  handler.getSlot(1).getStack().isEmpty()
        &&  handler.getSlot(2).getStack().isEmpty();
        if (frames % 300 == 0) {/*
            System.out.println("stack 0: "+handler.getSlot(0).getStack().getTranslationKey()+"/"+handler.getSlot(0).getStack().getCount());
            System.out.println("stack 1: "+handler.getSlot(1).getStack().getTranslationKey()+"/"+handler.getSlot(0).getStack().getCount());
            System.out.println("stack 2: "+handler.getSlot(2).getStack().getTranslationKey()+"/"+handler.getSlot(0).getStack().getCount());
            System.out.println("result = "+result);*/
         }
        return result;
    }

    private boolean hasEnoughItemsInInventory(TradeOffer recipe) {
        if (!hasEnoughItemsInInventory(recipe.getAdjustedFirstBuyItem()))
            return false;
        if (!hasEnoughItemsInInventory(recipe.getSecondBuyItem()))
            return false;
        return true;
    }

    private boolean hasEnoughItemsInInventory(ItemStack stack) {
        int remaining=stack.getCount();
        for (int i=handler.slots.size()-36; i<handler.slots.size(); i++) {
            ItemStack invstack=handler.getSlot(i).getStack();
            if (invstack==null)
                continue;
            if (areItemStacksMergable(stack, invstack)) {
                //System.out.println("taking "+invstack.getCount()+" items from slot # "+i);
                remaining-=invstack.getCount();
            }
            if (remaining<=0)
                return true;
        }
        return false;
    }

    private boolean canReceiveOutput(ItemStack stack) {
        int remaining=stack.getCount();
        for (int i=handler.slots.size()-36; i<handler.slots.size(); i++) {
            ItemStack invstack=handler.getSlot(i).getStack();
            if (invstack==null || invstack.isEmpty()) {
                //System.out.println("can put result into empty slot "+i);
                return true;
            }
            if (areItemStacksMergable(stack, invstack)
            &&  stack.getMaxCount() >= stack.getCount() + invstack.getCount()) {
                //System.out.println("Can merge "+(invstack.getMaxStackSize()-invstack.getCount())+" items with slot "+i);
                remaining-=(invstack.getMaxCount()-invstack.getCount());
            }
            if (remaining<=0)
                return true;
        }
        return false;
    }

    private void transact(TradeOffer recipe) {
        int putback0, putback1=-1;
        putback0=fillSlot(0, recipe.getAdjustedFirstBuyItem());
        putback1=fillSlot(1, recipe.getSecondBuyItem());

        getslot(2, recipe.getSellItem(), putback0, putback1);
        //System.out.println("putting back to slot "+putback0+" from 0, and to "+putback1+"from 1");
        if (putback0!=-1) {
            slotShiftClick(0);
        }
        if (putback1!=-1) {
            slotShiftClick(1);
        }

        this.onMouseClick(null, /* slot*/ 0, /* clickData*/ 99, SlotActionType.SWAP);
    }

    /**
     * @param slot - the number of the (trading) slot that should receive items
     * @param stack - what the trading slot should receive
     * @return the number of the inventory slot into which these items should be put back
     * after the transaction. May be -1 if nothing needs to be put back.
     */
    private int fillSlot(int slot, ItemStack stack) {
        int remaining=stack.getCount();
        if(remaining == 0) return -1;
        for (int i=handler.slots.size()-36; i<handler.slots.size(); i++) {
            ItemStack invstack=handler.getSlot(i).getStack();
            if (invstack==null)
                continue;
            boolean needPutBack=false;
            if (areItemStacksMergable(stack, invstack)) {
                if (stack.getCount()+invstack.getCount() > stack.getMaxCount()){
                    needPutBack=true;
                    //System.out.println("need put back");
                }

                remaining-=invstack.getCount();
                //System.out.println("taking "+invstack.getCount()+" items from slot # "+i+", remaining is now "+remaining);
                slotClick(i);
                slotClick(slot);
            }
            if (needPutBack) {
                slotClick(i);
            }
            if (remaining<=0)
                return remaining<0 ? i : -1;
        }
        // We should not be able to arrive here, since hasEnoughItemsInInventory should have been
        // called before fillSlot. But if we do, something went wrong; in this case better do a bit less.
        return -1;
    }


    private boolean areItemStacksMergable(ItemStack a, ItemStack b) {
        if (a==null || b==null)
            return false;
        if (a.getItem() == b.getItem()
        &&  (!a.isDamageable() || a.getDamage()==b.getDamage())
        &&   ItemStack.areTagsEqual(a, b))
            return true;
        return false;
    }

    private void getslot(int slot, ItemStack stack, int... forbidden) {
        slotShiftClick(slot);


        // When looking for an empty slot, don't take one that we want to put some input back to.
        for (int i=handler.slots.size()-36; i<handler.slots.size(); i++) {
            boolean isForbidden=false;
            for (int f:forbidden) {
                if (i==f)
                    isForbidden=true;
            }
            if (isForbidden)
                continue;
            ItemStack invstack=handler.getSlot(i).getStack();
            if (invstack==null || invstack.isEmpty()) {
                slotClick(i);
                // System.out.println("putting result into empty slot "+i);
                return;
            }
        }
    }

    public void slotClick(int slot) {
        //System.out.println("Clicking slot "+slot);
        this.onMouseClick(null, slot, 0, SlotActionType.PICKUP);
    }

    private void slotShiftClick(int slot){
        //System.out.println("Shift clicking slot "+slot);
        this.onMouseClick(null, slot, 0, SlotActionType.QUICK_MOVE);
    }
}
