package com.example.knifeclient;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;

import java.util.List;

public final class Knives {
    public static final String[] IDS = {"karambit", "bayonet", "butterfly", "huntsman", "flip", "falchion"};

    /** A fresh knife stack with the chosen model and tint colour. */
    public static ItemStack current() {
        int i = Math.floorMod(Modules.KNIVES_KNIFE.index, IDS.length);
        int rgb = Modules.KNIVES_COLOR.rgb(0.0);
        ItemStack stack = new ItemStack(Items.STICK);
        stack.set(DataComponentTypes.ITEM_MODEL, Identifier.of("knifeclient", IDS[i]));
        stack.set(DataComponentTypes.CUSTOM_MODEL_DATA,
                new CustomModelDataComponent(List.of(), List.of(), List.of(), List.of(rgb)));
        return stack;
    }
}
