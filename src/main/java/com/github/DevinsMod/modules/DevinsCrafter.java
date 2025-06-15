package com.github.DevinsMod.modules;

import com.github.DevinsMod.DevinsAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.recipebook.RecipeResultCollection;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.recipe.RecipeDisplayEntry;
import net.minecraft.recipe.display.SlotDisplayContexts;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalNear;
import net.minecraft.item.Item;

import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

public class DevinsCrafter extends Module {
    private enum State {IDLE, FETCH, FETCH2, CRAFT, EXPORT, RETURN}

    private static final long TIMEOUT_MS = 500; // 0.5 seconds

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<List<Item>> fetchItems = sgGeneral.add(
        new ItemListSetting.Builder()
            .name("fetch-items")
            .description("Only these items will be pulled from the farm chest.")
            .defaultValue(Collections.emptyList())
            .build()
    );

    private final Setting<List<Item>> fetchItems2 = sgGeneral.add(
        new ItemListSetting.Builder()
            .name("fetch-items-2")
            .description("Items to pull from the second farm chest.")
            .defaultValue(Collections.emptyList())
            .build()
    );

    private final Setting<List<Item>> targets = sgGeneral.add(
        new ItemListSetting.Builder()
            .name("targets")
            .description("Items to auto-craft.")
            .defaultValue(Collections.emptyList())
            .build()
    );

    private final Setting<List<Item>> exportItems = sgGeneral.add(
        new ItemListSetting.Builder()
            .name("export-items")
            .description("Only these items will be moved into the export chest.")
            .defaultValue(Collections.emptyList())
            .build()
    );

    private final Setting<Boolean> shiftCraft = sgGeneral.add(
        new BoolSetting.Builder()
            .name("shift-craft")
            .description("Use shift-click to craft maximum stack at once.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(
        new IntSetting.Builder()
            .name("delay-ticks")
            .description("Delay between each craft attempt in ticks.")
            .defaultValue(2)
            .min(0)
            .sliderMax(20)
            .build()
    );

    private final Setting<Boolean> antiDesync = sgGeneral.add(
        new BoolSetting.Builder()
            .name("anti-desync")
            .description("Sync inventory each craft attempt to reduce desync.")
            .defaultValue(false)
            .build()
    );

    // ——— NEW: Chest-open rate limit ———
    private final Setting<Integer> chestOpenLimit = sgGeneral.add(
        new IntSetting.Builder()
            .name("chest-open-per-second")
            .description("Maximum number of chest opens per second.")
            .defaultValue(5)
            .min(1)
            .sliderMax(20)
            .build()
    );
    private long chestOpenWindowStart = 0;
    private int chestOpenActions = 0;
    // ——————————————————————————————

    private final Setting<Integer> stacksToFetch = sgGeneral.add(
        new IntSetting.Builder()
            .name("stacks-to-fetch-1")
            .description("How many stacks to take from the first farm chest per cycle.")
            .defaultValue(1)
            .min(1)
            .sliderMax(27)
            .build()
    );

    private final Setting<Integer> stacksToFetch2 = sgGeneral.add(
        new IntSetting.Builder()
            .name("stacks-to-fetch-2")
            .description("How many stacks to take from the second farm chest per cycle.")
            .defaultValue(1)
            .min(1)
            .sliderMax(27)
            .build()
    );

    private final Setting<Boolean> autoMode = sgGeneral.add(
        new BoolSetting.Builder()
            .name("auto-mode")
            .description("Enable full farm→craft→export automation.")
            .defaultValue(false)
            .build()
    );

    private final Setting<BlockPos> farmChestPos = sgGeneral.add(
        new BlockPosSetting.Builder()
            .name("farm-chest-pos")
            .description("Where to pull raw items from.")
            .defaultValue(BlockPos.ORIGIN)
            .build()
    );

    private final Setting<BlockPos> farmChest2Pos = sgGeneral.add(
        new BlockPosSetting.Builder()
            .name("farm-chest-2-pos")
            .description("Where to pull raw items from (second chest).")
            .defaultValue(BlockPos.ORIGIN)
            .build()
    );

    private final Setting<BlockPos> craftingTablePos = sgGeneral.add(
        new BlockPosSetting.Builder()
            .name("crafting-table-pos")
            .description("Where your crafting table is located.")
            .defaultValue(BlockPos.ORIGIN)
            .build()
    );

    private final Setting<BlockPos> exportChestPos = sgGeneral.add(
        new BlockPosSetting.Builder()
            .name("export-chest-pos")
            .description("Where to dump the finished items.")
            .defaultValue(BlockPos.ORIGIN)
            .build()
    );

    private final Setting<Boolean> setFarmHere = sgGeneral.add(
        newPositionSetter("set-farm-here", farmChestPos, "farm chest").build()
    );

    private final Setting<Boolean> setFarm2Here = sgGeneral.add(
        newPositionSetter("set-farm-2-here", farmChest2Pos, "second farm chest").build()
    );

    private final Setting<Boolean> setCraftHere = sgGeneral.add(
        newPositionSetter("set-craft-here", craftingTablePos, "crafting table").build()
    );

    private final Setting<Boolean> setExportHere = sgGeneral.add(
        newPositionSetter("set-export-here", exportChestPos, "export chest").build()
    );

    private final Setting<Boolean> enableFarmChest2 = sgGeneral.add(
        new BoolSetting.Builder()
            .name("enable-farm-chest-2")
            .description("Enable fetching from the second farm chest.")
            .defaultValue(false)
            .build()
    );

    private boolean openedFarmChest = false;
    private boolean openedFarmChest2 = false;
    private boolean openedCraftTable = false;
    private boolean openedExportChest = false;

    private int nextChestSlot = 0;
    private int nextChestSlot2 = 0;
    private int cooldown = 0;
    private long actionWindowStart = 0;
    private int invActions = 0;
    private State state = State.IDLE;

    // Tracks last activity time
    private long lastProgressTime = 0;

    private int stacksFetched = 0; // Track across ticks
    private int stacksFetched2 = 0; // Track across ticks

    private boolean waitingForFetch2 = false;
    private long fetch2WaitTimer = 0;

    public DevinsCrafter() {
        super(DevinsAddon.CATEGORY, "DevinsCrafter", "Automates fetch → craft → selective export.");
    }

    @Override
    public void onActivate() {
        openedFarmChest = false;
        openedFarmChest2 = false;
        openedCraftTable = false;
        openedExportChest = false;
        nextChestSlot = 0;
        nextChestSlot2 = 0;
        cooldown = 0;
        actionWindowStart = System.currentTimeMillis();
        invActions = 0;
        state = State.IDLE;
        lastProgressTime = System.currentTimeMillis();
        chestOpenWindowStart = System.currentTimeMillis();
        chestOpenActions = 0;
        stacksFetched = 0;
        stacksFetched2 = 0;
        waitingForFetch2 = false;
        fetch2WaitTimer = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!Utils.canUpdate() || mc.interactionManager == null || targets.get().isEmpty()) return;

        boolean pathing = isBaritonePathing();
        if (pathing) {
            // keep watchdog fresh while Baritone is moving so we don't reset to IDLE mid-route
            lastProgressTime = System.currentTimeMillis();
        } else {
            checkStuck();
        }

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        if (!autoMode.get()) {
            manualTick();
        } else {
            autoModeTick();
        }
    }

    private boolean isBaritonePathing() {
        return BaritoneAPI.getProvider()
            .getPrimaryBaritone()
            .getPathingBehavior()
            .isPathing();
    }

    private void checkStuck() {
        long now = System.currentTimeMillis();
        if (now - lastProgressTime > TIMEOUT_MS) {
            resetCycle();
            lastProgressTime = now;
        }
    }

    private void resetCycle() {
        openedFarmChest = false;
        openedFarmChest2 = false;
        openedCraftTable = false;
        openedExportChest = false;
        nextChestSlot = 0;
        nextChestSlot2 = 0;
        state = State.IDLE;
    }

    private boolean storeExportItemsIfPresent() {
        // Check if any export items are in inventory
        boolean hasExport = mc.player.getInventory().main.stream().anyMatch(st -> exportItems.get().contains(st.getItem()) && !st.isEmpty());
        if (!hasExport) return false;
        if (!isAt(exportChestPos.get())) {
            moveTo(exportChestPos.get());
            return true; // block state machine until at chest
        }
        // Open chest if not already open
        if (openChestThrottled(() -> openedExportChest, () -> openedExportChest = true, exportChestPos.get())) return true;
        var handler = mc.player.currentScreenHandler;
        if (!(handler instanceof GenericContainerScreenHandler) && !(handler instanceof ShulkerBoxScreenHandler)) return true;
        boolean moved = false;
        for (Slot s : handler.slots) {
            if (s.inventory == mc.player.getInventory() && s.hasStack() && exportItems.get().contains(s.getStack().getItem()) && clickSlot(handler.syncId, s.id)) {
                moved = true;
            }
        }
        // If nothing left to move, close chest
        boolean stillHasExport = mc.player.getInventory().main.stream().anyMatch(st -> exportItems.get().contains(st.getItem()) && !st.isEmpty());
        if (!stillHasExport) {
            closeContainer(handler.syncId);
            openedExportChest = false;
        }
        return true; // block state machine until all export items are stored
    }

    private void autoModeTick() {
        // Only store export items after crafting is done (EXPORT, RETURN, IDLE)
        if (state == State.EXPORT || state == State.RETURN || state == State.IDLE) {
            if (storeExportItemsIfPresent()) return;
        }

        switch (state) {
            case IDLE:
                ChatUtils.info("[DevinsCrafter] State: IDLE");
                if (isAt(farmChestPos.get())) {
                    state = State.FETCH;
                    ChatUtils.info("[DevinsCrafter] Transition: IDLE -> FETCH");
                } else {
                    moveTo(farmChestPos.get());
                    ChatUtils.info("[DevinsCrafter] Moving to farm chest 1");
                }
                break;

            case FETCH:
                ChatUtils.info("[DevinsCrafter] State: FETCH");

                // ---------- MOD BLOCK (skip fetch when inventory already has fetch-items) ----------
                boolean alreadyHaveFetch = mc.player.getInventory().main.stream()
                    .anyMatch(st -> !st.isEmpty() && fetchItems.get().contains(st.getItem()));
                if (alreadyHaveFetch) {
                    ChatUtils.info("[DevinsCrafter] Inventory already contains fetch items – skipping FETCH");

                    openedFarmChest = false;
                    nextChestSlot = 0;
                    stacksFetched = 0;

                    if (enableFarmChest2.get()) {
                        state = State.FETCH2;
                        ChatUtils.info("[DevinsCrafter] Transition: FETCH(SKIP) -> FETCH2");
                        if (!isAt(farmChest2Pos.get())) {
                            moveTo(farmChest2Pos.get());
                            ChatUtils.info("[DevinsCrafter] Moving to farm chest 2");
                        }
                    } else {
                        state = State.CRAFT;
                        ChatUtils.info("[DevinsCrafter] Transition: FETCH(SKIP) -> CRAFT");
                        if (!isAt(craftingTablePos.get())) {
                            moveTo(craftingTablePos.get());
                            ChatUtils.info("[DevinsCrafter] Moving to crafting table");
                        }
                    }
                    break;
                }
                // ---------- END MOD BLOCK ----------

                if (openChestThrottled(() -> openedFarmChest, () -> openedFarmChest = true, farmChestPos.get())) {
                    ChatUtils.info("[DevinsCrafter] Opening farm chest 1");
                    return;
                }
                var handler = mc.player.currentScreenHandler;
                if (!(handler instanceof GenericContainerScreenHandler) && !(handler instanceof ShulkerBoxScreenHandler)) return;
                int rows;
                if (handler instanceof GenericContainerScreenHandler g) {
                    rows = g.getRows();
                } else {
                    rows = 3; // Shulker boxes always have 3 rows
                }
                long expectedSlots = rows * 9L;
                long loadedSlots = handler.slots.stream().filter(s -> s.inventory != mc.player.getInventory()).count();
                if (loadedSlots < expectedSlots) return;

                int slots = rows * 9;
                boolean couldFetchFetch = false;
                while (nextChestSlot < slots && stacksFetched < stacksToFetch.get()) {
                    Slot s = handler.slots.get(nextChestSlot++);
                    if (s.hasStack() && fetchItems.get().contains(s.getStack().getItem()) && hasEmptyInvSlot() && clickSlot(handler.syncId, s.id)) {
                        stacksFetched++;
                        couldFetchFetch = true;
                        ChatUtils.info("[DevinsCrafter] Took stack from farm chest 1, slot " + s.id);
                    }
                }

                if (stacksFetched >= stacksToFetch.get() || nextChestSlot >= slots || !hasEmptyInvSlot() || !couldFetchFetch) {
                    closeContainer(handler.syncId);
                    openedFarmChest = false;
                    nextChestSlot = 0;
                    stacksFetched = 0;
                    if (enableFarmChest2.get()) {
                        state = State.FETCH2;
                        ChatUtils.info("[DevinsCrafter] Transition: FETCH -> FETCH2");
                        if (!isAt(farmChest2Pos.get())) {
                            moveTo(farmChest2Pos.get());
                            ChatUtils.info("[DevinsCrafter] Moving to farm chest 2");
                        }
                    } else {
                        state = State.CRAFT;
                        ChatUtils.info("[DevinsCrafter] Transition: FETCH -> CRAFT");
                        if (!isAt(craftingTablePos.get())) {
                            moveTo(craftingTablePos.get());
                            ChatUtils.info("[DevinsCrafter] Moving to crafting table");
                        }
                    }
                }
                break;

            case FETCH2:
                ChatUtils.info("[DevinsCrafter] State: FETCH2");
                ChatUtils.info("[DevinsCrafter] before openedFarmChest2" + openedFarmChest2);

                // ---------- MOD BLOCK (skip fetch2 when inventory already has fetch-items-2) ----------
                boolean alreadyHaveFetch2 = mc.player.getInventory().main.stream()
                    .anyMatch(st -> !st.isEmpty() && fetchItems2.get().contains(st.getItem()));
                if (alreadyHaveFetch2) {
                    ChatUtils.info("[DevinsCrafter] Inventory already contains fetch-2 items – skipping FETCH2");

                    openedFarmChest2 = false;
                    nextChestSlot2 = 0;
                    stacksFetched2 = 0;

                    state = State.CRAFT;
                    ChatUtils.info("[DevinsCrafter] Transition: FETCH2(SKIP) -> CRAFT");
                    if (!isAt(craftingTablePos.get())) {
                        moveTo(craftingTablePos.get());
                        ChatUtils.info("[DevinsCrafter] Moving to crafting table");
                    }
                    break;
                }
                // ---------- END MOD BLOCK ----------

                if (isAt(farmChest2Pos.get())) {
                    if (openChestThrottled(() -> openedFarmChest2, () -> openedFarmChest2 = true, farmChest2Pos.get())) {
                        ChatUtils.info("[DevinsCrafter] Opening farm chest 2");
                        ChatUtils.info("[DevinsCrafter] ao openedFarmChest2" + openedFarmChest2);

                        return;
                    }
                    ChatUtils.info("[DevinsCrafter] Fetch2 opned");

                    var handler2 = mc.player.currentScreenHandler;
                    if (!(handler2 instanceof GenericContainerScreenHandler) && !(handler2 instanceof ShulkerBoxScreenHandler)) return;
                    int rows2;
                    if (handler2 instanceof GenericContainerScreenHandler g2) {
                        rows2 = g2.getRows();
                    } else {
                        rows2 = 3;
                    }
                    long expectedSlots2 = rows2 * 9L;
                    long loadedSlots2 = handler2.slots.stream().filter(s -> s.inventory != mc.player.getInventory()).count();
                    if (loadedSlots2 < expectedSlots2) return;
                    int slots2 = rows2 * 9;

                    boolean couldFetch = false;
                    int slotsChecked = 0;
                    while (nextChestSlot2 < slots2 && stacksFetched2 < stacksToFetch2.get()) {
                        ChatUtils.info("[DevinsCrafter] while loop, nextChestSlot2: " + nextChestSlot2 + ", stacksFetched2: " + stacksFetched2);

                        Slot s2 = handler2.slots.get(nextChestSlot2++);
                        slotsChecked++;
                        if (s2.hasStack() && fetchItems2.get().contains(s2.getStack().getItem()) && hasEmptyInvSlot()) {
                            if (clickSlot(handler2.syncId, s2.id)) {
                                stacksFetched2++;
                                couldFetch = true;
                                ChatUtils.info("[DevinsCrafter] Took stack from farm chest 2, slot " + s2.id);
                            }
                        }
                    }

                    // Fallback: if we checked all slots, or couldn't fetch anything, or inventory is full, always advance
                    if (stacksFetched2 >= stacksToFetch2.get() || nextChestSlot2 >= slots2 || !hasEmptyInvSlot() || !couldFetch || slotsChecked == 0) {
                        closeContainer(handler2.syncId);
                        openedFarmChest2 = false;
                        nextChestSlot2 = 0;
                        stacksFetched2 = 0;
                        state = State.CRAFT;
                        ChatUtils.info("[DevinsCrafter] Transition: FETCH2 -> CRAFT");
                        if (!isAt(craftingTablePos.get())) {
                            moveTo(craftingTablePos.get());
                            ChatUtils.info("[DevinsCrafter] Moving to crafting table");
                        }
                    }
                } else {
                    openedFarmChest2 = false;
                    nextChestSlot2 = 0;
                    stacksFetched2 = 0;
                    moveTo(farmChest2Pos.get());
                    ChatUtils.info("[DevinsCrafter] Moving to farm chest 2");
                }
                break;

            case CRAFT:
                ChatUtils.info("[DevinsCrafter] State: CRAFT");
                if (!isAt(craftingTablePos.get())) return;
                if (openContainerOnce(() -> openedCraftTable, () -> openedCraftTable = true, craftingTablePos.get())) {
                    ChatUtils.info("[DevinsCrafter] Opening crafting table");
                    return;
                }
                if (!(mc.player.currentScreenHandler instanceof CraftingScreenHandler)) return;
                if (!craftTick()) return;
                mc.player.closeHandledScreen();
                openedCraftTable = false;
                state = State.EXPORT;
                ChatUtils.info("[DevinsCrafter] Transition: CRAFT -> EXPORT");
                if (!isAt(exportChestPos.get())) {
                    moveTo(exportChestPos.get());
                    ChatUtils.info("[DevinsCrafter] Moving to export chest");
                }
                break;

            case EXPORT:
                ChatUtils.info("[DevinsCrafter] State: EXPORT");
                if (!isAt(exportChestPos.get())) return;
                if (openChestThrottled(() -> openedExportChest, () -> openedExportChest = true, exportChestPos.get())) {
                    ChatUtils.info("[DevinsCrafter] Opening export chest");
                    return;
                }
                var handler2 = mc.player.currentScreenHandler;
                if (!(handler2 instanceof GenericContainerScreenHandler) && !(handler2 instanceof ShulkerBoxScreenHandler)) return;
                boolean moved = false;
                for (Slot s : handler2.slots) {
                    if (s.inventory == mc.player.getInventory() && s.hasStack() && exportItems.get().contains(s.getStack().getItem()) && clickSlot(handler2.syncId, s.id)) {
                        moved = true;
                        ChatUtils.info("[DevinsCrafter] Exported item from slot " + s.id);
                        break;
                    }
                }
                if (moved) return;
                closeContainer(handler2.syncId);
                openedExportChest = false;
                state = State.RETURN;
                ChatUtils.info("[DevinsCrafter] Transition: EXPORT -> RETURN");
                if (!isAt(farmChestPos.get())) {
                    moveTo(farmChestPos.get());
                    ChatUtils.info("[DevinsCrafter] Moving to farm chest 1");
                }
                break;

            case RETURN:
                ChatUtils.info("[DevinsCrafter] State: RETURN");
                if (isAt(farmChestPos.get())) {
                    state = State.IDLE;
                    ChatUtils.info("[DevinsCrafter] Transition: RETURN -> IDLE");
                }
                break;
        }
    }

    private boolean clickSlot(int syncId, int slotId) {
        if (!canPerformAction()) return false;
        mc.interactionManager.clickSlot(syncId, slotId, 0, SlotActionType.QUICK_MOVE, mc.player);
        cooldown = delay.get();
        lastProgressTime = System.currentTimeMillis();
        return true;
    }

    // original openContainerOnce, no throttle
    private boolean openContainerOnce(BooleanSupplier openedFlag, Runnable setter, BlockPos pos) {
        if (!openedFlag.getAsBoolean()) {
            setter.run();
            openChest(pos);
            lastProgressTime = System.currentTimeMillis();
            return true;
        }
        return false;
    }

    // new helper for chest-open throttle
    private boolean openChestThrottled(BooleanSupplier openedFlag, Runnable setter, BlockPos pos) {
        if (!openedFlag.getAsBoolean()) {
            ChatUtils.info("[DevinsCrafter] CAN ? openChestThrottled");
            if (!canOpenChestAction()) return true;

            ChatUtils.info("[DevinsCrafter] CAN openChestThrottled");

            setter.run();
            openChest(pos);
            lastProgressTime = System.currentTimeMillis();
            return true;
        }
        return false;
    }

    private void closeContainer(int syncId) {
        mc.player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(syncId));
        mc.player.closeHandledScreen();
        lastProgressTime = System.currentTimeMillis();
    }

    private boolean canPerformAction() {
        long now = System.currentTimeMillis();
        if (now - actionWindowStart > 5000) {
            actionWindowStart = now;
            invActions = 0;
        }
        return ++invActions <= 100;
    }

    private boolean canOpenChestAction() {
        long now = System.currentTimeMillis();
        if (now - chestOpenWindowStart > 1000) {
            chestOpenWindowStart = now;
            chestOpenActions = 0;
        }
        return chestOpenActions++ < chestOpenLimit.get();
    }

    private boolean hasEmptyInvSlot() {
        return mc.player.getInventory().main.stream().anyMatch(st -> st.isEmpty());
    }

    private void moveTo(BlockPos pos) {
        BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().onLostControl();
        BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalNear(pos, 3));
        lastProgressTime = System.currentTimeMillis();
    }

    private boolean isAt(BlockPos pos) {
        return mc.player.getBlockPos().isWithinDistance(pos, 8);
    }

    private void openChest(BlockPos pos) {
        BlockHitResult hit = new BlockHitResult(pos.toCenterPos(), Direction.UP, pos, false);
        // Use main hand and standard interaction
        mc.player.networkHandler.sendPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, hit, 0));
    }

    private boolean craftTick() {
        CraftingScreenHandler h = (CraftingScreenHandler) mc.player.currentScreenHandler;
        if (antiDesync.get()) h.syncState();
        List<RecipeDisplayEntry> recipes = mc.player.getRecipeBook().getOrderedResults().stream()
            .flatMap(c -> c.filter(RecipeResultCollection.RecipeFilterMode.CRAFTABLE).stream())
            .map(o -> (RecipeDisplayEntry) o)
            .filter(e -> e.display().result().getStacks(SlotDisplayContexts.createParameters(mc.world))
                .stream().anyMatch(s -> targets.get().contains(s.getItem())))
            .collect(Collectors.toList());
        if (recipes.isEmpty()) return true;
        if (!canPerformAction()) return false;
        mc.interactionManager.clickRecipe(h.syncId, recipes.get(0).id(), shiftCraft.get());
        lastProgressTime = System.currentTimeMillis();
        if (!canPerformAction()) return false;
        mc.interactionManager.clickSlot(h.syncId, 0, 0, SlotActionType.QUICK_MOVE, mc.player);
        cooldown = delay.get();
        lastProgressTime = System.currentTimeMillis();
        return false;
    }

    private void manualTick() {
        if (!(mc.player.currentScreenHandler instanceof CraftingScreenHandler)) return;
        if (craftTick()) return;
    }

    private BoolSetting.Builder newPositionSetter(String name, Setting<BlockPos> target, String label) {
        return new BoolSetting.Builder()
            .name(name)
            .description("Look at a " + label + " and toggle to save its coords")
            .defaultValue(false)
            .onChanged(b -> {
                if (!b) return;
                if (mc.crosshairTarget instanceof BlockHitResult hit) {
                    target.set(hit.getBlockPos());
                    ChatUtils.info("✅ Set to " + hit.getBlockPos());
                } else {
                    ChatUtils.error("👀 Look at a " + label + " first!");
                }
            });
    }
}
