package com.amethystclient.hud;

import com.amethystclient.ui.Draw;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
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

	private static Minecraft mc() {
		return Minecraft.getInstance();
	}

	public static boolean inWorld() {
		return mc().player != null;
	}

	/** True when vanilla's HUD is hidden (F1). */
	public static boolean hudHidden() {
		return mc().gui.hud.isHidden();
	}

	public static boolean screenOpen() {
		return mc().gui.screen() != null;
	}

	public static int fps() {
		return mc().getFps();
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
		return mc().player.getDirection().getSerializedName();
	}

	/** Latency in ms; -1 in singleplayer, -2 when unknown. */
	public static int ping() {
		if (mc().hasSingleplayerServer()) {
			return -1;
		}
		ClientPacketListener connection = mc().getConnection();
		LocalPlayer player = mc().player;
		if (connection == null || player == null) {
			return -2;
		}
		PlayerInfo info = connection.getPlayerInfo(player.getUUID());
		return info != null ? info.getLatency() : -2;
	}

	public static boolean keyDown(Key key) {
		KeyMapping mapping = switch (key) {
			case FORWARD -> mc().options.keyUp;
			case LEFT -> mc().options.keyLeft;
			case BACK -> mc().options.keyDown;
			case RIGHT -> mc().options.keyRight;
			case JUMP -> mc().options.keyJump;
			case ATTACK -> mc().options.keyAttack;
			case USE -> mc().options.keyUse;
		};
		return mapping.isDown();
	}

	/** The physical mouse button state (GLFW button index), for counting clicks. */
	public static boolean mouseButtonDown(int button) {
		return GLFW.glfwGetMouseButton(mc().getWindow().handle(), button) == GLFW.GLFW_PRESS;
	}

	/** The physical state of a keyboard key (GLFW key code), for module keybinds. */
	public static boolean physicalKeyDown(int key) {
		return GLFW.glfwGetKey(mc().getWindow().handle(), key) == GLFW.GLFW_PRESS;
	}

	/** Worn armour, helmet first, then (if {@code held}) the main-hand item; empty slots are left out. */
	public static List<Item> equipment(boolean held) {
		List<Item> items = new ArrayList<>();
		LocalPlayer player = mc().player;
		if (player == null) {
			return items;
		}
		for (EquipmentSlot slot : ARMOR) {
			add(items, player.getItemBySlot(slot));
		}
		if (held) {
			add(items, player.getMainHandItem());
		}
		return items;
	}

	private static void add(List<Item> items, ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}
		int durability = stack.isDamageableItem()
				? Math.round(100f * (stack.getMaxDamage() - stack.getDamageValue()) / stack.getMaxDamage())
				: -1;
		items.add(new Item(stack, durability));
	}

	public static void drawItem(Draw d, Item item, int x, int y) {
		d.graphics.item(item.stack(), x, y);
	}
}
