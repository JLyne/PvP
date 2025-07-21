package uk.co.notnull.pvp;

import java.util.Collections;
import java.util.List;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import static io.papermc.paper.command.brigadier.Commands.argument;
import static io.papermc.paper.command.brigadier.Commands.literal;

public final class Commands {
	private final PvP plugin;

	public Commands(PvP plugin, io.papermc.paper.command.brigadier.Commands registrar) {
		this.plugin = plugin;

		LiteralCommandNode<CommandSourceStack> toggleCommand = literal("pvp")
				.requires(source -> source.getSender().hasPermission("pvp.toggle"))
				.executes(this::toggleSelf)
				.then(argument("player", ArgumentTypes.players())
							  .requires(source -> source.getSender().hasPermission("pvp.toggle.other"))
							  .executes(this::toggleOther))
				.build();

		LiteralCommandNode<CommandSourceStack> infoCommand = literal("pvpinfo")
				.requires(source -> source.getSender().hasPermission("pvp.info"))
				.executes(this::infoSelf)
				.then(argument("player", ArgumentTypes.players())
							  .requires(source -> source.getSender().hasPermission("pvp.info.other"))
							  .executes(this::infoOther))
				.build();

		LiteralCommandNode<CommandSourceStack> reloadCommand = literal("pvpreload")
				.requires(source -> source.getSender().hasPermission("pvp.reload"))
				.executes(this::reload)
				.build();

		registrar.register(toggleCommand, "Toggle PvP");
		registrar.register(infoCommand, "Show PvP status");
		registrar.register(reloadCommand, "Reload PvP configuration");
	}

    private int toggleSelf(CommandContext<CommandSourceStack> ctx) {
		CommandSender sender = ctx.getSource().getSender();

		if (!(sender instanceof Player player)) {
			sender.sendMessage(Messages.getComponent("errors.not-a-player"));
			return 0;
		}

		if (plugin.isInPvPArena(player)) {
			player.sendMessage(Messages.getComponent("errors.cannot-toggle-in-arena"));
			return 0;
		}

		if(plugin.hasPvPEnabled(player)) {
			long toggleCooldown = plugin.getRemainingToggleCooldown(player);
			long pvpCooldown = plugin.getRemainingPvPCooldown(player);

			if(toggleCooldown > 0 && toggleCooldown > pvpCooldown) {
				player.sendMessage(Messages.getComponent("errors.cannot-toggle-command-cooldown",
														 Collections.singletonMap("time", String.valueOf(toggleCooldown)),
														 Collections.emptyMap()));
				return 0;
			}

			if(pvpCooldown > 0 && pvpCooldown > toggleCooldown) {
				player.sendMessage(Messages.getComponent("errors.cannot-toggle-pvp-cooldown",
														 Collections.singletonMap("time", String.valueOf(pvpCooldown)),
														 Collections.emptyMap()));
				return 0;
			}
		}

		if(plugin.togglePvP(player)) {
			player.sendMessage(Messages.getComponent("self-pvp-enabled"));
		} else {
			player.sendMessage(Messages.getComponent("self-pvp-disabled"));
		}

		return Command.SINGLE_SUCCESS;
	}

	private int toggleOther(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		CommandSender sender = ctx.getSource().getSender();
		PlayerSelectorArgumentResolver resolver = ctx.getArgument("player", PlayerSelectorArgumentResolver.class);
		List<Player> players = resolver.resolve(ctx.getSource());

		for (Player target : players) {
			if(plugin.togglePvP(target)) {
				sender.sendMessage(Messages.getComponent("target-pvp-enabled", Collections.emptyMap(),
														 Collections.singletonMap("player", target.displayName())));

				if(!target.equals(sender)) {
					target.sendMessage(Messages.getComponent("pvp-force-enabled",
															 Collections.singletonMap("player", sender.getName()),
															 Collections.emptyMap()));
				}
			} else {
				sender.sendMessage(Messages.getComponent("target-pvp-disabled", Collections.emptyMap(),
														 Collections.singletonMap("player", target.displayName())));

				if(!target.equals(sender)) {
					target.sendMessage(Messages.getComponent("pvp-force-disabled",
															 Collections.singletonMap("player", sender.getName()),
															 Collections.emptyMap()));
				}
			}
		}

		return Command.SINGLE_SUCCESS;
	}

	private int infoSelf(CommandContext<CommandSourceStack> ctx) {
		CommandSender sender = ctx.getSource().getSender();

		if (!(sender instanceof Player player)) {
			sender.sendMessage(Messages.getComponent("errors.not-a-player"));
			return 0;
		}

		String message;

		if (plugin.isInPvPArena(player)) {
			message = "self-info-arena";
		} else if (plugin.hasPvPEnabled(player)) {
			message = "self-info-enabled";
		} else {
			message = "self-info-disabled";
		}

		sender.sendMessage(Messages.getComponent(message));

		return Command.SINGLE_SUCCESS;
	}

	private int infoOther(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		CommandSender sender = ctx.getSource().getSender();
		PlayerSelectorArgumentResolver resolver = ctx.getArgument("player", PlayerSelectorArgumentResolver.class);
		List<Player> players = resolver.resolve(ctx.getSource());

		for (Player target : players) {
			String message;

			if (plugin.isInPvPArena(target)) {
				message = "target-info-arena";
			} else if (plugin.hasPvPEnabled(target)) {
				message = "target-info-enabled";
			} else {
				message = "target-info-disabled";
			}

			sender.sendMessage(Messages.getComponent(message, Collections.emptyMap(),
													 Collections.singletonMap("player", target.displayName())));
		}

		return Command.SINGLE_SUCCESS;
	}

    private int reload(CommandContext<CommandSourceStack> ctx) {
		plugin.reload();
		ctx.getSource().getSender().sendMessage(Messages.getComponent("reloaded"));

		return Command.SINGLE_SUCCESS;
	}
}
