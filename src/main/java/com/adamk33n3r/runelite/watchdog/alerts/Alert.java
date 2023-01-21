package com.adamk33n3r.runelite.watchdog.alerts;

import com.adamk33n3r.runelite.watchdog.Util;
import com.adamk33n3r.runelite.watchdog.notifications.Notification;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
public abstract class Alert {
    @Setter
    private boolean enabled = true;

    @Setter
    private String name;

    @Setter
    private int debounceTime;

    private final List<Notification> notifications = new ArrayList<>();

    public Alert(String name) {
        this.name = name;
        this.debounceTime = 0;
    }

    public String getDisplayName() {
        return Util.humanReadableClass(this);
    }
}
