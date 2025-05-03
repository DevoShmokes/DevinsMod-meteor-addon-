package com.github.DevinsMod.tasks;

import com.github.DevinsMod.tracker.RotationManager;
import com.github.DevinsMod.utils.RotationRequest;
import lombok.Getter;
import java.util.List;
import java.util.HashSet;
import java.util.ArrayList;
import baritone.api.BaritoneAPI;


import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.pathing.BaritoneUtils;
import meteordevelopment.meteorclient.utils.player.ChatUtils;

@Getter
public class Task {
    // Static list to store all task instances
    private static final List<Task> tasks = new ArrayList<>();
    public RotationRequest rotationRequest = new RotationRequest(0, false);
    public boolean complete;

    public Task() {
        MeteorClient.EVENT_BUS.subscribe(this);
        Task.tasks.add(this);
        RotationManager.addRequest(rotationRequest);
        info("Task created");
    }

    // Method to end this task
    public void endTask(String reason) {
        RotationManager.removeRequest(rotationRequest);
        info("Task complete: " + reason);
        Task.tasks.remove(this);
        unSubscribe();
        complete = true;
    }

    // Unsubscribe this task from the event bus
    public void unSubscribe() {
        MeteorClient.EVENT_BUS.unsubscribe(this);
    }

    public static void endAllTasks() {
        ChatUtils.info("ending all tasks " + Task.tasks.size());
        if (BaritoneUtils.IS_AVAILABLE) {
            BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
        }
        // Use an iterator to safely remove items from the list while iterating
        try {
            for (Task task : new HashSet<>(Task.tasks)) {
                ChatUtils.info("killing task " + task.getClass().getSimpleName());
                task.endTask("Killed All Tasks");
            }
        } catch (Exception e) {
            ChatUtils.error("error ending all tasks");
        }

    }

    public void info(String message, Object... args) {
        ChatUtils.infoPrefix(getClass().getSimpleName(), message, args);
    }

    public void error(String message, Object... args) {
        ChatUtils.errorPrefix("TaskError " + getClass().getSimpleName(), message, args);
    }

    public boolean willWork() {
        return true;
    }

}
