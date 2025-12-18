/*
 * PvP, an opt-in PvP plugin
 *
 * Copyright (c) 2025 James Lyne
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package uk.co.notnull.pvp;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.internal.platform.WorldGuardPlatform;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;

import java.util.List;
import java.util.Set;

public final class ArenaEvents implements Listener {
	private final PvP plugin;
	private final WorldGuardPlatform platform;
	private List<String> arenaRegions;

	public ArenaEvents(PvP plugin) {
		this.plugin = plugin;
		platform = WorldGuard.getInstance().getPlatform();
		plugin.getServer().getPluginManager().registerEvents(this, plugin);

		reload();
	}

	void reload() {
		this.arenaRegions = plugin.getConfig().getStringList("pvp-arenas");
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onPlayerMoved(PlayerMoveEvent event) {
		checkArenaStatus(event.getPlayer(), event.getTo());
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onPlayerTeleported(PlayerTeleportEvent event) {
		checkArenaStatus(event.getPlayer(), event.getTo());
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onVehicleMoved(VehicleMoveEvent event) {
		for (Entity p : event.getVehicle().getPassengers()) {
			if (p instanceof Player player) {
				checkArenaStatus(player, event.getTo());
			}
		}
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onPlayerJoined(PlayerJoinEvent event) {
		checkArenaStatus(event.getPlayer());
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onPlayerRespawned(PlayerRespawnEvent event) {
		checkArenaStatus(event.getPlayer(), event.getRespawnLocation());
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onVehicleEntered(VehicleEnterEvent event) {
		if (event.getEntered() instanceof Player player) {
			checkArenaStatus(player, event.getVehicle().getLocation());
		}
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onEntityMounted(EntityMountEvent event) {
		if (event.getEntity() instanceof Player player) {
			checkArenaStatus(player, event.getMount().getLocation());
		}
	}

	void checkArenaStatus(Player player) {
		checkArenaStatus(player, player.getLocation());
	}

	private void checkArenaStatus(Player player, Location location) {
		boolean inArena = isInPvPArena(location);

		if (inArena != plugin.isInPvPArena(player)) {
			plugin.setInPvPArena(player, inArena);
		}
	}

	boolean isInPvPArena(Location location) {
		com.sk89q.worldedit.util.Location weLocation = BukkitAdapter.adapt(location);

		Set<ProtectedRegion> regions =
				platform.getRegionContainer().createQuery().getApplicableRegions(weLocation).getRegions();

		return regions.stream().anyMatch(r -> arenaRegions.contains(r.getId()));
	}
}
