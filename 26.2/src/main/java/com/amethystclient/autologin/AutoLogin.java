package com.amethystclient.autologin;

import com.amethystclient.AmethystServers;
import com.amethystclient.mixin.ClientCommonPacketListenerImplAccessor;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Logs cracked accounts in on the Amethyst servers. The auth plugin asks for the password with a
 * "Login" / "Register" dialog (or a chat message); we fill the dialog in and press its button, or answer
 * with /login or /register, using the password saved for this username. The first login or
 * registration is typed by hand and remembered.
 */
public final class AutoLogin {
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("amethystclient-autologin.properties");
	private static final long RETRY_GRACE_MILLIS = 3000;
	private static final List<String> CONFIRM_WORDS = List.of("login", "log in", "register", "sign", "submit", "confirm", "continue");
	private static final List<String> CANCEL_WORDS = List.of("cancel", "disconnect", "leave", "quit", "exit", "back", "close");

	private enum Kind { LOGIN, REGISTER }

	private static final Properties passwords = new Properties();

	// Per connection: what we already sent, so a failed attempt isn't repeated in a loop.
	private static boolean loginSent;
	private static long loginSentAt;
	private static boolean registerSent;
	private static Kind pending;
	private static Screen pendingScreen;
	// The auth dialog usually opens in the configuration phase, before getCurrentServer() knows the server.
	private static ServerData configuringServer;

	private AutoLogin() {
	}

	public static void register() {
		load();
		ClientConfigurationConnectionEvents.INIT.register((handler, client) -> {
			reset();
			configuringServer = ((ClientCommonPacketListenerImplAccessor) handler).amethystclient$getServerData();
		});
		ClientConfigurationConnectionEvents.COMPLETE.register((handler, client) -> configuringServer = null);
		ClientConfigurationConnectionEvents.DISCONNECT.register((handler, client) -> {
			reset();
			configuringServer = null;
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
		ScreenEvents.AFTER_INIT.register(AutoLogin::onScreen);
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> onChat(message));
		ClientSendMessageEvents.COMMAND.register(AutoLogin::rememberTyped);
		ClientTickEvents.END_CLIENT_TICK.register(AutoLogin::tick);
	}

	private static void reset() {
		loginSent = false;
		registerSent = false;
		pending = null;
		pendingScreen = null;
	}

	private static boolean onAmethystServer(Minecraft client) {
		return AmethystServers.isAmethystServer(client) || AmethystServers.isAmethystServer(configuringServer);
	}

	private static void onScreen(Minecraft client, Screen screen, int width, int height) {
		if (!onAmethystServer(client)) {
			return;
		}
		String title = screen.getTitle().getString().toLowerCase(Locale.ROOT);
		Kind kind = title.contains("register") ? Kind.REGISTER : title.contains("login") ? Kind.LOGIN : null;
		if (kind == null || !hasTextField(screen)) {
			return;
		}
		// A password the player types into the dialog themselves is remembered for next time.
		ScreenEvents.remove(screen).register(AutoLogin::rememberFromScreen);
		if (kind == Kind.LOGIN && loginSent) {
			// We already tried and the server asked again: the saved password is wrong. (A dialog
			// arriving right after we sent it was just already on its way.)
			if (System.currentTimeMillis() - loginSentAt > RETRY_GRACE_MILLIS) {
				forget(client);
			}
			return;
		}
		// Act on the next tick, not in the middle of the screen's init.
		pending = kind;
		pendingScreen = screen;
	}

	private static void onChat(Component message) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || !AmethystServers.isAmethystServer(client)) {
			return;
		}
		String text = message.getString().toLowerCase(Locale.ROOT);
		if (text.contains("/register")) {
			pending = Kind.REGISTER;
		} else if (text.contains("/login")) {
			pending = Kind.LOGIN;
		}
	}

	private static void tick(Minecraft client) {
		Kind kind = pending;
		if (kind == null) {
			return;
		}
		Screen screen = pendingScreen;
		pending = null;
		pendingScreen = null;
		if (!onAmethystServer(client)) {
			return;
		}

		String username = client.getUser().getName();
		String password = passwords.getProperty(username);
		if (kind == Kind.REGISTER) {
			if (registerSent) {
				return;
			}
			registerSent = true;
			if (password == null) {
				// The first registration is done by hand; the password typed is saved for next time.
				message(client, "§fRegister once and your password will be remembered for automatic login.");
				return;
			}
		} else {
			if (loginSent) {
				return;
			}
			loginSent = true;
			loginSentAt = System.currentTimeMillis();
			if (password == null) {
				message(client, "§fEnter your password once and it will be remembered for automatic login.");
				return;
			}
		}

		// A dialog is answered through its own button: that also works in the configuration phase,
		// where there is no player to send a command from.
		if (screen != null && client.gui.screen() == screen && submitDialog(screen, password)) {
			return;
		}
		if (client.player == null) {
			return;
		}
		client.player.connection.sendCommand(kind == Kind.REGISTER ? "register " + password + " " + password : "login " + password);

		// Close the dialog ourselves, without running its cancel action.
		if (screen != null && client.gui.screen() == screen) {
			client.gui.setScreen(null);
		}
	}

	/** Types the password into every field (register asks for it twice) and presses the confirm button. */
	private static boolean submitDialog(Screen screen, String password) {
		Button confirm = confirmButton(screen);
		if (confirm == null) {
			return false;
		}
		for (EditBox field : textFields(screen)) {
			field.setValue(password);
		}
		confirm.onPress(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
		return true;
	}

	/** The button labelled like "Login", or the dialog's only button that isn't a cancel/disconnect. */
	private static Button confirmButton(Screen screen) {
		List<Button> candidates = new ArrayList<>();
		for (Button button : widgets(screen, Button.class)) {
			// The dialog's "!" warning icon is an ImageButton.
			String label = button.getMessage().getString().toLowerCase(Locale.ROOT);
			if (button instanceof ImageButton || !button.active || !button.visible || label.isBlank()
					|| CANCEL_WORDS.stream().anyMatch(label::contains)) {
				continue;
			}
			if (CONFIRM_WORDS.stream().anyMatch(label::contains)) {
				return button;
			}
			candidates.add(button);
		}
		return candidates.size() == 1 ? candidates.get(0) : null;
	}

	/** Remembers the password when the player types /login or /register by hand. */
	private static void rememberTyped(String command) {
		Minecraft client = Minecraft.getInstance();
		if (!AmethystServers.isAmethystServer(client)) {
			return;
		}
		String[] parts = command.trim().split("\\s+");
		if (parts.length < 2) {
			return;
		}
		String name = parts[0].toLowerCase(Locale.ROOT);
		if (name.equals("login") || name.equals("l") || name.equals("register") || name.equals("reg")) {
			save(client.getUser().getName(), parts[1]);
		}
	}

	/** Remembers what the player typed into a login/register dialog when it closes. */
	private static void rememberFromScreen(Screen screen) {
		Minecraft client = Minecraft.getInstance();
		for (EditBox field : textFields(screen)) {
			String typed = field.getValue().trim();
			if (!typed.isEmpty() && !typed.contains(" ")) {
				save(client.getUser().getName(), typed);
				return;
			}
		}
	}

	/** Saves passwords for accounts made by the account generator (username -> password). */
	public static void rememberPasswords(Map<String, String> byUsername) {
		if (byUsername.isEmpty()) {
			return;
		}
		passwords.putAll(byUsername);
		store();
	}

	private static void forget(Minecraft client) {
		String username = client.getUser().getName();
		if (passwords.remove(username) != null) {
			store();
			message(client, "§fThe saved password didn't work - enter it again and it will be remembered.");
		}
	}

	private static boolean hasTextField(Screen screen) {
		return !textFields(screen).isEmpty();
	}

	private static List<EditBox> textFields(Screen screen) {
		return widgets(screen, EditBox.class);
	}

	private static <T> List<T> widgets(Screen screen, Class<T> type) {
		List<T> found = new ArrayList<>();
		collect(screen, type, found);
		return found;
	}

	// Dialog inputs sit inside scrollable containers, so walk the whole widget tree.
	private static <T> void collect(ContainerEventHandler parent, Class<T> type, List<T> found) {
		for (GuiEventListener child : parent.children()) {
			if (type.isInstance(child)) {
				found.add(type.cast(child));
			} else if (child instanceof ContainerEventHandler nested) {
				collect(nested, type, found);
			}
		}
	}

	private static void message(Minecraft client, String text) {
		client.gui.hud.getChat().addClientSystemMessage(Component.literal("§b[Amethyst Client] " + text));
	}

	private static void save(String username, String password) {
		if (!password.equals(passwords.getProperty(username))) {
			passwords.setProperty(username, password);
			store();
		}
	}

	private static void load() {
		if (!Files.isRegularFile(FILE)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
			passwords.load(reader);
		} catch (IOException e) {
			System.err.println("[Amethyst Client] Couldn't read " + FILE + ": " + e);
		}
	}

	// Also called from the account generator's background thread.
	private static synchronized void store() {
		try {
			Files.createDirectories(FILE.getParent());
			try (Writer writer = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
				passwords.store(writer, "Amethyst Client auto-login passwords (username=password)");
			}
		} catch (IOException e) {
			System.err.println("[Amethyst Client] Couldn't save " + FILE + ": " + e);
		}
	}
}
