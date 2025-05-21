package com.github.DevinsMod;

import com.github.DevinsMod.modules.DevinsCrafter;
import com.github.DevinsMod.modules.DevinsTrader;
import com.github.DevinsMod.modules.RotationManager;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

public class DevinsAddon extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("DevinsMods");

    @Override
    public void onInitialize() {
        LOG.info("Initializing DevinsMod");

        // Modules
        Modules.get().add(new DevinsCrafter());
        Modules.get().add(new DevinsTrader());
        Modules.get().add(new RotationManager());
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.github.DevinsMod";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("MeteorDevelopment", "meteor-addon-template");
    }
}
