package uk.co.notnull.pvp;

import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LightningStrike;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;

public final class PvP extends JavaPlugin implements Listener {

	private ArenaEvents arenaEvents = null;
	private Configuration config;
    private final List<UUID> pvpEnabled = new ArrayList<>();
    private final Set<UUID> inPvPArena = new HashSet<>();
	private final Map<Player, Instant> lastDamage = new HashMap<>();
	private final Map<Player, Instant> lastMessage = new HashMap<>();
	private final Map<UUID, Instant> lastToggle = new HashMap<>();
	private Placeholders placeholders;

	public static final List<PotionEffectType> positiveEffects = List.of(
			PotionEffectType.ABSORPTION,
			PotionEffectType.CONDUIT_POWER,
			PotionEffectType.RESISTANCE,
			PotionEffectType.DOLPHINS_GRACE,
			PotionEffectType.HASTE,
			PotionEffectType.FIRE_RESISTANCE,
			PotionEffectType.INSTANT_HEALTH,
			PotionEffectType.HEALTH_BOOST,
			PotionEffectType.HERO_OF_THE_VILLAGE,
			PotionEffectType.STRENGTH,
			PotionEffectType.JUMP_BOOST,
			PotionEffectType.LUCK,
			PotionEffectType.NIGHT_VISION,
			PotionEffectType.REGENERATION,
			PotionEffectType.SATURATION,
			PotionEffectType.SLOW_FALLING,
			PotionEffectType.SPEED,
			PotionEffectType.WATER_BREATHING,
			PotionEffectType.BREATH_OF_THE_NAUTILUS
	);

	NamespacedKey responsibleKey;

	@Override
	public void onEnable() {
		responsibleKey = new NamespacedKey(this, "responsible");

		// Plugin startup logic
		getServer().getPluginManager().registerEvents(this, this);
		getServer().getPluginManager().registerEvents(new Events(this), this);
		initConfig();
		loadPvPStates();

		LifecycleEventManager<@NotNull Plugin> manager = getLifecycleManager();
        manager.registerEventHandler(LifecycleEvents.COMMANDS,
									 event -> new Commands(this, event.registrar()));

		getServer().getScheduler().scheduleSyncRepeatingTask(this, () -> {
			Iterator<Player> iterator = lastDamage.keySet().iterator();

			while (iterator.hasNext()) {
				Player player = iterator.next();

				if(getRemainingPvPCooldown(player) == 0) {
					player.sendMessage(Messages.getComponent("safe-to-leave"));
					iterator.remove();
				}
			}
		}, 1L, 1L);

		getServer().getScheduler().scheduleSyncRepeatingTask(this, this::savePvPStates, 300L, 300L);

		if(getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
			placeholders = new Placeholders(this);
			placeholders.register();
		}
	}

	@Override
	public void onDisable() {
		savePvPStates();

		if(placeholders != null) {
			placeholders.unregister();
		}
	}

	@EventHandler
	public void onPluginEnable(PluginEnableEvent event) {
		if (event.getPlugin().getName().equals("WorldGuard")) {
			getLogger().info("Initialising WorldGuard handler");
			arenaEvents = new ArenaEvents(this);
		}
	}

	@EventHandler
	public void onPluginDisable(PluginDisableEvent event) {
		if (event.getPlugin().getName().equals("WorldGuard")) {
			if (arenaEvents != null) {
				getLogger().info("Disabling WorldGuard handler");
				arenaEvents = null;
			}
		}
	}

	public void initConfig() {
		config = getConfig();

		Configuration defaults = new MemoryConfiguration();

		defaults.addDefault("pvp-timeout", 30);
		defaults.setComments("pvp-timeout", List.of(
				"The number of seconds that must pass without a player giving or receiving PvP damage,",
				"in order that player to be able to leave the server without punishment."));

		defaults.addDefault("pvp-arenas", Collections.emptyList());
		defaults.setComments("pvp-arenas", List.of(
				"Worldguard regions considered PvP arenas"));

		config.setDefaults(defaults);
		saveDefaultConfig();

		if(!new File(getDataFolder(), "data.yml").exists()) {
			saveResource("data.yml", false);
		}

		if(!new File(getDataFolder(), "messages.yml").exists()) {
			saveResource("messages.yml", false);
		}

		Configuration messages = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "messages.yml"));
		Messages.set(messages);

		if (arenaEvents != null) {
			arenaEvents.reload();
		}
	}

	/**
	 * Determines whether a PvP attempt is allowed based on the status of the involved players
	 * A message will be sent to the attacking player if the PvP attempt is not allowed
	 * @param attacker - The attacker
	 * @param victim - The victim
	 * @return Whether the PvP attempt is allowed
	 */
	public boolean checkPvPAttempt(OfflinePlayer attacker, Player victim) {
		return checkPvPAttempt(attacker, victim, true);
	}

	/**
	 * Determines whether a PvP attempt is allowed based on the status of the involved players
	 * @param attacker - The attacker
	 * @param victim - The victim
	 * @param sendMessages - Whether to send messages to the attacking player if the PvP attempt is not allowed
	 * @return Whether the PvP attempt is allowed
	 */
	public boolean checkPvPAttempt(OfflinePlayer attacker, Player victim, boolean sendMessages) {
		if(attacker.equals(victim)) {
			return true;
		}

		if(!attacker.isOnline()) {
			return false;
		}

		// Ensure we have valid attacker instance
		Player onlineAttacker = getServer().getPlayer(attacker.getUniqueId());
		if (onlineAttacker == null) {
			return false;
		}

		// Check arena status as we can't trust movement events alone
		arenaEvents.checkArenaStatus((Player) attacker);
		arenaEvents.checkArenaStatus(victim);

		if (inPvPArena.contains(attacker.getUniqueId()) && inPvPArena.contains(victim.getUniqueId())) {
			return true;
		}

		if (inPvPArena.contains(attacker.getUniqueId()) && !inPvPArena.contains(victim.getUniqueId())) {
			if(sendMessages) {
				sendDenyMessage("errors.cannot-damage-target-not-in-arena", onlineAttacker, victim);
			}

			return false;
		}

		if (!inPvPArena.contains(attacker.getUniqueId()) && inPvPArena.contains(victim.getUniqueId())) {
			if(sendMessages ) {
				sendDenyMessage("errors.cannot-damage-target-in-arena", onlineAttacker, victim);
			}

			return false;
		}

		if(!pvpEnabled.contains(attacker.getUniqueId())) {
			if(sendMessages) {
				sendDenyMessage("errors.cannot-damage-pvp-disabled", onlineAttacker, victim);
			}

			return false;
		}

		if(!pvpEnabled.contains(victim.getUniqueId())) {
			if(sendMessages) {
				sendDenyMessage("errors.cannot-damage-target-pvp-disabled", onlineAttacker, victim);
			}

			return false;
		}

		return true;
	}

	private void sendDenyMessage(String key, Player player, Player target) {
		if (!player.canSee(target) || !checkMessageCooldown(player)) {
			return;
		}

		lastMessage.put(player, Instant.now());
		player.sendMessage(Messages.getComponent(key,
												 Collections.emptyMap(),
												 Collections.singletonMap("player", target.displayName())));
	}

	private boolean checkMessageCooldown(Player target) {
		return lastMessage.getOrDefault(target, Instant.EPOCH)
				.isBefore(Instant.now().minusSeconds(2));
	}

	List<Player> getNearbyProtectedPlayers(OfflinePlayer player, Location location) {
		return getNearbyProtectedPlayers(player, location, 3);
	}

	/**
	 * Returns players within range of the given location who are protected from PvP
	 * The given player is used for visibility checks and for their current PvP state.
	 * All nearby players will be considered protected if the given player has PvP disabled.
	 * @param player The player to use for visibility checks
	 * @param location The location to check
	 * @param range The range to check
	 * @return A list of any pvp protected players in range
	 */
	List<Player> getNearbyProtectedPlayers(OfflinePlayer player, Location location, int range) {
		double rangeSquared = Math.pow(range, 2);

		return location.getWorld().getPlayers().stream()
				.filter(otherPlayer -> {
					if(otherPlayer.equals(player)) {
						return false;
					}

					if (otherPlayer.getGameMode() == GameMode.CREATIVE || otherPlayer.getGameMode() == GameMode.SPECTATOR) {
						return false;
					}

					if (otherPlayer.getLocation().distanceSquared(location) >= rangeSquared) {
						return false;
					}

					if(player instanceof Player onlinePlayer && !onlinePlayer.canSee(otherPlayer)) {
						return false;
					}

					if(!player.isOnline()) {
						return true;
					}

					return !checkPvPAttempt(player, otherPlayer);
				})
				.sorted((Player player1, Player player2) -> {
					double player1Distance = player1.getLocation().distanceSquared(location);
					double player2Distance = player2.getLocation().distanceSquared(location);

					if(player1Distance < player2Distance) {
						return -1;
					} else if(player1Distance > player2Distance) {
						return 1;
					}

					return 0;
				}).collect(Collectors.toList());
	}

	/**
	 * Returns whether the given player has PvP enabled
	 * @param player The player to check
	 * @return Whether PvP is enabled
	 */
	public boolean hasPvPEnabled(Player player) {
		return pvpEnabled.contains(player.getUniqueId());
	}

	/**
	 * Returns whether the given player is in a PvP arena
	 * @param player The player to check
	 * @return Whether the player is in a PvP arena
	 */
	public boolean isInPvPArena(Player player) {
		return inPvPArena.contains(player.getUniqueId());
	}

	/**
	 * Returns whether the given location is in a PvP arena
	 * @param location The location to check
	 * @return Whether the location is in a PvP arena
	 */
	public boolean isInPvPArena(Location location) {
		return arenaEvents != null && arenaEvents.isInPvPArena(location);
	}

	/**
	 * Returns the last time the given player last engaged in PvP
	 * If the player has never engaged in PvP, the epoch time will be returned
	 * @param player The player to check
	 * @return The last PvP time
	 */
	public Instant getLastPvPTime(Player player) {
		return lastDamage.getOrDefault(player, Instant.EPOCH);
	}

	/**
	 * Returns the remaining time until the given player is no longer considered "in PvP"
	 * @param player The player to check
	 * @return The remaining time
	 */
	public long getRemainingPvPCooldown(Player player) {
		return Math.max(0, config.getInt("pvp-timeout") - getLastPvPTime(player).until(Instant.now(), ChronoUnit.SECONDS));
	}

	/**
	 * Returns the last time the given player toggled their PvP state
	 * If the player has never toggled their state, the epoch time will be returned
	 * @param player The player to check
	 * @return The last toggle time
	 */
	public Instant getLastToggleTime(Player player) {
		return lastToggle.getOrDefault(player.getUniqueId(), Instant.EPOCH);
	}

	/**
	 * Returns the remaining time until the given player is no longer considered to have recently toggled PvP
	 * @param player The player to check
	 * @return The remaining time
	 */
	public long getRemainingToggleCooldown(Player player) {
		return Math.max(0, config.getInt("pvp-timeout") - getLastToggleTime(player).until(Instant.now(), ChronoUnit.SECONDS));
	}

	/**
	 * Records a PvP event for the given players
	 * Both players will have their last PvP times set to the current time
	 * @param attacker The attacker
	 * @param victim The victim
	 */
	public void recordPvP(Player attacker, Player victim) {
		if(attacker.equals(victim)) {
			return;
		}

		Instant time = Instant.now();
		lastDamage.put(attacker, time);
		lastDamage.put(victim, time);
	}

	/**
	 * Toggles the PvP enabled state of the given player
	 * @param player The player to toggle
	 * @return The player's new PvP state
	 */
	public boolean togglePvP(@NotNull Player player) {
		clearPlayer(player);
		lastToggle.put(player.getUniqueId(), Instant.now());

		if(pvpEnabled.contains(player.getUniqueId())) {
			pvpEnabled.remove(player.getUniqueId());
			broadcastPvPStatus(player);
			return false;
		} else {
			pvpEnabled.add(player.getUniqueId());
			broadcastPvPStatus(player);
			return true;
		}
	}

	/**
	 * Sets whether the given player is in a PvPArena
	 * @param player The player
	 * @param state Whether the player is in a PvP arena
	 */
	void setInPvPArena(@NotNull Player player, boolean state) {
		getLogger().info(player.getName() + (state ? " entered " : " left ") + "a PvP arena");

		if (state) {
			inPvPArena.add(player.getUniqueId());
		} else {
			if (inPvPArena.remove(player.getUniqueId())) {
				clearPlayer(player);

				// Warn PvP is still enabled
				if (pvpEnabled.contains(player.getUniqueId())) {
					player.sendMessage(Messages.getComponent("self-pvp-still-enabled"));
				}
			}
		}
	}

	private void broadcastPvPStatus(Player player) {
		Component message = Messages.getComponent(hasPvPEnabled(player) ? "notify-pvp-enabled" : "notify-pvp-disabled",
												  Collections.emptyMap(),
												  Collections.singletonMap("player", player.displayName()));

		getLogger().info(player.getName() + (hasPvPEnabled(player) ? " enabled " : " disabled ") + "PvP");

		for (Player onlinePlayer : getServer().getOnlinePlayers()) {
			if(!onlinePlayer.equals(player) && onlinePlayer.canSee(player)) {
				onlinePlayer.sendMessage(message);
			}
		}
	}

	/**
	 * Clears the state of the given player. Their PvP status will not be affected.
	 * @param player The player to clear
	 */
	public void clearPlayer(Player player) {
		lastDamage.remove(player);
		lastMessage.remove(player);
		lastToggle.remove(player.getUniqueId());
	}

	/**
	 * Returns the player "responsible" for the given DamageSoruce, if any
	 * @param DamageSource The damageSource to check
	 * @return The player
	 */
	Optional<OfflinePlayer> getResponsiblePlayer(DamageSource damageSource) {
		return getResponsiblePlayer(damageSource.getCausingEntity());
	}

	/**
	 * Returns the player "responsible" for the given entity, if any
	 * If the entity is a player then the player is returned
	 * If the entity is a projectile then the shooter is returned if it was a player
	 * If the entity is tnt then the source is returned if it was a player
	 * If the entity is a wolf then the owner is returned if is a player
	 * @param entity The entity to check
	 * @return The player
	 */
	Optional<OfflinePlayer> getResponsiblePlayer(Entity entity) {
		if(entity instanceof Player player) {
			return Optional.of(player);
		}

		if(entity instanceof Projectile projectile) {
			if(projectile.getShooter() instanceof Player player) {
				return Optional.of(player);
			}
		}

		if(entity instanceof AreaEffectCloud cloud) {
			if(cloud.getSource() instanceof Player player) {
				return Optional.of(player);
			}
		}

		if(entity instanceof TNTPrimed tnt) {
			if(tnt.getSource() instanceof Player player) {
				return Optional.of(player);
			}
		}

		if(entity instanceof Tameable tameable) {
			if(tameable.getOwner() instanceof Player player) {
				return Optional.of(player);
			}
		}

		if(entity instanceof LightningStrike lightning) {
			if(lightning.getCausingEntity() instanceof Player player) {
				return Optional.of(player);
			}
		}

		try {
			String responsible = entity.getPersistentDataContainer().get(responsibleKey, PersistentDataType.STRING);

			if (responsible != null) {
				UUID uuid = UUID.fromString(responsible);
				return Optional.of(getServer().getOfflinePlayer(uuid));
			}

		} catch (IllegalArgumentException ignored) {
			return Optional.empty();
		}

		return Optional.empty();
	}

	private void loadPvPStates() {
		pvpEnabled.clear();

		FileConfiguration data = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "data.yml"));

		List<?> enabled = data.getList("pvp-enabled", Collections.emptyList());

		for (Object uuid : enabled) {
			try {
				pvpEnabled.add(UUID.fromString(uuid.toString()));
			} catch(IllegalArgumentException e) {
				getLogger().warning("Ignoring invalid uuid in pvp-enabled config: " + uuid);
			}
		}
	}

	private void savePvPStates() {
		File dataFile = new File(getDataFolder(), "data.yml");
		FileConfiguration data = new YamlConfiguration();

		data.set("pvp-enabled", pvpEnabled.stream().map(UUID::toString).collect(Collectors.toList()));

		try {
			data.save(dataFile);
		} catch (IOException e) {
			getLogger().log(Level.SEVERE, "Failed to save player PvP statuses", e);
		}
	}

	void reload() {
		reloadConfig();
		initConfig();
	}
}
