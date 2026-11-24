package io.github.rifa456.msed.vocabulary;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The integer ids that tick records use in place of names. Blocks, items, entity types, sounds and
 * effects get their game registry id. Screens without a registered menu, tags and events get a
 * {@link HashIds hash id}. The vocabulary remembers each name it hashed during the session, so
 * {@code vocabulary.json} can map the id back. Any thread may call it.
 */
public class Vocabulary {
    private static final Logger LOG = LoggerFactory.getLogger(Vocabulary.class);
    /** The mapping namespace a released game runs in. */
    private static final String INTERMEDIARY = "intermediary";

    private static final Vocabulary INSTANCE = new Vocabulary();
    /** The id for no value, for example no attacker or an empty tag. Registry ids are never negative. */
    public static final int NONE = -1;

    private final Map<Integer, String> seenScreens = new ConcurrentHashMap<>();
    private final Map<Integer, String> seenTags = new ConcurrentHashMap<>();
    private final Map<Integer, String> seenEvents = new ConcurrentHashMap<>();

    /**
     * Returns the vocabulary of this client.
     *
     * @return the only vocabulary
     */
    public static Vocabulary getInstance() {
        return INSTANCE;
    }

    /** Forgets the hashed names of the previous session. Called when a session starts. */
    public void resetCache() {
        seenScreens.clear();
        seenTags.clear();
        seenEvents.clear();
    }

    /**
     * Records the name of an event's type, as {@code event.<payload field>}, so the vocabulary
     * lists it. An event without a payload is ignored.
     *
     * @param event the event about to be queued
     */
    public void traceEvent(io.github.rifa456.msed.proto.v1.Event event) {
        var caseEnum = event.getPayloadCase();
        if (caseEnum == io.github.rifa456.msed.proto.v1.Event.PayloadCase.PAYLOAD_NOT_SET) {
            return;
        }
        int fieldNumber = caseEnum.getNumber();
        String eventName =
                event.getDescriptorForType().findFieldByNumber(fieldNumber).getName();
        traceDynamicEvent("event." + eventName);
    }

    private void traceDynamicEvent(String eventName) {
        getHashId(eventName, seenEvents);
    }

    /**
     * Returns the id of a game value. Items and item stacks get the item registry id, blocks and
     * block states the block registry id, and entity types, sounds and effects their own registry
     * id. A container screen with a registered menu type gets that menu's registry id, and any
     * other screen the hash of its class name. Identifiers and strings get a hash id. Any other
     * type gets the hash of its {@code toString()}, with a logged warning.
     *
     * @param key the value to look up, or {@code null}
     * @return the value's id, or {@link #NONE} for {@code null} or an empty string
     */
    public int getId(@Nullable Object key) {
        return switch (key) {
            case null -> NONE;
            case Item item -> getRegistryId(BuiltInRegistries.ITEM, item);
            case ItemStack stack -> getRegistryId(BuiltInRegistries.ITEM, stack.getItem());
            case Block block -> getRegistryId(BuiltInRegistries.BLOCK, block);
            case BlockState state -> getRegistryId(BuiltInRegistries.BLOCK, state.getBlock());
            case EntityType<?> type -> getRegistryId(BuiltInRegistries.ENTITY_TYPE, type);
            case SoundEvent sound -> getRegistryId(BuiltInRegistries.SOUND_EVENT, sound);
            case MobEffect effect -> getRegistryId(BuiltInRegistries.MOB_EFFECT, effect);
            case Screen screen -> getScreenId(screen);
            case Identifier id -> getSemanticTagId(id.toString());
            case String str -> getSemanticTagId(str);
            default -> {
                LOG.warn(
                        "No vocabulary id for type {}; hashing its toString()",
                        key.getClass().getSimpleName());
                yield getSemanticTagId(key.toString());
            }
        };
    }

    private <T> int getRegistryId(net.minecraft.core.Registry<T> registry, T value) {
        return registry.getId(value);
    }

    /**
     * Returns the block registry id of air.
     *
     * @return the id of {@code minecraft:air}
     */
    public int getAirBlockId() {
        return getRegistryId(BuiltInRegistries.BLOCK, Blocks.AIR);
    }

    /**
     * Container screens backed by a registered menu type get that menu's registry id; other
     * screens get the hash of their class's intermediary name. The vocabulary file lists both
     * kinds.
     */
    private int getScreenId(Screen screen) {
        if (screen == null) return NONE;
        if (screen instanceof AbstractContainerScreen<?> handled) {
            try {
                MenuType<?> type = handled.getMenu().getType();
                Identifier key = BuiltInRegistries.MENU.getKey(type);
                if (key != null) {
                    int id = BuiltInRegistries.MENU.getId(type);
                    seenScreens.putIfAbsent(id, key.toString());
                    return id;
                }
            } catch (UnsupportedOperationException ignored) {
                // Menus without a type (the player inventory) fall through to the class name.
            }
        }
        return getHashId(intermediaryName(screen.getClass()), seenScreens);
    }

    /**
     * Returns a class's name in Fabric's intermediary mappings, such as
     * {@code net.minecraft.class_408}. A released game runs with these names, while the
     * development environment uses Mojang's, so this is the name both share. A class the
     * mappings do not cover, such as another mod's screen, keeps its own name.
     */
    private static String intermediaryName(Class<?> type) {
        return FabricLoader.getInstance().getMappingResolver().unmapClassName(INTERMEDIARY, type.getName());
    }

    /**
     * Returns the registry id of an entity type given by name.
     *
     * @param entityTypeKey the entity type, as {@code namespace:path}, or {@code null}
     * @return the registry id, or {@link #NONE} if the name is {@code null}, malformed or not
     *     registered
     */
    public int entityTypeId(@Nullable String entityTypeKey) {
        if (entityTypeKey == null) return NONE;
        Identifier id = Identifier.tryParse(entityTypeKey);
        if (id == null) return NONE;
        return BuiltInRegistries.ENTITY_TYPE
                .getOptional(id)
                .map(BuiltInRegistries.ENTITY_TYPE::getId)
                .orElse(NONE);
    }

    private int getSemanticTagId(String tag) {
        if (tag == null || tag.isEmpty()) return NONE;
        return getHashId(tag, seenTags);
    }

    private int getHashId(String identifier, Map<Integer, String> cache) {
        int hash = HashIds.of(identifier);
        cache.putIfAbsent(hash, identifier);
        return hash;
    }

    /**
     * Returns the screens seen in this session.
     *
     * @return a live map from screen id to menu type or screen class name
     */
    public Map<Integer, String> getSeenScreens() {
        return seenScreens;
    }

    /**
     * Returns the tags hashed in this session.
     *
     * @return a live map from hash id to tag
     */
    public Map<Integer, String> getSeenTags() {
        return seenTags;
    }

    /**
     * Returns the event types seen in this session.
     *
     * @return a live map from hash id to event name
     */
    public Map<Integer, String> getSeenEvents() {
        return seenEvents;
    }
}
