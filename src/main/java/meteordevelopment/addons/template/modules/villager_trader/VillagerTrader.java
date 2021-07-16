/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client/).
 * Copyright (c) 2021 Meteor Development.
 */

package meteordevelopment.addons.template.modules.villager_trader;

import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.misc.Pool;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.world.BlockIterator;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.List;

public class VillagerTrader extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgDelay = settings.createGroup("Delay");
    private final SettingGroup sgXPFarm = settings.createGroup("XP Farm");

    public VillagerTrader() {
        super(Categories.Misc, "Villager Trader", "Speeds up villager trading.");
    }

    private final Setting<Double> wallsRange = sgGeneral.add(new DoubleSetting.Builder().name("walls-range").description("The maximum range the villager can be traded through walls.").defaultValue(3).min(0).max(5).sliderMax(5).build());
    private final Setting<Double> tradeRange = sgGeneral.add(new DoubleSetting.Builder().name("trade-range").description("The maximum range the villager can be traded.").defaultValue(3).min(0).max(5).sliderMax(5).build());

    private final Setting<Object2BooleanMap<EntityType<?>>> entities = sgGeneral.add(new EntityTypeListSetting.Builder().name("just-tick-villager").description("Entities to trade with.").defaultValue(new Object2BooleanOpenHashMap<>(0)).onlyAttackable().build());
    private final Setting<SortPriority> priority = sgGeneral.add(new EnumSetting.Builder<SortPriority>().name("priority").description("How to filter targets within range.").defaultValue(SortPriority.LowestDistance).build());
    private final Setting<Integer> tradeDelay = sgDelay.add(new IntSetting.Builder().name("trade-delay").description("How fast the aura trades each villager in ticks.").defaultValue(5).min(0).sliderMax(50).build());
    private final Setting<Integer> retradeDelay = sgDelay.add(new IntSetting.Builder().name("cooldown").description("How long in seconds it takes to trade a villager again.").defaultValue(60).min(0).sliderMax(300).build());
    private final Setting<Boolean> ignoreWalls = sgGeneral.add(new BoolSetting.Builder().name("ignore-walls").description("Whether or not to trade the villager through a wall.").defaultValue(true).build());
    private final Setting<Boolean> presetTrade = sgGeneral.add(new BoolSetting.Builder().name("auto-trade").description("Enables automatic trading when interacting with a villager").defaultValue(true).build());

    private final Setting<List<Item>> trades = sgGeneral.add(new ItemListSetting.Builder()
        .name("selected-trades")
        .description("The items you wish to auto trade for.")
        .defaultValue(new ArrayList<>(0))
        .build()
    );
    private final Setting<Boolean> xpFarmEnabled = sgXPFarm.add(new BoolSetting.Builder()
        .name("xp-bottle-farm")
        .description("Enables xp bottle farm capabilities.")
        .defaultValue(false)
        .build()
    );

    private final Setting<List<Block>> storageDevice = sgXPFarm.add(new BlockListSetting.Builder()
        .name("storage-device")
        .description("Choose regular chest if you don't know.")
        .visible(xpFarmEnabled::get)
        .defaultValue(new ArrayList<>(0))
        .build()
    );

    private final Setting<Double> chestRange = sgXPFarm.add(new DoubleSetting.Builder()
        .name("chest-range")
        .description("The range to open chests.")
        .visible(xpFarmEnabled::get)
        .defaultValue(3)
        .min(0)
        .max(5)
        .build()
    );

    private final List<Entity> entityList = new ArrayList<>();
    private final List<Entity> alreadyTradedVillagers = new ArrayList<>();

    private final Pool<BlockPos.Mutable> blockPosPool = new Pool<>(BlockPos.Mutable::new);
    private final List<BlockPos.Mutable> blocks = new ArrayList<>();
    private final List<BlockPos.Mutable> alreadyOpenedChests = new ArrayList<>();

    private int hitDelayTimer;
    private int tradeRefreshTimer;

    private final BlockPos.Mutable lastBlockPos = new BlockPos.Mutable();
    private int noBlockTimer;

    public ArrayList<Item> getWantedTradeItems(){
        ArrayList<Item> wantedTrades = new ArrayList<>();
        for(Item item : this.trades.get()){
            wantedTrades.add(item);
            System.out.println("Adding item to wanted trades: " + item);
        }
        return wantedTrades;
    }

    public BetterGuiMerchant initVillagerTrader(MerchantScreenHandler handler, PlayerInventory inv, Text title){
        ArrayList<Item> wantedTrades = this.getWantedTradeItems();
        return new BetterGuiMerchant(handler, inv, title, wantedTrades);
    }

    public boolean isPresetTradeTicked(){
        return presetTrade.get();
    }

    @Override
    public void onActivate() {
        noBlockTimer = 0;
    }

    @Override
    public void onDeactivate() {
        hitDelayTimer = 0;
        tradeRefreshTimer = 0;
        noBlockTimer = 0;
        alreadyTradedVillagers.clear();
        alreadyOpenedChests.clear();
        entityList.clear();
    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        if(!mc.player.isAlive() || mc.player.isDead() || PlayerUtils.getGameMode() == GameMode.SPECTATOR || !xpFarmEnabled.get()) return;
        Vec3d pos = mc.player.getPos();
        double range = tradeRange.get();
//        String villager = "Villager";
        TargetUtils.getList(entityList, this::entityCheck, priority.get(), 1);

        if (delayCheck() && entityList.size() > 0) {
            entityList.forEach(this::trade);
        }
        resetTradedVillagers();

        if(xpFarmEnabled.get()){
            double pX = mc.player.getX();
            double pY = mc.player.getY();
            double pZ = mc.player.getZ();
            double rangeSq = Math.pow(chestRange.get(), 2);

            BlockIterator.register((int) Math.ceil(chestRange.get()), (int) Math.ceil(chestRange.get()), (blockPos, blockState) -> {
                // Check for air, unbreakable blocks and distance
                if (!BlockUtils.canBreak(blockPos, blockState) || Utils.squaredDistance(pX, pY, pZ, blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5) > rangeSq) return;

                // Check for selected
                if (!storageDevice.get().contains(blockState.getBlock())) return;

                // Add only one chest
                if(blocks.size() < 1){
                    blocks.add(blockPosPool.get().set(blockPos));
                }

                //System.out.println("item list size: " + blocks.size());


            });
//          Interact with block if found
            BlockIterator.after(() -> {
                // Break
                int count = 0;

                for (var block : blocks) {
                    if (count >= 1 || AlreadyOpened(block)) break;

                    System.out.println("chest pos : " + block.getX() + " " + block.getY() + " " + block.getZ());
                    if (mc.interactionManager != null) {
                        mc.interactionManager.interactBlock(mc.player, mc.world, Hand.MAIN_HAND, new BlockHitResult(new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5), Direction.UP, blocks.get(0), true));
                        mc.player.swingHand(Hand.MAIN_HAND);
                        alreadyOpenedChests.add(block);
                        System.out.println("Stealing from chest");

                    }

                    count++;
                }
                // Clear current storage positions
                for (BlockPos.Mutable blockPos : blocks) blockPosPool.free(blockPos);
                blocks.clear();

            });


        }
    }

    private boolean AlreadyOpened(BlockPos.Mutable block){
        return alreadyOpenedChests.contains(block);
    }

    private boolean AlreadyTraded(Entity target) {
        return alreadyTradedVillagers.contains(target);
    }

    private boolean entityCheck(Entity entity) {
        if (entity.equals(mc.player) || entity.equals(mc.cameraEntity)) return false;
        if ((entity instanceof LivingEntity && ((LivingEntity) entity).isDead()) || !entity.isAlive()) return false;
        if (PlayerUtils.distanceTo(entity) > tradeRange.get()) return false;
        if (!entities.get().getBoolean(entity.getType())) return false;
        if (AlreadyTraded(entity)) return false;
        if (ignoreWalls.get()) {
            if (PlayerUtils.distanceTo(entity) > wallsRange.get()) return false;
        } else {
            if (!PlayerUtils.canSeeEntity(entity)) return false;
        }

        //if(entity.getBlockPos().getY() > pos.getY() + 1) return false;
        //System.out.println("entity type: " + (entity.getType().getName().getString()));
        return !(entity instanceof VillagerEntity) || !((VillagerEntity) entity).isBaby();
    }

    private boolean delayCheck() {
        if (hitDelayTimer >= 0) {
            hitDelayTimer--;
            return false;
        } else {
            hitDelayTimer = tradeDelay.get();
        }
        return true;
    }

    private void resetTradedVillagers(){
        if(tradeRefreshTimer >= 0){
            tradeRefreshTimer--;
            return;
        }else{
            alreadyTradedVillagers.clear();
            alreadyOpenedChests.clear();
            tradeRefreshTimer = retradeDelay.get() * 20; // seconds * tps
        }
    }

    private void trade(Entity target){
        mc.interactionManager.interactEntity(mc.player, target, Hand.MAIN_HAND);
        mc.player.swingHand(Hand.MAIN_HAND);
        alreadyTradedVillagers.add(target);
    }
}
