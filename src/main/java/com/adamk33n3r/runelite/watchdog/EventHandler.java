package com.adamk33n3r.runelite.watchdog;

import com.adamk33n3r.runelite.watchdog.alerts.*;
import com.adamk33n3r.runelite.watchdog.ui.panels.HistoryPanel;

import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NotificationFired;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.Text;

import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import java.awt.TrayIcon;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.adamk33n3r.runelite.watchdog.alerts.SpawnedAlert.SpawnedDespawned.DESPAWNED;
import static com.adamk33n3r.runelite.watchdog.alerts.SpawnedAlert.SpawnedDespawned.SPAWNED;
import static com.adamk33n3r.runelite.watchdog.alerts.SpawnedAlert.SpawnedType.*;

@Slf4j
@Singleton
public class EventHandler {
    @Inject
    private Client client;

    @Inject
    private ItemManager itemManager;

    @Inject
    private AlertManager alertManager;

    @Inject
    private EventBus eventBus;

    @Inject
    private Provider<HistoryPanel> historyPanelProvider;

    private final Map<Alert, Instant> lastTriggered = new HashMap<>();

    private final Map<Skill, Integer> previousSkillLevelTable = new EnumMap<>(Skill.class);
    private final Map<Skill, Integer> previousSkillXPTable = new EnumMap<>(Skill.class);

    private boolean ignoreNotificationFired = false;

    public synchronized void notify(String message) {
        this.ignoreNotificationFired = true;
        // The event bus is synchronous
        this.eventBus.post(new NotificationFired(message, TrayIcon.MessageType.NONE));
        this.ignoreNotificationFired = false;
    }

    //region Chat Message
    @Subscribe
    public void onChatMessage(ChatMessage chatMessage) {
        // Don't process messages sent by this plugin
        if (chatMessage.getName().equals(WatchdogPlugin.getInstance().getName())) {
            return;
        }

//        log.debug(chatMessage.getType().name() + ": " + chatMessage.getMessage());
        String unformattedMessage = Text.removeFormattingTags(chatMessage.getMessage());

        // Send player messages to a different handler
        if (
            chatMessage.getType() == ChatMessageType.PUBLICCHAT
                || chatMessage.getType() == ChatMessageType.AUTOTYPER
                || chatMessage.getType() == ChatMessageType.PRIVATECHAT
                || chatMessage.getType() == ChatMessageType.PRIVATECHATOUT
                || chatMessage.getType() == ChatMessageType.MODCHAT
                || chatMessage.getType() == ChatMessageType.MODPRIVATECHAT
                || chatMessage.getType() == ChatMessageType.MODAUTOTYPER
                || chatMessage.getType() == ChatMessageType.FRIENDSCHAT
                || chatMessage.getType() == ChatMessageType.CLAN_CHAT
                || chatMessage.getType() == ChatMessageType.CLAN_GUEST_CHAT
                || chatMessage.getType() == ChatMessageType.CLAN_GIM_CHAT
        ) {
            this.alertManager.getAllEnabledAlertsOfType(PlayerChatAlert.class)
                .forEach(chatAlert -> {
                    String[] groups = this.matchPattern(chatAlert, unformattedMessage);
                    if (groups == null) return;

                    this.fireAlert(chatAlert, groups);
                });
            return;
        }

        this.alertManager.getAllEnabledAlertsOfType(ChatAlert.class)
            .forEach(gameAlert -> {
                String[] groups = this.matchPattern(gameAlert, unformattedMessage);
                if (groups == null) return;

                this.fireAlert(gameAlert, groups);
            });
    }
    //endregion

    //region Notification
    @Subscribe
    public void onNotificationFired(NotificationFired notificationFired) {
        // This flag is set when we are firing our own events, so we don't cause an infinite loop/stack overflow
        if (this.ignoreNotificationFired) {
            return;
        }

        this.alertManager.getAllEnabledAlertsOfType(NotificationFiredAlert.class)
            .forEach(notificationFiredAlert -> {
                String[] groups = this.matchPattern(notificationFiredAlert, notificationFired.getMessage());
                if (groups == null) return;

                this.fireAlert(notificationFiredAlert, groups);
            });
    }
    //endregion

    //region Stat Changed
    @Subscribe
    private void onGameStateChanged(GameStateChanged gameStateChanged) {
        if (gameStateChanged.getGameState() == GameState.LOGGED_IN) {
            for (Skill skill : Skill.values()) {
                this.previousSkillLevelTable.put(skill, this.client.getBoostedSkillLevel(skill));
                this.previousSkillXPTable.put(skill, this.client.getSkillExperience(skill));
            }
        }
    }

    @Subscribe
    public void onStatChanged(StatChanged statChanged) {
//        log.debug(String.format("%s: %s/%s", statChanged.getSkill().getName(), statChanged.getBoostedLevel(), statChanged.getLevel()));
        this.handleStatChanged(statChanged);
        this.handleXPDrop(statChanged);
    }

    private void handleStatChanged(StatChanged statChanged) {
        Integer previousLevel = this.previousSkillLevelTable.put(statChanged.getSkill(), statChanged.getBoostedLevel());
        if (previousLevel == null) {
            return;
        }

        this.alertManager.getAllEnabledAlertsOfType(StatChangedAlert.class)
            .filter(alert -> {
                boolean isSkill = alert.getSkill() == statChanged.getSkill();
                if (!isSkill) {
                    return false;
                }

                int targetLevel = statChanged.getLevel() + alert.getChangedAmount();
                boolean isNegative = alert.getChangedAmount() < 0;
                boolean isLower = statChanged.getBoostedLevel() <= targetLevel;
                boolean wasHigher = previousLevel > targetLevel;
                boolean isHigher = statChanged.getBoostedLevel() >= targetLevel;
                boolean wasLower = previousLevel < targetLevel;
//                log.debug("targetLevel: " + targetLevel);
//                log.debug("{}, {}, {}", isSkill, isLower, wasHigher);
                return (isNegative && isLower && wasHigher) || (!isNegative && isHigher && wasLower);
            })
            .forEach(alert -> this.fireAlert(alert, statChanged.getSkill().getName()));
    }

    private void handleXPDrop(StatChanged statChanged) {
        Integer previousXP = this.previousSkillXPTable.put(statChanged.getSkill(), statChanged.getXp());
        if (previousXP == null) {
            return;
        }

        this.alertManager.getAllEnabledAlertsOfType(XPDropAlert.class)
            .filter(alert -> {
                boolean isSkill = alert.getSkill() == statChanged.getSkill();
                int gainedXP = statChanged.getXp() - previousXP;
                return isSkill && gainedXP >= alert.getGainedAmount();
            })
            .forEach(alert -> this.fireAlert(alert, statChanged.getSkill().getName()));
    }
    //endregion

    //region Inventory
    @Subscribe
    private void onItemContainerChanged(ItemContainerChanged itemContainerChanged) {
        // Ignore everything but inventory
        if (itemContainerChanged.getItemContainer().getId() != InventoryID.INVENTORY.getId())
            return;
        this.alertManager.getAllEnabledAlertsOfType(InventoryAlert.class)
            .forEach(inventoryAlert -> {
                Item[] items = itemContainerChanged.getItemContainer().getItems();
                long itemCount = Arrays.stream(items).filter(item -> item.getId() > -1).count();
                if (inventoryAlert.getInventoryAlertType() == InventoryAlert.InventoryAlertType.FULL && itemCount == 28) {
                    this.fireAlert(inventoryAlert, inventoryAlert.getInventoryAlertType().getName());
                } else if (inventoryAlert.getInventoryAlertType() == InventoryAlert.InventoryAlertType.EMPTY && itemCount == 0) {
                    this.fireAlert(inventoryAlert, inventoryAlert.getInventoryAlertType().getName());
                } else if (inventoryAlert.getInventoryAlertType() == InventoryAlert.InventoryAlertType.ITEM) {
                    Map<Integer, Integer> allItems = new HashMap<>();
                    Arrays.stream(items)
                        .forEach(item -> allItems.merge(item.getId(), item.getQuantity(), Integer::sum));
                    allItems.entrySet().stream()
                        .filter(itemWithCount -> inventoryAlert.getItemQuantity() == 0 || itemWithCount.getValue() == inventoryAlert.getItemQuantity())
                        .map(itemWithCount -> this.matchPattern(inventoryAlert, this.itemManager.getItemComposition(itemWithCount.getKey()).getName()))
                        .filter(Objects::nonNull)
                        .findFirst()
                        .ifPresent(groups -> this.fireAlert(inventoryAlert, groups));
                }
            });
    }
    //endregion

    //region Spawned
    @Subscribe
    private void onItemSpawned(ItemSpawned itemSpawned) {
        ItemComposition comp = this.itemManager.getItemComposition(itemSpawned.getItem().getId());
        this.onSpawned(comp.getName(), SPAWNED, ITEM);
    }
    @Subscribe
    private void onItemDespawned(ItemDespawned itemDespawned) {
        ItemComposition comp = this.itemManager.getItemComposition(itemDespawned.getItem().getId());
        this.onSpawned(comp.getName(), DESPAWNED, ITEM);
    }
    @Subscribe
    private void onNpcSpawned(NpcSpawned npcSpawned) {
        this.onActorSpawned(npcSpawned.getNpc(), NPC);
    }
    @Subscribe
    private void onNpcDespawned(NpcDespawned npcDespawned) {
        this.onActorDespawned(npcDespawned.getNpc(), NPC);
    }
    @Subscribe
    private void onPlayerSpawned(PlayerSpawned playerSpawned) {
        this.onActorSpawned(playerSpawned.getPlayer(), PLAYER);
    }
    @Subscribe
    private void onPlayerDespawned(PlayerDespawned playerDespawned) {
        this.onActorDespawned(playerDespawned.getPlayer(), PLAYER);
    }
    private void onActorSpawned(Actor actor, SpawnedAlert.SpawnedType type) {
        this.onSpawned(actor.getName(), SPAWNED, type);
    }
    private void onActorDespawned(Actor actor, SpawnedAlert.SpawnedType type) {
        this.onSpawned(actor.getName(), DESPAWNED, type);
    }

    @Subscribe
    private void onGroundObjectSpawned(GroundObjectSpawned groundObjectSpawned) {
        this.onTileObjectSpawned(groundObjectSpawned.getGroundObject(), SPAWNED, GROUND_OBJECT);
    }
    @Subscribe
    private void onGroundObjectDespawned(GroundObjectDespawned groundObjectDespawned) {
        this.onTileObjectSpawned(groundObjectDespawned.getGroundObject(), DESPAWNED, GROUND_OBJECT);
    }

    @Subscribe
    private void onDecorativeObjectSpawned(DecorativeObjectSpawned decorativeObjectSpawned) {
        this.onTileObjectSpawned(decorativeObjectSpawned.getDecorativeObject(), SPAWNED, DECORATIVE_OBJECT);
    }
    @Subscribe
    private void onDecorativeObjectDespawned(DecorativeObjectDespawned decorativeObjectDespawned) {
        this.onTileObjectSpawned(decorativeObjectDespawned.getDecorativeObject(), DESPAWNED, DECORATIVE_OBJECT);
    }

    @Subscribe
    private void onGameObjectSpawned(GameObjectSpawned gameObjectSpawned) {
        this.onTileObjectSpawned(gameObjectSpawned.getGameObject(), SPAWNED, GAME_OBJECT);
    }
    @Subscribe
    private void onGameObjectDespawned(GameObjectDespawned gameObjectDespawned) {
        this.onTileObjectSpawned(gameObjectDespawned.getGameObject(), DESPAWNED, GAME_OBJECT);
    }

    @Subscribe
    private void onWallObjectSpawned(WallObjectSpawned wallObjectSpawned) {
        this.onTileObjectSpawned(wallObjectSpawned.getWallObject(), SPAWNED, WALL_OBJECT);
    }
    @Subscribe
    private void onWallObjectDespawned(WallObjectDespawned wallObjectDespawned) {
        this.onTileObjectSpawned(wallObjectDespawned.getWallObject(), DESPAWNED, WALL_OBJECT);
    }

    private void onTileObjectSpawned(TileObject tileObject, SpawnedAlert.SpawnedDespawned mode, SpawnedAlert.SpawnedType type) {
        final ObjectComposition comp = this.client.getObjectDefinition(tileObject.getId());
        final ObjectComposition impostor = comp.getImpostorIds() != null ? comp.getImpostor() : comp;
        if (impostor == null) {
            return;
        }
        this.onSpawned(impostor.getName(), mode, type);
    }

    private void onSpawned(String name, SpawnedAlert.SpawnedDespawned mode, SpawnedAlert.SpawnedType type) {
        String unformattedName = Text.removeFormattingTags(name);
        this.alertManager.getAllEnabledAlertsOfType(SpawnedAlert.class)
            .filter(spawnedAlert -> spawnedAlert.getSpawnedDespawned() == mode)
            .filter(spawnedAlert -> spawnedAlert.getSpawnedType() == type)
            .forEach(spawnedAlert -> {
                String[] groups = this.matchPattern(spawnedAlert, unformattedName);
                if (groups == null) return;

                this.fireAlert(spawnedAlert, groups);
            });
    }
    //endregion

    private String[] matchPattern(RegexMatcher regexMatcher, String input) {
        String regex = regexMatcher.isRegexEnabled() ? regexMatcher.getPattern() : Util.createRegexFromGlob(regexMatcher.getPattern());
        Matcher matcher = Pattern.compile(regex, regexMatcher.isRegexEnabled() ? 0 : Pattern.CASE_INSENSITIVE).matcher(input);
        if (!matcher.matches()) return null;

        String[] groups = new String[matcher.groupCount()];
        for (int i = 0; i < matcher.groupCount(); i++) {
            groups[i] = matcher.group(i+1);
        }
        return groups;
    }

    private void fireAlert(Alert alert, String triggerValue) {
        this.fireAlert(alert, new String[] { triggerValue });
    }

    private void fireAlert(Alert alert, String[] triggerValues) {
        // Don't fire if it is disabled
        if (!alert.isEnabled()) return;

        List<AlertGroup> ancestors = alert.getAncestors();
        // Don't fire if any of the ancestors are disabled
        if (ancestors != null && !ancestors.stream().allMatch(Alert::isEnabled)) {
            return;
        }

        Alert alertToDebounceWith = ancestors == null ? alert : Stream.concat(ancestors.stream(), Stream.of(alert))
            .filter(ancestor -> ancestor.getDebounceTime() > 0)
            .max(Comparator.comparingInt(Alert::getDebounceTime))
            .orElse(alert);

        // If the alert hasn't been fired yet, or has been enough time, set the last trigger time to now and fire.
        if (!this.lastTriggered.containsKey(alertToDebounceWith) || Instant.now().compareTo(this.lastTriggered.get(alertToDebounceWith).plusMillis(alertToDebounceWith.getDebounceTime())) >= 0) {
            SwingUtilities.invokeLater(() -> {
                this.historyPanelProvider.get().addEntry(alert, triggerValues);
            });
            this.lastTriggered.put(alertToDebounceWith, Instant.now());
            alert.getNotifications().forEach(notification -> notification.fire(triggerValues));
        }
    }
}
