package com.adamk33n3r.runelite.watchdog.notifications;

import com.adamk33n3r.runelite.watchdog.WatchdogPlugin;

import net.runelite.client.config.FlashNotification;
import net.runelite.client.util.ColorUtil;

import lombok.Getter;
import lombok.Setter;

import java.awt.Color;

@Getter
@Setter
public class ScreenFlash extends Notification {
    private Color color = ColorUtil.fromHex("#46FF0000");
    private FlashNotification flashNotification = FlashNotification.SOLID_TWO_SECONDS;

    @Override
    protected void fireImpl(String[] triggerValues) {
        WatchdogPlugin.getInstance().getFlashOverlay().flash(this);
    }
}
