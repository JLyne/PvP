package uk.co.notnull.pvp;

import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionType;

import java.util.*;

public class Events implements Listener {
	private final PvP plugin;

	public Events(PvP plugin) {
		this.plugin = plugin;
	}

	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent event) {
		//Warn player if PvP is still enabled
		if(plugin.hasPvPEnabled(event.getPlayer())) {
			event.getPlayer().sendMessage(Messages.getComponent("self-pvp-still-enabled"));
		}
	}

	@EventHandler
	public void onPlayerLeave(PlayerQuitEvent event) {
		//Kill player if they are abandoning a fight
		if(plugin.getRemainingPvPCooldown(event.getPlayer()) > 0) {
			event.getPlayer().setHealth(0);
			plugin.getServer().broadcast(
					Messages.getComponent("notify-pvp-punish", Collections.emptyMap(),
										  Collections.singletonMap("player", event.getPlayer().displayName())));
		}

		plugin.clearPlayer(event.getPlayer());
	}

	@EventHandler(ignoreCancelled = true)
	public void onPlayerDamage(EntityDamageByEntityEvent event) {
		if(event.getEntity() instanceof Player victim) {
			//Prevent damage if either player has PvP disabled
			Optional<OfflinePlayer> attacker = plugin.getResponsiblePlayer(event.getDamager());

			if(attacker.isPresent()) {
				if(!plugin.checkPvPAttempt(attacker.get(), victim)) {
					event.setCancelled(true);

					if(event.getDamager() instanceof Tameable) {
						((Tameable) event.getDamager()).setTarget(null);
					}
				}

				return;
			}
		}

		if(event.getEntity() instanceof EnderCrystal crystal) {
			plugin.getResponsiblePlayer(event.getDamager()).ifPresent(attacker -> {
				crystal.setMetadata("responsible", new FixedMetadataValue(plugin, attacker.getUniqueId()));
			});
		}
	}

	@EventHandler(ignoreCancelled = true)
	public void onEntityCombust(EntityCombustByEntityEvent event) {
		if(event.getEntity() instanceof ExplosiveMinecart minecart) {
			plugin.getResponsiblePlayer(event.getCombuster()).ifPresent(attacker -> {
				minecart.setMetadata("responsible", new FixedMetadataValue(plugin, attacker.getUniqueId()));
			});
		}
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onPlayerDamaged(EntityDamageByEntityEvent event) {
		if(!(event.getEntity() instanceof Player victim)) {
			return;
		}

		//Record PvP damage
		plugin.getResponsiblePlayer(event.getDamager()).ifPresent(attacker -> {
			if(attacker instanceof Player onlinePlayer) {
				plugin.recordPvP(onlinePlayer, victim);
			}
		});
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onPlayerDeath(EntityDeathEvent event) {
		if(!(event.getEntity() instanceof Player victim)) {
			return;
		}

		plugin.clearPlayer(victim);
	}

	@EventHandler(ignoreCancelled = true)
	public void onBlockIgnite(BlockIgniteEvent event) {
		if(!(event.getIgnitingEntity() instanceof LightningStrike lightning) || lightning.getCausingEntity() == null) {
			return;
		}

		plugin.getResponsiblePlayer(lightning.getCausingEntity()).ifPresent(attacker -> {
			if(!plugin.getNearbyProtectedPlayers(attacker, lightning.getLocation()).isEmpty()) {
				event.setCancelled(true);
			}
 		});
	}


	@EventHandler(ignoreCancelled = true)
	public void onPotionSplash(PotionSplashEvent event) {
		//Ignore potions with only positive effects
		if(event.getPotion().getEffects().stream().allMatch(effect -> PvP.positiveEffects.contains(effect.getType()))) {
			return;
		}

		//Prevent potion effect application if PvP isn't allowed
		plugin.getResponsiblePlayer(event.getEntity()).ifPresent(attacker -> {
			for (LivingEntity affectedEntity : event.getAffectedEntities()) {
				if(affectedEntity instanceof Player victim) {
					if(!plugin.checkPvPAttempt(attacker, victim)) {
						event.setIntensity(victim, 0.0);
					} else if (attacker instanceof Player onlinePlayer) {
						plugin.recordPvP(onlinePlayer, victim);
					}
				}
			}
		});
	}

	@EventHandler(ignoreCancelled = true)
	public void onPotionLinger(AreaEffectCloudApplyEvent event) {
		AreaEffectCloud cloud = event.getEntity();

		Optional<OfflinePlayer> attacker = plugin.getResponsiblePlayer(event.getEntity());

		if(attacker.isEmpty()) {
			return;
		}

		PotionType basePotionType = cloud.getBasePotionType();
		List<PotionEffect> effects = new ArrayList<>();

		if(cloud.hasCustomEffects()) {
			effects.addAll(cloud.getCustomEffects());
		}

		if(basePotionType != null) {
			effects.addAll(basePotionType.getPotionEffects());
		}

		//Ignore clouds with only positive effects
		if(effects.stream().allMatch(effect -> PvP.positiveEffects.contains(effect.getType()))) {
			return;
		}

		//Prevent potion effect application if PvP isn't allowed
		Iterator<LivingEntity> iterator = event.getAffectedEntities().iterator();

		while(iterator.hasNext()) {
			LivingEntity affectedEntity = iterator.next();

			if(affectedEntity instanceof Player victim) {
				if(!plugin.checkPvPAttempt(attacker.get(), victim)) {
					iterator.remove();
				} else if (attacker.get() instanceof Player onlinePlayer) {
					plugin.recordPvP(onlinePlayer, victim);
				}
			}
		}
	}

	@EventHandler(ignoreCancelled = true)
	public void onEntityTarget(EntityTargetLivingEntityEvent event) {
		if(!(event.getTarget() instanceof Player target) || !(event.getEntity() instanceof Tameable entity)) {
			return;
		}

		if(!(entity.getOwner() instanceof Player owner)) {
			return;
		}

		//Prevent pets targeting players if PvP isn't allowed
		if(!plugin.hasPvPEnabled(target) || !plugin.hasPvPEnabled(owner)) {
			event.setCancelled(true);
		}
	}

	@EventHandler(ignoreCancelled = true)
	public void onBlockPlace(BlockPlaceEvent event) {
		Player player = event.getPlayer();
		Block block = event.getBlockPlaced();

		if (block.getType() != Material.FIRE) {
			return;
		}

		//Prevent placing fire near other players if PvP isn't allowed
		List<Player> nearby = plugin.getNearbyProtectedPlayers(player, block.getLocation());

		if(!nearby.isEmpty()) {
			event.setCancelled(true);

			if(!plugin.hasPvPEnabled(player)) {
				player.sendMessage(
						Messages.getComponent("errors.cannot-ignite-pvp-disabled", Collections.emptyMap(),
											  Collections.singletonMap("player", nearby.getFirst().displayName())));
			} else {
				player.sendMessage(
						Messages.getComponent("errors.cannot-ignite-nearby-pvp-disabled", Collections.emptyMap(),
											  Collections.singletonMap("player", nearby.getFirst().displayName())));
			}
		}
	}

	@EventHandler(ignoreCancelled = true)
	public void onBucketEmpty(PlayerBucketEmptyEvent event) {
		if (event.getBucket() != Material.LAVA_BUCKET) {
			return;
		}

		Player player = event.getPlayer();

		//Prevent placing lava near other players if PvP isn't allowed
		List<Player> nearby = plugin.getNearbyProtectedPlayers(player, event.getBlock().getLocation());

		if(!nearby.isEmpty()) {
			event.setCancelled(true);

			if(!plugin.hasPvPEnabled(player)) {
				player.sendMessage(
						Messages.getComponent("errors.cannot-lava-pvp-disabled", Collections.emptyMap(),
											  Collections.singletonMap("player", nearby.getFirst().displayName())));
			} else {
				player.sendMessage(
						Messages.getComponent("errors.cannot-lava-nearby-pvp-disabled", Collections.emptyMap(),
											  Collections.singletonMap("player", nearby.getFirst().displayName())));
			}
		}
	}
}
