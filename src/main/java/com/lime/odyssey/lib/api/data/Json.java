package com.lime.odyssey.lib.api.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.util.UUID;

/**
 * Shared Gson instance for every Odyssey data and config file.
 * ItemStacks are written as SNBT strings, ResourceLocations and UUIDs as strings.
 */
public final class Json {
    public static final Gson GSON = builder().setPrettyPrinting().create();
    public static final Gson COMPACT = builder().create();

    private Json() {}

    public static GsonBuilder builder() {
        return new GsonBuilder()
                .disableHtmlEscaping()
                .serializeSpecialFloatingPointValues()
                .registerTypeHierarchyAdapter(ItemStack.class, new ItemStackAdapter().nullSafe())
                .registerTypeAdapter(ResourceLocation.class, new ResourceLocationAdapter().nullSafe())
                .registerTypeAdapter(UUID.class, new UuidAdapter().nullSafe());
    }

    private static final class ItemStackAdapter extends TypeAdapter<ItemStack> {
        @Override
        public void write(JsonWriter out, ItemStack value) throws IOException {
            out.value(ItemCodec.encode(value));
        }

        @Override
        public ItemStack read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return ItemStack.EMPTY;
            }
            return ItemCodec.decode(in.nextString());
        }
    }

    private static final class ResourceLocationAdapter extends TypeAdapter<ResourceLocation> {
        @Override
        public void write(JsonWriter out, ResourceLocation value) throws IOException {
            out.value(value.toString());
        }

        @Override
        public ResourceLocation read(JsonReader in) throws IOException {
            return ResourceLocation.tryParse(in.nextString());
        }
    }

    private static final class UuidAdapter extends TypeAdapter<UUID> {
        @Override
        public void write(JsonWriter out, UUID value) throws IOException {
            out.value(value.toString());
        }

        @Override
        public UUID read(JsonReader in) throws IOException {
            String s = in.nextString();
            try {
                return UUID.fromString(s);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }
}
