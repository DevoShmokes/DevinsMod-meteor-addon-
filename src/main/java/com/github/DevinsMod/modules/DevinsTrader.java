package com.github.DevinsMod.modules;

import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalNear;
import com.github.DevinsMod.DevinsAddon;
import com.github.DevinsMod.events.RotationRequestCompletedEvent;
import com.github.DevinsMod.tracker.RotationManager;
import com.github.DevinsMod.utils.RotationRequest;
import com.github.DevinsMod.utils.Utils;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.MerchantScreen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import net.minecraft.village.VillagerProfession;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;


public class
DevinsTrader extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> useBaritone = sgGeneral.add(new BoolSetting.Builder()
        .name("use-baritone")
        .description("Automatically pathfind to villagers using Baritone.")
        .defaultValue(false)
        .build()
    );
    private final Setting<Boolean> debugChat = sgGeneral.add(new BoolSetting.Builder()
        .name("debug-chat")
        .description("Enable verbose debug messages in chat.")
        .defaultValue(false)
        .build()
    );
    private final Setting<Profession> targetProfession = sgGeneral.add(new EnumSetting.Builder<Profession>()
        .name("profession")
        .description("Which villager profession to path to when using Baritone.")
        .defaultValue(Profession.CLERIC)
        .build()
    );
    private final Setting<Boolean> waitForBaritoneFinish = sgGeneral.add(new BoolSetting.Builder()
        .name("wait-for-baritone-finish")
        .description("When using Baritone, wait until it finishes pathing before interacting.")
        .defaultValue(false)
        .build()
    );
    private final Setting<Boolean> enableBuy = sgGeneral.add(new BoolSetting.Builder()
        .name("enable-buy")
        .description("Enable buying items from villagers.")
        .defaultValue(true)
        .build()
    );
    private final Setting<String> buyItem = sgGeneral.add(new StringSetting.Builder()
        .name("buy-item")
        .description("Item name to buy.")
        .defaultValue("experience_bottle")
        .build()
    );
    private final Setting<Boolean> enableNudgeLogic = sgGeneral.add(new BoolSetting.Builder()
        .name("enable-nudge-logic")
        .description("Toggle Baritone’s nudge-when-stuck logic on or off")
        .defaultValue(true)
        .build()
    );
    private final Setting<Double> interactionDelay = sgGeneral.add(new DoubleSetting.Builder()
        .name("interaction-delay")
        .description("Delay in ticks after a villager interaction before the next.")
        .defaultValue(0.8)
        .min(0)
        .max(20)
        .build()
    );
    private final Setting<Double> maxInteractionsPerTick = sgGeneral.add(new DoubleSetting.Builder()
        .name("max-interactions-per-tick")
        .description("Max villagers to interact with per tick.")
        .defaultValue(1.0)
        .min(1)
        .max(10)
        .build()
    );
    private final Setting<Integer> scanMaxRange = sgGeneral.add(new IntSetting.Builder()
        .name("scan-max-range")
        .description("Maximum horizontal blocks to search villagers.")
        .defaultValue(60)
        .min(1).max(256)
        .build()
    );
    private final Setting<Double> interactionRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("interaction-range")
        .description("Max distance to interact with villagers.")
        .defaultValue(3.0)
        .min(0.1)
        .max(6.0)
        .build()
    );
    private final Setting<Integer> maxSpendPerTrade = sgGeneral.add(new IntSetting.Builder()
        .name("max-spend-per-trade")
        .description("Skip any trade costing more emeralds than this")
        .defaultValue(5)
        .min(1)
        .max(64)
        .build()
    );
    private final Setting<Integer> minEmeralds = sgGeneral.add(new IntSetting.Builder()
        .name("min-emeralds")
        .description("Disable if you have fewer than this many emeralds.")
        .defaultValue(4)
        .min(0)
        .max(999)
        .build()
    );
    private final Setting<Integer> chestX = sgGeneral.add(new IntSetting.Builder()
        .name("restock-chest-x")
        .description("X coord of your restock chest")
        .defaultValue(0)
        .build()
    );
    private final Setting<Integer> chestY = sgGeneral.add(new IntSetting.Builder()
        .name("restock-chest-y")
        .description("Y coord of your restock chest")
        .defaultValue(64)
        .build()
    );
    private final Setting<Integer> chestZ = sgGeneral.add(new IntSetting.Builder()
        .name("restock-chest-z")
        .description("Z coord of your restock chest")
        .defaultValue(0)
        .build()
    );
    private final Setting<Integer> exportChestX = sgGeneral.add(new IntSetting.Builder()
        .name("export-chest-x")
        .description("X coord of your export chest")
        .defaultValue(1)
        .build()
    );
    private final Setting<Integer> exportChestY = sgGeneral.add(new IntSetting.Builder()
        .name("export-chest-y")
        .description("Y coord of your export chest")
        .defaultValue(64)
        .build()
    );
    private final Setting<Integer> exportChestZ = sgGeneral.add(new IntSetting.Builder()
        .name("export-chest-z")
        .description("Z coord of your export chest")
        .defaultValue(1)
        .build()
    );
    private final Setting<Integer> exportThreshold = sgGeneral.add(new IntSetting.Builder()
        .name("export-threshold")
        .description("When you have this many buyItems, export to chest")
        .defaultValue(1000)
        .min(1)
        .build()
    );
    private final Map<Integer, Integer> interactedVillagers = new HashMap<>();
    private final Set<Integer> tradedVillagersPermanent = new HashSet<>();
    private final int villagerCooldown = 200; // ticks
    private final RotationRequest rotationRequest = new RotationRequest(100, false);
    private final Random random = new Random();
    private final int nudgeDuration = 150;
    private double tradeCooldown = 0, interactionCooldown = 0;
    private int interactionsThisTick = 0;
    private Entity currentVillager = null;
    private int stuckTicks = 0;
    private BlockPos lastGoalPos = null;
    private int stuckAttempts = 0;
    private int currentYLevel = 0;
    private Vec3d lastPlayerPos = Vec3d.ZERO;
    private int nudgeTicksRemaining = 0;
    private int nudgeElapsed = 0;
    private boolean isRestocking = false;
    private boolean hasOpenedChest = false;
    private boolean isExporting = false;
    private boolean hasOpenedExportChest = false;
    private int tradeScreenOpenTicks = 0;
    private boolean awaitingExportChestOpen = false;
    private int exportChestOpenTicks = 0;
    private int restockChestOpenTicks = 0;
    private Integer firstVillagerId = null;
    private Vec3d firstVillagerPos = null;
    public DevinsTrader() {
        super(DevinsAddon.CATEGORY, "DevinsTrader", "Trades with villagers using silent rotation logic (start with a stack of Emerald Blocks in inv).");
    }

    private void log(String message) {
        if (debugChat.get()) ChatUtils.info(message);
    }

    @Override
    public void onActivate() {
        interactedVillagers.clear();
        tradedVillagersPermanent.clear();
        tradeCooldown = interactionCooldown = 0;
        interactionsThisTick = 0;
        currentVillager = null;
        currentYLevel = mc.player.getBlockPos().getY();
        rotationRequest.setActive(false);
        lastPlayerPos = mc.player.getPos();
        lastGoalPos = null;
        stuckTicks = 0;
        stuckAttempts = 0;
        nudgeTicksRemaining = 0;
        RotationManager.requests.add(rotationRequest);

        firstVillagerId = null;
        firstVillagerPos = null;

        if (useBaritone.get()) {
            var baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
            BaritoneAPI.getSettings().allowBreak.value = false;
            BaritoneAPI.getSettings().allowPlace.value = false;
            BaritoneAPI.getSettings().allowParkour.value = true;
            baritone.getCustomGoalProcess().onLostControl();
        }

        log("DevinsTrader activated.");
    }

    @Override
    public void onDeactivate() {
        interactedVillagers.clear();
        tradedVillagersPermanent.clear();
        tradeCooldown = interactionCooldown = 0;
        interactionsThisTick = 0;
        currentVillager = null;
        rotationRequest.setActive(false);
        RotationManager.requests.remove(rotationRequest);

        if (useBaritone.get()) {
            var baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
            baritone.getCustomGoalProcess().onLostControl();
        }

        if (mc.currentScreen instanceof MerchantScreen) {
            mc.player.closeHandledScreen();
        }

        ChatUtils.info("DevinsTrader deactivated, data reset.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (debugChat.get()) {
            int emeralds = mc.player.getInventory().main.stream()
                .filter(s -> s.getItem() == Items.EMERALD).mapToInt(s -> s.getCount()).sum();
            int blocks = mc.player.getInventory().main.stream()
                .filter(s -> s.getItem() == Items.EMERALD_BLOCK).mapToInt(s -> s.getCount()).sum();
            log("onTick: exporting=" + isExporting
                + " restocking=" + isRestocking
                + " emeralds=" + emeralds
                + " blocks=" + blocks);
        }

        if (!useBaritone.get()) {
            isExporting = false;
            isRestocking = false;
        }

        int have = mc.player.getInventory().main.stream()
            .filter(s -> {
                Item it = s.getItem();
                Item target = tryGetItem(buyItem.get().toLowerCase(Locale.ROOT).trim());
                return it == target;
            })
            .mapToInt(s -> s.getCount())
            .sum();

        if (mc.currentScreen instanceof MerchantScreen) {
            tradeScreenOpenTicks++;
            if (tradeScreenOpenTicks > 30) {
                mc.player.closeHandledScreen();
                ChatUtils.error("Trade GUI open too long, closing to recover.");
                tradeScreenOpenTicks = 0;
            } else {
                handleMerchantScreen((MerchantScreen) mc.currentScreen);
            }
            return;
        } else {
            tradeScreenOpenTicks = 0;
        }

        if (useBaritone.get()) {
            if (have >= exportThreshold.get() && !isExporting) {
                startExport();
                return;
            }
            if (isExporting) {
                handleExportChestScreen();
                return;
            }
        }

        int emeraldCount = mc.player.getInventory().main.stream()
            .filter(s -> s.getItem() == Items.EMERALD)
            .mapToInt(s -> s.getCount())
            .sum();
        if (emeraldCount < minEmeralds.get()) {
            autoCraftOneEmeraldBlockPerTick();
            return;
        }

        if (useBaritone.get()) {
            int blockCount = mc.player.getInventory().main.stream()
                .filter(s -> s.getItem() == Items.EMERALD_BLOCK)
                .mapToInt(s -> s.getCount())
                .sum();
            if (blockCount == 0 && !isRestocking) {
                startRestock();
                return;
            }
            if (isRestocking) {
                handleRestockChestScreen();
                return;
            }
        }

        interactionsThisTick = 0;

        if (useBaritone.get() && enableNudgeLogic.get()) {
            var bar = BaritoneAPI.getProvider().getPrimaryBaritone();
            if (!bar.getCustomGoalProcess().isActive()) checkBaritoneStuck();
        }

        if (tradeCooldown > 0) {
            tradeCooldown--;
            return;
        }
        if (interactionCooldown > 0) {
            interactionCooldown--;
            return;
        }
        interactedVillagers.replaceAll((id, cd) -> cd - 1);
        interactedVillagers.entrySet().removeIf(e -> e.getValue() <= 0);

        if (mc.currentScreen instanceof MerchantScreen merch) {
            handleMerchantScreen(merch);
            return;
        }

        boolean baritoneBusy = useBaritone.get()
            && waitForBaritoneFinish.get()
            && BaritoneAPI.getProvider().getPrimaryBaritone()
            .getCustomGoalProcess().isActive();

        if (!baritoneBusy) {
            interactAura();
        } else if (debugChat.get()) {
            log("Waiting for Baritone to finish before interacting");
        }

        if (useBaritone.get() && enableNudgeLogic.get()) {
            checkBaritoneStuck();
        }
    }

    private void interactAura() {
        double interactSq = interactionRange.get() * interactionRange.get();

        if (useBaritone.get()) {
            VillagerProfession prof = Registries.VILLAGER_PROFESSION.get(
                Identifier.tryParse("minecraft:" + targetProfession.get().getId())
            );
            if (prof == null) {
                if (debugChat.get())
                    log("Unknown profession: " + targetProfession.get().getId() + " — skipping Baritone pathing.");
                return;
            }

            List<VillagerEntity> all = StreamSupport.stream(mc.world.getEntities().spliterator(), false)
                .filter(e -> e instanceof VillagerEntity)
                .map(e -> (VillagerEntity) e)
                .filter(v -> v.getVillagerData().getProfession() == prof)
                .filter(v -> !tradedVillagersPermanent.contains(v.getId()))
                .filter(v -> !interactedVillagers.containsKey(v.getId()))
                .filter(v -> mc.player.squaredDistanceTo(v) <= scanMaxRange.get() * scanMaxRange.get())
                .collect(Collectors.toList());

            if (all.isEmpty()) {
                restartTradingCycle();
                return;
            }

            int bestY = all.stream()
                .mapToInt(v -> v.getBlockPos().getY())
                .boxed()
                .min(Comparator.comparingInt(y -> Math.abs(y - currentYLevel)))
                .orElse(currentYLevel);
            currentYLevel = bestY;

            List<VillagerEntity> levelList = all.stream()
                .filter(v -> v.getBlockPos().getY() == bestY)
                .sorted(Comparator.comparingDouble(v -> mc.player.squaredDistanceTo(v)))
                .collect(Collectors.toList());
            if (levelList.isEmpty()) return;

            VillagerEntity target = levelList.get(0);
            BlockPos pos = new BlockPos(
                MathHelper.floor(target.getX()),
                MathHelper.floor(target.getY()),
                MathHelper.floor(target.getZ())
            );
            double horizSq = mc.player.squaredDistanceTo(target);

            var baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
            if (horizSq > interactSq) {
                if (debugChat.get()) log("Baritone → path to " + pos);
                Vec3d center = new Vec3d(target.getX(), target.getY(), target.getZ());
                baritone.getCustomGoalProcess().onLostControl();
                baritone.getCustomGoalProcess().setGoalAndPath(new Goal() {
                    @Override
                    public boolean isInGoal(int x, int y, int z) {
                        return new Vec3d(x + .5, y, z + .5).distanceTo(center) <= interactionRange.get();
                    }

                    @Override
                    public double heuristic(int x, int y, int z) {
                        return new Vec3d(x + .5, y, z + .5).distanceTo(center);
                    }
                });
                lastGoalPos = pos;
            } else {
                baritone.getCustomGoalProcess().onLostControl();
                interactWithVillagerEntity(target);
                lastGoalPos = null;
            }
            return;
        }

        List<Entity> villagers = StreamSupport.stream(mc.world.getEntities().spliterator(), false)
            .filter(e -> e instanceof VillagerEntity)
            .filter(e -> mc.player.squaredDistanceTo(e) <= interactSq)
            .filter(e -> !interactedVillagers.containsKey(e.getId()))
            .sorted(Comparator.comparingDouble(e -> mc.player.squaredDistanceTo(e)))
            .collect(Collectors.toList());

        if (villagers.isEmpty()) {
            if (useBaritone.get()) restartTradingCycle();
            return;
        }

        for (Entity v : villagers) {
            if (interactionsThisTick >= maxInteractionsPerTick.get()) break;
            interactWithVillagerEntity(v);
        }
    }

    private void startExport() {
        BlockPos pos = new BlockPos(exportChestX.get(), exportChestY.get(), exportChestZ.get());
        if (!useBaritone.get()) {
            ChatUtils.error("Export requires Baritone!");
            return;
        }
        var bar = BaritoneAPI.getProvider().getPrimaryBaritone();
        bar.getCustomGoalProcess().onLostControl();
        bar.getCustomGoalProcess().setGoalAndPath(new GoalNear(pos, 1));
        isExporting = true;
        hasOpenedExportChest = false;
        awaitingExportChestOpen = false;
        ChatUtils.info("Exporting → walking to chest at " + pos);
    }

    private void restartTradingCycle() {
        if (firstVillagerId == null) return;

        ChatUtils.info("✅ All villagers traded. Restarting cycle with first villager (ID " + firstVillagerId + ").");

        tradedVillagersPermanent.clear();
        interactedVillagers.clear();

        Entity e = mc.world.getEntityById(firstVillagerId);
        if (e instanceof VillagerEntity first) {
            BlockPos pos = new BlockPos(
                MathHelper.floor(first.getX()),
                MathHelper.floor(first.getY()),
                MathHelper.floor(first.getZ())
            );

            if (useBaritone.get()) {
                var baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
                baritone.getCustomGoalProcess().onLostControl();
                baritone.getCustomGoalProcess().setGoalAndPath(new GoalNear(pos, (int) Math.ceil(interactionRange.get())));
                ChatUtils.info("→ Pathing back to first villager at " + pos);
            } else {
                log("First villager at " + pos + ". Will interact when in range.");
            }
        } else {
            ChatUtils.error("Could not find first villager in world (ID: " + firstVillagerId + ").");
        }
    }

    private void interactWithVillagerEntity(Entity villager) {
        currentVillager = villager;
        rotationRequest.setTarget(villager);
        rotationRequest.setActive(true);
        log("Requesting silent rotation for villager: " + villager.getName().getString());
    }

    private void handleExportChestScreen() {
        var bar = BaritoneAPI.getProvider().getPrimaryBaritone();
        if (bar.getCustomGoalProcess().isActive()) return;

        if (mc.player.currentScreenHandler instanceof GenericContainerScreenHandler) {
            exportChestOpenTicks++;
            if (exportChestOpenTicks > 30) {
                mc.player.closeHandledScreen();
                ChatUtils.error("Export chest GUI open too long, closing to recover.");
                exportChestOpenTicks = 0;
                mc.setScreen(null);
                return;
            }
        } else {
            exportChestOpenTicks = 0;
        }

        BlockPos pos = new BlockPos(exportChestX.get(), exportChestY.get(), exportChestZ.get());
        Vec3d center = Vec3d.ofCenter(pos);

        if (!hasOpenedExportChest) {
            if (mc.player.squaredDistanceTo(center) <= 5 * 5) {

                if (useBaritone.get()) bar.getCustomGoalProcess().onLostControl();

                BlockHitResult hit = new BlockHitResult(pos.toCenterPos(), Direction.UP, pos, false);
                mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(
                    PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                    BlockPos.ORIGIN, Direction.DOWN));
                mc.player.networkHandler.sendPacket(new PlayerInteractBlockC2SPacket(Hand.OFF_HAND, hit, 0));
                mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(
                    PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                    BlockPos.ORIGIN, Direction.DOWN
                ));

                hasOpenedExportChest = true;
                ChatUtils.info("Opening export chest…");
            }
            return;
        }

        if (mc.player.currentScreenHandler instanceof GenericContainerScreenHandler chest) {
            Item target = tryGetItem(buyItem.get().toLowerCase(Locale.ROOT).trim());
            int syncId = chest.syncId;
            int deposited = 0;

            for (int i = 5; i < chest.slots.size(); i++) {
                if (chest.slots.get(i).getStack().getItem() == target) {
                    clickSlot(i, SlotActionType.QUICK_MOVE);
                    deposited++;
                }
            }

            mc.player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(syncId));
            mc.setScreen(null);
            isExporting = false;
            ChatUtils.info("Exported " + deposited + " stacks of " + buyItem.get());
        }
    }

    @EventHandler
    private void onRotationRequestComplete(RotationRequestCompletedEvent.Post event) {
        if (event.request != rotationRequest) return;

        if (currentVillager != null && firstVillagerId == null) {
            firstVillagerId = currentVillager.getId();
            firstVillagerPos = currentVillager.getPos();
            log("Recorded first villager ID: " + firstVillagerId);
        }

        // Export chest opening logic
        if (awaitingExportChestOpen) {
            BlockPos pos = new BlockPos(exportChestX.get(), exportChestY.get(), exportChestZ.get());
            BlockHitResult hit = new BlockHitResult(
                pos.toCenterPos(),
                Direction.UP,
                pos,
                false
            );

            mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                BlockPos.ORIGIN, Direction.DOWN
            ));
            mc.player.networkHandler.sendPacket(new PlayerInteractBlockC2SPacket(Hand.OFF_HAND, hit, 0));
            mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                BlockPos.ORIGIN, Direction.DOWN
            ));

            hasOpenedExportChest = true;
            awaitingExportChestOpen = false;
            ChatUtils.info("Opening export chest…");
            return;
        }

        if (currentVillager != null) {
            mc.player.networkHandler.sendPacket(
                PlayerInteractEntityC2SPacket.interactAt(
                    currentVillager,
                    mc.player.isSneaking(),
                    Hand.MAIN_HAND,
                    Utils.getClosestPointOnBox(mc.player.getEyePos(), currentVillager.getBoundingBox())
                )
            );
            mc.player.networkHandler.sendPacket(
                PlayerInteractEntityC2SPacket.interact(
                    currentVillager,
                    mc.player.isSneaking(),
                    Hand.MAIN_HAND
                )
            );

            if (useBaritone.get()) {
                var baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
                baritone.getCustomGoalProcess().onLostControl();
                stuckTicks = 0;
                stuckAttempts = 0;
                nudgeTicksRemaining = 0;
                nudgeElapsed = 0;
                lastGoalPos = null;
            }

            tradedVillagersPermanent.add(currentVillager.getId());
            interactedVillagers.put(currentVillager.getId(), villagerCooldown);
            interactionCooldown = 10;
            interactionsThisTick++;
            log("Interaction complete: " + currentVillager.getName().getString());

            rotationRequest.setActive(false);
            currentVillager = null;
        }
    }

    private void handleMerchantScreen(MerchantScreen merch) {
        ScreenHandler sh = mc.player.currentScreenHandler;
        if (!(sh instanceof MerchantScreenHandler handler)) {
            mc.player.closeHandledScreen();
            return;
        }

        TradeOfferList offers = handler.getRecipes();
        if (offers == null || offers.isEmpty()) {
            mc.player.closeHandledScreen();
            interactionCooldown = 10;
            return;
        }

        Item targetItem = tryGetItem(buyItem.get().toLowerCase(Locale.ROOT).trim());
        if (targetItem == null) {
            ChatUtils.error("Invalid buy-item: " + buyItem.get());
            mc.player.closeHandledScreen();
            return;
        }

        TradeOffer chosen = null;
        for (TradeOffer offer : offers) {
            if (offer.getSellItem().getItem() == targetItem && !offer.isDisabled()) {
                chosen = offer;
                break;
            }
        }

        if (chosen == null) {
            if (debugChat.get()) log("No trade for " + targetItem.getTranslationKey());
            mc.player.closeHandledScreen();
            return;
        }

        ItemStack firstBuy = chosen.getDisplayedFirstBuyItem();
        ItemStack secondBuy = chosen.getDisplayedSecondBuyItem();
        int cost = firstBuy.getCount() + secondBuy.getCount();

        if (debugChat.get()) {
            log("Target trade costs " + cost +
                " emeralds (first slot=" + firstBuy.getCount() +
                (secondBuy.isEmpty() ? "" : ", second slot=" + secondBuy.getCount()) +
                ")");
        }
        if (cost - 1 >= maxSpendPerTrade.get()) {
            if (debugChat.get()) log("Skipping — cost exceeds maxSpendPerTrade");
            mc.player.closeHandledScreen();
            return;
        }

        int emeraldCount = mc.player.getInventory().main.stream()
            .filter(s -> s.getItem() == Items.EMERALD)
            .mapToInt(s -> s.getCount())
            .sum();

        if (emeraldCount < cost) {
            if (debugChat.get()) log("Skipping — not enough emeralds (have " + emeraldCount + ")");
            mc.player.closeHandledScreen();
            return;
        }

        int index = offers.indexOf(chosen);
        mc.getNetworkHandler().sendPacket(new SelectMerchantTradeC2SPacket(index));
        clickSlot(2, SlotActionType.QUICK_MOVE);
        int before = countBuyItem();
        int after = countBuyItem();
        if (after <= before) {
            if (debugChat.get()) log("Trade didn’t register — retrying");
            mc.getNetworkHandler().sendPacket(new SelectMerchantTradeC2SPacket(index));
            clickSlot(2, SlotActionType.QUICK_MOVE);
        }

        if (useBaritone.get()) {
            BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().onLostControl();
        }
        mc.player.closeHandledScreen();
    }
    private void clickSlot(int slotId, SlotActionType actionType) {
        try {
            mc.interactionManager.clickSlot(
                mc.player.currentScreenHandler.syncId,
                slotId,
                0,
                actionType,
                mc.player
            );
            log("Clicked slot " + slotId + " with " + actionType);
        } catch (Exception e) {
            ChatUtils.error("Error Crafting Emeralds: " + e.getMessage());
        }
    }

    private Item tryGetItem(String itemName) {
        Identifier id = Identifier.tryParse("minecraft:" + itemName);
        if (id == null || !Registries.ITEM.containsId(id)) return null;
        return Registries.ITEM.get(id);
    }

    private int countBuyItem() {
        Item itemToBuy = tryGetItem(buyItem.get().toLowerCase(Locale.ROOT).trim());
        if (itemToBuy == null) return 0;
        return mc.player.getInventory().main.stream()
            .filter(s -> s.getItem() == itemToBuy)
            .mapToInt(s -> s.getCount())
            .sum();
    }

    private void startRestock() {
        BlockPos chestPos = new BlockPos(chestX.get(), chestY.get(), chestZ.get());
        if (!useBaritone.get()) {
            ChatUtils.error("Restock requires Baritone enabled!");
            return;
        }
        var bar = BaritoneAPI.getProvider().getPrimaryBaritone();
        bar.getCustomGoalProcess().onLostControl();
        bar.getCustomGoalProcess().setGoalAndPath(new GoalNear(chestPos, 1));
        isRestocking = true;
        hasOpenedChest = false;
        ChatUtils.info("Restocking → walking to chest at " + chestPos);
    }

    private void handleRestockChestScreen() {
        var bar = BaritoneAPI.getProvider().getPrimaryBaritone();

        if (bar.getCustomGoalProcess().isActive()) return;
        if (mc.player.currentScreenHandler instanceof GenericContainerScreenHandler) {
            restockChestOpenTicks++;
            if (restockChestOpenTicks > 30) {
                mc.player.closeHandledScreen();
                ChatUtils.error("Restock chest GUI open too long, closing to recover.");
                restockChestOpenTicks = 0;
                mc.setScreen(null);
                return;
            }
        } else {
            restockChestOpenTicks = 0;
        }
        BlockPos pos = new BlockPos(chestX.get(), chestY.get(), chestZ.get());
        Vec3d chestCenter = Vec3d.ofCenter(pos);
        if (!hasOpenedChest) {
            if (mc.player.squaredDistanceTo(chestCenter) <= 3 * 3) {
                if (useBaritone.get()) bar.getCustomGoalProcess().onLostControl();

                BlockHitResult hit = new BlockHitResult(pos.toCenterPos(), Direction.UP, pos, false);

                mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(
                    PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN
                ));
                mc.player.networkHandler.sendPacket(new PlayerInteractBlockC2SPacket(Hand.OFF_HAND, hit, 0));
                mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(
                    PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN
                ));

                hasOpenedChest = true;
                ChatUtils.info("Opening restock chest…");
            }
            return;
        }

        if (!(mc.player.currentScreenHandler instanceof GenericContainerScreenHandler chest)) return;

        int rows = chest.getRows();
        int chestSlots = rows * 9;


        int availableStacks = 0;
        for (int i = 0; i < chestSlots; i++) {
            if (chest.slots.get(i).getStack().getItem() == Items.EMERALD_BLOCK) availableStacks++;
        }
        if (debugChat.get()) ChatUtils.info("Restock chest contains " + availableStacks + " stacks of Emerald Blocks");

        if (availableStacks == 0) {
            for (int i = 0; i < chestSlots; i++) {
                if (chest.slots.get(i).getStack().getItem() == Items.EMERALD_BLOCK) availableStacks++;
            }
            int syncId = chest.syncId;
            mc.player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(syncId));
            ChatUtils.error("❌ No Emerald Blocks left in restock chest – disabling DevinsTrader.");
            toggle();
            return;
        }

        int taken = 0;
        int syncId = chest.syncId;
        for (int i = 0; i < chestSlots && taken < 4; i++) {
            if (chest.slots.get(i).getStack().getItem() == Items.EMERALD_BLOCK) {
                clickSlot(i, SlotActionType.QUICK_MOVE);
                taken++;
            }
        }

        mc.player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(syncId));
        mc.setScreen(null);
        isRestocking = false;
        ChatUtils.info("✅ Restocked " + taken + " stacks of Emerald Blocks.");
    }

    private void autoCraftOneEmeraldBlockPerTick() {
        if (!(mc.player.currentScreenHandler instanceof PlayerScreenHandler handler)) return;

        int emeraldCount = mc.player.getInventory().main.stream()
            .filter(s -> s.getItem() == Items.EMERALD)
            .mapToInt(s -> s.getCount())
            .sum();
        if (emeraldCount >= minEmeralds.get()) return;

        int syncId = handler.syncId;

        for (int i = 5; i < handler.slots.size(); i++) {
            var slot = handler.slots.get(i);
            var stack = slot.getStack();
            if (stack.getItem() != Items.EMERALD_BLOCK) continue;

            clickSlot(i, SlotActionType.PICKUP);
            clickSlot(1, SlotActionType.PICKUP);

            clickSlot(0, SlotActionType.QUICK_MOVE);

            clickSlot(1, SlotActionType.PICKUP);
            clickSlot(i, SlotActionType.PICKUP);

            ChatUtils.info("Auto-crafted 1 Emerald Block → 9 Emeralds");
            break;
        }
    }

    private void checkBaritoneStuck() {
        var baritone = BaritoneAPI.getProvider().getPrimaryBaritone();

        if (lastGoalPos == null) {
            nudgeTicksRemaining = 0;
            nudgeElapsed = 0;
            stuckTicks = 0;
            stuckAttempts = 0;
            lastPlayerPos = mc.player.getPos();
            return;
        }

        if (nudgeTicksRemaining > 0) {
            nudgeElapsed++;

            if (nudgeElapsed > 500) {
                Vec3d playerPos = mc.player.getPos();
                Vec3d goalCenter = new Vec3d(
                    lastGoalPos.getX() + 0.5,
                    lastGoalPos.getY(),
                    lastGoalPos.getZ() + 0.5
                );
                Vec3d dirToGoal = goalCenter.subtract(playerPos).normalize();
                Vec3d oppositeVec = goalCenter.add(dirToGoal.multiply(scanMaxRange.get()));
                BlockPos opposite = new BlockPos(
                    MathHelper.floor(oppositeVec.x),
                    MathHelper.floor(oppositeVec.y),
                    MathHelper.floor(oppositeVec.z)
                );

                baritone.getCustomGoalProcess().onLostControl();
                baritone.getCustomGoalProcess().setGoalAndPath(
                    new GoalNear(opposite, (int) Math.ceil(interactionRange.get()))
                );

                lastGoalPos = opposite;
                nudgeTicksRemaining = 0;
                log("Nudge >500 ticks; rerouting to opposite side at " + opposite);
                return;
            }

            Vec3d goalCenter = new Vec3d(
                lastGoalPos.getX() + 0.5,
                lastGoalPos.getY(),
                lastGoalPos.getZ() + 0.5
            );
            Vec3d dir = goalCenter.subtract(mc.player.getPos()).normalize();
            float walkSpeed = mc.player.getAbilities().getWalkSpeed();
            log("Nudge active (" + nudgeTicksRemaining + " ticks, elapsed=" + nudgeElapsed + ")");
            mc.player.setVelocity(dir.x * walkSpeed, mc.player.getVelocity().y, dir.z * walkSpeed);
            nudgeTicksRemaining--;
            lastPlayerPos = mc.player.getPos();
            return;
        }

        if (!useBaritone.get() || lastGoalPos == null) {
            stuckTicks = 0;
            stuckAttempts = 0;
            lastPlayerPos = mc.player.getPos();
            return;
        }

        if (interactionsThisTick > 0
            || mc.player.getPos().distanceTo(lastPlayerPos) > 0.1
        ) {
            stuckTicks = 0;
            stuckAttempts = 0;
            lastPlayerPos = mc.player.getPos();
            return;
        }

        stuckTicks++;
        if (stuckTicks < 60) return;

        stuckTicks = 0;
        stuckAttempts++;
        if (stuckAttempts < 2) {
            nudgeTicksRemaining = nudgeDuration;
            nudgeElapsed = 0;
        }
    }

    public enum Profession {
        ARMORER("armorer"),
        BUTCHER("butcher"),
        CARTOGRAPHER("cartographer"),
        CLERIC("cleric"),
        FARMER("farmer"),
        FISHERMAN("fisherman"),
        FLETCHER("fletcher"),
        LEATHERWORKER("leatherworker"),
        LIBRARIAN("librarian"),
        MASON("mason"),
        SHEPHERD("shepherd"),
        TOOLSMITH("toolsmith");

        private final String id;

        Profession(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }

}
