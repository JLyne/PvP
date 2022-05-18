package uk.co.notnull.pvp;

import cloud.commandframework.Command;
import cloud.commandframework.CommandTree;
import cloud.commandframework.annotations.AnnotationParser;
import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;
import cloud.commandframework.annotations.CommandPermission;
import cloud.commandframework.annotations.specifier.Greedy;
import cloud.commandframework.arguments.parser.ParserParameters;
import cloud.commandframework.bukkit.CloudBukkitCapabilities;
import cloud.commandframework.execution.AsynchronousCommandExecutionCoordinator;
import cloud.commandframework.execution.CommandExecutionCoordinator;
import cloud.commandframework.meta.CommandMeta;
import cloud.commandframework.meta.SimpleCommandMeta;
import cloud.commandframework.minecraft.extras.MinecraftExceptionHandler;
import cloud.commandframework.minecraft.extras.MinecraftHelp;
import cloud.commandframework.paper.PaperCommandManager;

import java.util.Collections;
import java.util.function.Function;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.checkerframework.checker.nullness.qual.NonNull;

public class Commands {
	private final PvP plugin;
	private PaperCommandManager<CommandSender> paperCommandManager;
    private AnnotationParser<CommandSender> annotationParser;
    private MinecraftHelp<CommandSender> minecraftHelp;

	public Commands(PvP plugin) {
		this.plugin = plugin;
		
        final Function<CommandTree<CommandSender>, CommandExecutionCoordinator<CommandSender>> executionCoordinatorFunction =
                AsynchronousCommandExecutionCoordinator.<CommandSender>newBuilder().build();

        try {
            paperCommandManager = new PaperCommandManager<>(
                    plugin,
                    executionCoordinatorFunction,
                    Function.identity(),
                    Function.identity());
        } catch (final Exception e) {
            plugin.getLogger().severe("Failed to initialize the command manager");
            /* Disable the plugin */
            plugin.getServer().getPluginManager().disablePlugin(plugin);
            return;
        }

        //
        // Create the Minecraft help menu system
        //
        this.minecraftHelp = new MinecraftHelp<>(
                /* Help Prefix */ "/pvp",
                /* Audience mapper */ (sender) -> sender,
                /* Manager */ this.paperCommandManager
        );
        //
        // Register Brigadier mappings
        //
        if (paperCommandManager.queryCapability(CloudBukkitCapabilities.BRIGADIER)) {
            paperCommandManager.registerBrigadier();
        }
        //
        // Register asynchronous completions
        //
        if (paperCommandManager.queryCapability(CloudBukkitCapabilities.ASYNCHRONOUS_COMPLETION)) {
            this.paperCommandManager.registerAsynchronousCompletions();
        }

        //
        // Create the annotation parser. This allows you to define commands using methods annotated with
        // @CommandMethod
        //
        final Function<ParserParameters, CommandMeta> commandMetaFunction = p ->
                 SimpleCommandMeta.builder().with(CommandMeta.DESCRIPTION, "No description").build();

        this.annotationParser = new AnnotationParser<>(
                /* Manager */ this.paperCommandManager,
                /* Command sender type */ CommandSender.class,
                /* Mapper for command meta instances */ commandMetaFunction
        );
        //
        // Override the default exception handlers
        //
        new MinecraftExceptionHandler<CommandSender>()
                .withInvalidSyntaxHandler()
                .withInvalidSenderHandler()
                .withNoPermissionHandler()
                .withArgumentParsingHandler()
                .apply(paperCommandManager, (sender) -> sender);

        this.constructCommands();
	}

	private void constructCommands() {
		final Command.Builder<CommandSender> builder = this.paperCommandManager.commandBuilder("pvp");

		this.annotationParser.parse(this);
	}

	@CommandMethod("pvp help [query]")
    @CommandDescription("Help menu")
    private void commandHelp(
            final @NonNull CommandSender sender,
            final @Argument("query") @Greedy String query
    ) {
        this.minecraftHelp.queryCommands(query == null ? "" : query, sender);
    }

	@CommandMethod("pvp")
    @CommandDescription("Toggle your own PvP status")
	@CommandPermission("pvp.toggle")
    private void commandToggle(
            final @NonNull Player player
    ) {
		if(plugin.hasPvPEnabled(player)) {
			long toggleCooldown = plugin.getRemainingToggleCooldown(player);
			long pvpCooldown = plugin.getRemainingPvPCooldown(player);

			if(toggleCooldown > 0 && toggleCooldown > pvpCooldown) {
				player.sendMessage(Messages.getComponent("errors.cannot-toggle-command-cooldown",
														 Collections.singletonMap("time", String.valueOf(toggleCooldown)),
														 Collections.emptyMap()));
				return;
			}

			if(pvpCooldown > 0 && pvpCooldown > toggleCooldown) {
				player.sendMessage(Messages.getComponent("errors.cannot-toggle-pvp-cooldown",
														 Collections.singletonMap("time", String.valueOf(pvpCooldown)),
														 Collections.emptyMap()));
				return;
			}
		}

		if(plugin.togglePvP(player)) {
			player.sendMessage(Messages.getComponent("self-pvp-enabled"));
		} else {
			player.sendMessage(Messages.getComponent("self-pvp-disabled"));
		}
	}

	@CommandMethod("pvp info <player>")
    @CommandDescription("Get info on another player's PvP status")
	@CommandPermission("pvp.info")
    private void commandInfo(
            final @NonNull CommandSender sender,
            final @Argument("player") Player target
    ) {
		if(plugin.hasPvPEnabled(target)) {
			sender.sendMessage(Messages.getComponent("target-info-enabled", Collections.emptyMap(),
													 Collections.singletonMap("player", target.displayName())));
		} else {
			sender.sendMessage(Messages.getComponent("target-info-disabled", Collections.emptyMap(),
													 Collections.singletonMap("player", target.displayName())));
		}
	}

	@CommandMethod("pvp toggle <player>")
    @CommandDescription("Toggles the specified player's PvP status")
	@CommandPermission("pvp.toggle.other")
    private void commandToggleOther(
            final @NonNull CommandSender sender,
            final @Argument("player") Player target
    ) {
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

	@CommandMethod("pvp reload")
    @CommandDescription("Reload the configuration")
	@CommandPermission("pvp.reload")
    private void commandReload(final @NonNull CommandSender sender) {
		plugin.reload();
		sender.sendMessage(Messages.getComponent("reloaded"));
	}
}
