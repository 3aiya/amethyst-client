package com.amethystclient.hud;

import com.amethystclient.ui.Draw;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/**
 * Everything the shared HUD code reads from the game. Each Minecraft version has its own copy that
 * maps these onto that version's names.
 */
public final class Game {
	public enum Key { FORWARD, LEFT, BACK, RIGHT, JUMP, ATTACK, USE }

	/** An equipped item and how much durability it has left (0–100, or -1 if it can't break). */
	public record Item(ItemStack stack, int durability) {
	}

	private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	private Game() {
	}

	private static MinecraftClient mc() {
		return MinecraftClient.getInstance();
	}

	public static boolean inWorld() {
		return mc().player != null;
	}

	/** True when vanilla's HUD is hidden (F1). */
	public static boolean hudHidden() {
		return mc().options.hudHidden;
	}

	public static boolean screenOpen() {
		return mc().currentScreen != null;
	}

	public static int fps() {
		return mc().getCurrentFps();
	}

	public static double x() {
		return mc().player.getX();
	}

	public static double y() {
		return mc().player.getY();
	}

	public static double z() {
		return mc().player.getZ();
	}

	/** "north", "east", ... */
	public static String facing() {
		return mc().player.getHorizontalFacing().asString();
	}

	/** Latency in ms; -1 in singleplayer, -2 when unknown. */
	public static int ping() {
		if (mc().isIntegratedServerRunning()) {
			return -1;
		}
		ClientPlayNetworkHandler connection = mc().getNetworkHandler();
		ClientPlayerEntity player = mc().player;
		if (connection == null || player == null) {
			return -2;
		}
		PlayerListEntry info = connection.getPlayerListEntry(player.getUuid());
		return info != null ? info.getLatency() : -2;
	}

	public static boolean keyDown(Key key) {
		KeyBinding mapping = switch (key) {
			case FORWARD -> mc().options.forwardKey;
			case LEFT -> mc().options.leftKey;
			case BACK -> mc().options.backKey;
			case RIGHT -> mc().options.rightKey;
			case JUMP -> mc().options.jumpKey;
			case ATTACK -> mc().options.attackKey;
			case USE -> mc().options.useKey;
		};
		return mapping.isPressed();
	}

	/** The physical mouse button state (GLFW button index), for counting clicks. */
	public static boolean mouseButtonDown(int button) {
		return GLFW.glfwGetMouseButton(mc().getWindow().getHandle(), button) == GLFW.GLFW_PRESS;
	}

	/** The physical state of a keyboard key (GLFW key code), for module keybinds. */
	public static boolean physicalKeyDown(int key) {
		return GLFW.glfwGetKey(mc().getWindow().getHandle(), key) == GLFW.GLFW_PRESS;
	}

	/** Worn armour, helmet first, then (if {@code held}) the main-hand item; empty slots are left out. */
	public static List<Item> equipment(boolean held) {
		List<Item> items = new ArrayList<>();
		ClientPlayerEntity player = mc().player;
		if (player == null) {
			return items;
		}
		for (EquipmentSlot slot : ARMOR) {
			add(items, player.getEquippedStack(slot));
		}
		if (held) {
			add(items, player.getMainHandStack());
		}
		return items;
	}

	private static void add(List<Item> items, ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}
		int durability = stack.isDamageable()
				? Math.round(100f * (stack.getMaxDamage() - stack.getDamage()) / stack.getMaxDamage())
				: -1;
		items.add(new Item(stack, durability));
	}

	public static void drawItem(Draw d, Item item, int x, int y) {
		d.graphics.drawItem(item.stack(), x, y);
	}
}
