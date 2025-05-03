package com.github.DevinsMod.modules;


import com.github.DevinsMod.DevinsAddon;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;

public class RotationManager extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    public final Setting<Boolean> movementFix = sgGeneral.add(new BoolSetting.Builder()
        .name("movement-fix")
        .defaultValue(true)
        .build()
    );

    public RotationManager() {
        super(DevinsAddon.CATEGORY, "rotation-manager", "Spams interact item packets.");
    }
}
