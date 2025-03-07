package com.adamk33n3r.runelite.watchdog.ui.notifications.panels;

import com.adamk33n3r.runelite.watchdog.NotificationType;
import com.adamk33n3r.runelite.watchdog.WatchdogConfig;
import com.adamk33n3r.runelite.watchdog.WatchdogPlugin;
import com.adamk33n3r.runelite.watchdog.notifications.Notification;
import com.adamk33n3r.runelite.watchdog.ui.Icons;
import com.adamk33n3r.runelite.watchdog.ui.StretchedStackedLayout;
import com.adamk33n3r.runelite.watchdog.ui.panels.NotificationsPanel;
import com.adamk33n3r.runelite.watchdog.ui.panels.PanelUtils;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.components.MouseDragEventForwarder;

import lombok.Getter;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class NotificationPanel extends JPanel {
    // worldhopper - arrow down
    // screenmarker - border color icon - pencil
    // screenmarker/timetracking - delete icon - X
    // timetracking - notify - bell
    // timetracking - reset - circle arrow - used for in-focus?
    // timetracking - start - right chevron
    // loottracker - back arrow
    // loottracker - collapsed/expanded
    // info - import cloud
    // info - github
    // config - edit/back

    @Getter
    protected Notification notification;
    protected Runnable onChangeListener;
    protected PanelUtils.OnRemove onRemove;
    protected JPanel settings = new JPanel(new StretchedStackedLayout(3, 3));

    private static final Border NAME_BOTTOM_BORDER = new CompoundBorder(
        BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR),
        BorderFactory.createMatteBorder(5, 10, 5, 0, ColorScheme.DARKER_GRAY_COLOR));

    public NotificationPanel(Notification notification, NotificationsPanel parentPanel, Runnable onChangeListener, PanelUtils.OnRemove onRemove) {
        this.notification = notification;
        this.onChangeListener = onChangeListener;
        this.onRemove = onRemove;

        WatchdogConfig config = WatchdogPlugin.getInstance().getConfig();

        this.setLayout(new BorderLayout());
        this.setBorder(new EmptyBorder(3, 0, 0, 0));
        JPanel container = new JPanel(new StretchedStackedLayout(3, 3));
        container.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        JPanel nameWrapper = new JPanel(new BorderLayout(3, 3));
        container.add(nameWrapper);

        nameWrapper.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        nameWrapper.setBorder(NAME_BOTTOM_BORDER);
        NotificationType notificationType = notification.getType();
        JLabel nameLabel = new JLabel(notificationType.getName());
        nameLabel.setToolTipText(notificationType.getTooltip());
        nameWrapper.add(nameLabel, BorderLayout.WEST);

        MouseDragEventForwarder mouseDragEventForwarder = new MouseDragEventForwarder(parentPanel.getNotificationContainer());
        nameWrapper.addMouseListener(mouseDragEventForwarder);
        nameWrapper.addMouseMotionListener(mouseDragEventForwarder);
        nameLabel.addMouseListener(mouseDragEventForwarder);
        nameLabel.addMouseMotionListener(mouseDragEventForwarder);

        // Right buttons
        JPanel rightActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        rightActions.setBorder(new EmptyBorder(4, 0, 0, 0));
        rightActions.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        nameWrapper.add(rightActions, BorderLayout.EAST);

        JPanel afkTimerConfigRow = new JPanel(new GridLayout(1, 2));
        afkTimerConfigRow.setBorder(new EmptyBorder(4, 10, 0, 5));
        afkTimerConfigRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        JLabel afkTimerLabel = new JLabel("AFK Seconds:");
        afkTimerLabel.setToolTipText("Number of seconds for which the client doesn't get any mouse or keyboard inputs.");
        AtomicInteger previousAFKSeconds = new AtomicInteger(notification.getFireWhenAFKForSeconds());

        JSpinner afkTimerSpinner = PanelUtils.createSpinner(Math.max(notification.getFireWhenAFKForSeconds(), 1),
            1,
            25 * 60,
            1,
            (val) -> {
                notification.setFireWhenAFKForSeconds(val);
                previousAFKSeconds.set(val);
                onChangeListener.run();
            });
        if (notification.isFireWhenAFK()) {
            afkTimerConfigRow.add(afkTimerLabel);
            afkTimerConfigRow.add(afkTimerSpinner);
        }

        JButton afkButton = PanelUtils.createToggleActionButton(
            Icons.AFK,
            Icons.AFK_HOVER,
            Icons.NON_AFK,
            Icons.NON_AFK_HOVER,
            "Enable notification even when you are active",
            "Switch to only fire notification when you have been AFK for a certain amount of time",
            notification.isFireWhenAFK(),
            (btn, modifiers) -> {
                notification.setFireWhenAFK(btn.isSelected());
                notification.setFireWhenAFKForSeconds(previousAFKSeconds.get());
                if (notification.isFireWhenAFK()) {
                    afkTimerSpinner.setValue(notification.getFireWhenAFKForSeconds());
                    afkTimerConfigRow.add(afkTimerLabel);
                    afkTimerConfigRow.add(afkTimerSpinner);
                } else {
                    afkTimerConfigRow.removeAll();
                }
                afkTimerConfigRow.revalidate();
                afkTimerConfigRow.repaint();
                onChangeListener.run();
            });
        rightActions.add(afkButton, BorderLayout.EAST);

        JButton focusBtn = PanelUtils.createToggleActionButton(
            Icons.FOREGROUND,
            Icons.FOREGROUND_HOVER,
            Icons.BACKGROUND,
            Icons.BACKGROUND_HOVER,
            "Switch to only fire notification while the game is in the background",
            "Enable notification while the game is in the foreground",
            notification.isFireWhenFocused(),
            (btn, modifiers) -> {
                notification.setFireWhenFocused(btn.isSelected());
                onChangeListener.run();
            });
        rightActions.add(focusBtn, BorderLayout.EAST);

        JButton testBtn = PanelUtils.createActionButton(
            Icons.TEST,
            Icons.TEST_HOVER,
            "Test the notification",
            (btn, modifiers) -> notification.fireForced(new String[]{ "1", "2", "3", "4", "5" }));
        rightActions.add(testBtn);

        JButton deleteBtn = PanelUtils.createActionButton(
            Icons.DELETE,
            Icons.DELETE_HOVER,
            "Remove this notification",
            (btn, modifiers) -> onRemove.elementRemoved(this));
        rightActions.add(deleteBtn);

        container.add(afkTimerConfigRow);

        this.settings.setBorder(new EmptyBorder(5, 10, 5, 10));
        this.settings.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        container.add(this.settings);

        this.add(container, BorderLayout.CENTER);
    }
}
