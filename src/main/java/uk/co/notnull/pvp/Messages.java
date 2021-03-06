package uk.co.notnull.pvp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Collections;
import java.util.Map;

public class Messages {
    private static ConfigurationSection messages;
    public static final MiniMessage miniMessage = MiniMessage.miniMessage();

    public static void set(ConfigurationSection messages) {
        Messages.messages = messages;
    }

    public static String get(String id) {
        return get(id, Collections.emptyMap());
    }

    public static String get(String id, Map<String, String> replacements) {
        if(messages == null) {
            return "";
        }

        String message = messages.getString(id, "Message " + id + " does not exist");

        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            message = message.replace(entry.getKey(), entry.getValue());
        }

        return message;
    }

    public static Component getComponent(String id) {
        return getComponent(id, Collections.emptyMap(), Collections.emptyMap());
    }

    public static Component getComponent(String id, Map<String, String> stringReplacements, Map<String, ComponentLike> componentReplacmenets) {
        if(messages == null) {
            return Component.empty();
        }

        String message =  messages.getString(id, "Message " + id + " does not exist");
        TagResolver.Builder placeholders = TagResolver.builder();

        for (Map.Entry<String, String> entry : stringReplacements.entrySet()) {
            placeholders.resolver(Placeholder.parsed(entry.getKey(), entry.getValue()));
        }

        for (Map.Entry<String, ComponentLike> entry : componentReplacmenets.entrySet()) {
            placeholders.resolver(Placeholder.component(entry.getKey(), entry.getValue()));
        }

        return miniMessage.deserialize(message, placeholders.build());
    }
}

