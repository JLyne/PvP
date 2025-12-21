package uk.co.notnull.pvp;

import io.papermc.paper.event.entity.EntityPushedByEntityAttackEvent;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.LightningStrike;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

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
			Optional<OfflinePlayer> attacker = plugin.getResponsiblePlayer(event.getDamageSource());

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
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onPlayerDamaged(EntityDamageByEntityEvent event) {
		if(!(event.getEntity() instanceof Player victim)) {
			return;
		}

		//Record PvP damage
		plugin.getResponsiblePlayer(event.getDamageSource()).ifPresent(attacker -> {
			if(attacker instanceof Player onlinePlayer) {
				plugin.recordPvP(onlinePlayer, victim);
			}
		});
	}

	@EventHandler(ignoreCancelled = true)
	public void onEntityKnockback(EntityPushedByEntityAttackEvent event) {
		if(event.getEntity() instanceof Player victim) {
			//Prevent knockback if either player has PvP disabled
			Optional<OfflinePlayer> attacker = plugin.getResponsiblePlayer(event.getPushedBy());

			attacker.ifPresent(offlinePlayer -> {
				if (!plugin.checkPvPAttempt(offlinePlayer, victim)) {
					event.setCancelled(true);
				}
			});
		}
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onEntityKnockedBack(EntityPushedByEntityAttackEvent event) {
		if(!(event.getEntity() instanceof Player victim)) {
			return;
		}

		//Record PvP damage
		plugin.getResponsiblePlayer(event.getPushedBy()).ifPresent(attacker -> {
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
					}
				}
			}
		});
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onPotionSplashed(PotionSplashEvent event) {
		//Ignore potions with only positive effects
		if(event.getPotion().getEffects().stream().allMatch(effect -> PvP.positiveEffects.contains(effect.getType()))) {
			return;
		}

		//Record PvP
		plugin.getResponsiblePlayer(event.getEntity()).ifPresent(attacker -> {
			for (LivingEntity affectedEntity : event.getAffectedEntities()) {
				if(affectedEntity instanceof Player victim) {
					if (event.getIntensity(victim) > 0 && attacker instanceof Player onlinePlayer) {
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
				}
			}
		}
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onPotionLingered(AreaEffectCloudApplyEvent event) {
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

		//Record PvP
		for (LivingEntity affectedEntity : event.getAffectedEntities()) {
			if (affectedEntity instanceof Player victim) {
				if (attacker.get() instanceof Player onlinePlayer) {
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
		if(!plugin.checkPvPAttempt(owner, target, false)) {
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

		if (plugin.isInPvPArena(event.getBlock().getLocation())) {
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

		if (plugin.isInPvPArena(event.getBlock().getLocation())) {
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
