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
    private enum State {IDLE, FETCH, CRAFT, EXPORT, RETURN}

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<List<Item>> fetchItems = sgGeneral.add(
        new ItemListSetting.Builder()
            .name("fetch-items")
            .description("Only these items will be pulled from the farm chest.")
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

    private final Setting<Boolean> setCraftHere = sgGeneral.add(
        newPositionSetter("set-craft-here", craftingTablePos, "crafting table").build()
    );

    private final Setting<Boolean> setExportHere = sgGeneral.add(
        newPositionSetter("set-export-here", exportChestPos, "export chest").build()
    );

    private boolean openedFarmChest = false;
    private boolean openedCraftTable = false;
    private boolean openedExportChest = false;

    private int nextChestSlot = 0;
    private int cooldown = 0;
    private long actionWindowStart = System.currentTimeMillis();
    private int invActions = 0;
    private State state = State.IDLE;

    public DevinsCrafter() {
        super(DevinsAddon.CATEGORY, "DevinsCrafter", "Automates fetch → craft → selective export.");
    }

    @Override
    public void onActivate() {
        openedFarmChest = false;
        openedCraftTable = false;
        openedExportChest = false;
        nextChestSlot = 0;
        cooldown = 0;
        actionWindowStart = System.currentTimeMillis();
        invActions = 0;
        state = State.IDLE;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!Utils.canUpdate() || mc.interactionManager == null || targets.get().isEmpty()) return;
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        if (!autoMode.get()) {
            manualTick();
            return;
        }
        autoModeTick();
    }

    private void autoModeTick() {
        switch (state) {
            case IDLE:
                if (isAt(farmChestPos.get())) state = State.FETCH;
                else moveTo(farmChestPos.get());
                break;

            case FETCH:
                if (openContainerOnce(() -> openedFarmChest, () -> openedFarmChest = true, farmChestPos.get())) return;
                if (!(mc.player.currentScreenHandler instanceof GenericContainerScreenHandler h)) return;
                long expectedSlots = h.getRows() * 9L;
                long loadedSlots = h.slots.stream().filter(s -> s.inventory != mc.player.getInventory()).count();
                if (loadedSlots < expectedSlots) return; // wait for server to send chest contents
                int slots = h.getRows() * 9;
                while (nextChestSlot < slots) {
                    Slot s = h.slots.get(nextChestSlot++);
                    if (s.hasStack() && fetchItems.get().contains(s.getStack().getItem()) && hasEmptyInvSlot() && clickSlot(h.syncId, s.id)) return;
                }
                closeContainer(h.syncId);
                openedFarmChest = false;
                nextChestSlot = 0;
                state = State.CRAFT;
                moveTo(craftingTablePos.get());
                break;

            case CRAFT:
                if (!isAt(craftingTablePos.get())) return;
                if (openContainerOnce(() -> openedCraftTable, () -> openedCraftTable = true, craftingTablePos.get())) return;
                if (!(mc.player.currentScreenHandler instanceof CraftingScreenHandler)) return;
                if (!craftTick()) return;
                mc.player.closeHandledScreen();
                openedCraftTable = false;
                state = State.EXPORT;
                moveTo(exportChestPos.get());
                break;

            case EXPORT:
                if (!isAt(exportChestPos.get())) return;
                if (openContainerOnce(() -> openedExportChest, () -> openedExportChest = true, exportChestPos.get())) return;
                if (!(mc.player.currentScreenHandler instanceof GenericContainerScreenHandler h2)) return;
                boolean moved = false;
                for (Slot s : h2.slots) {
                    if (s.inventory == mc.player.getInventory() && s.hasStack() && exportItems.get().contains(s.getStack().getItem()) && clickSlot(h2.syncId, s.id)) {
                        moved = true;
                        break;
                    }
                }
                if (moved) return;
                closeContainer(h2.syncId);
                openedExportChest = false;
                state = State.RETURN;
                moveTo(farmChestPos.get());
                break;

            case RETURN:
                if (isAt(farmChestPos.get())) state = State.IDLE;
                break;
        }
    }

    private boolean clickSlot(int syncId, int slotId) {
        if (!canPerformAction()) return false;
        mc.interactionManager.clickSlot(syncId, slotId, 0, SlotActionType.QUICK_MOVE, mc.player);
        cooldown = delay.get();
        return true;
    }

    private boolean openContainerOnce(BooleanSupplier openedFlag, Runnable setter, BlockPos pos) {
        if (!openedFlag.getAsBoolean()) {
            setter.run();
            openChest(pos);
            return true;
        }
        return false;
    }

    private void closeContainer(int syncId) {
        mc.player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(syncId));
        mc.player.closeHandledScreen();
    }

    private boolean canPerformAction() {
        long now = System.currentTimeMillis();
        if (now - actionWindowStart > 5000) {
            actionWindowStart = now;
            invActions = 0;
        }
        return ++invActions <= 100;
    }

    private boolean hasEmptyInvSlot() {
        return mc.player.getInventory().main.stream().anyMatch(st -> st.isEmpty());
    }

    private void moveTo(BlockPos pos) {
        BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().onLostControl();
        BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalNear(pos, 3));
    }

    private boolean isAt(BlockPos pos) {
        return mc.player.getBlockPos().isWithinDistance(pos, 3);
    }

    private void openChest(BlockPos pos) {
        BlockHitResult hit = new BlockHitResult(pos.toCenterPos(), Direction.UP, pos, false);
        mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
        mc.player.networkHandler.sendPacket(new PlayerInteractBlockC2SPacket(Hand.OFF_HAND, hit, 0));
        mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
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
        if (!canPerformAction()) return false;
        mc.interactionManager.clickSlot(h.syncId, 0, 0, SlotActionType.QUICK_MOVE, mc.player);
        cooldown = delay.get();
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
