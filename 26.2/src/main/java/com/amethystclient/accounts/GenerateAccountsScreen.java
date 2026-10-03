package com.amethystclient.accounts;

import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Makes cracked accounts in bulk; see {@link AccountGenerator}. */
public class GenerateAccountsScreen extends Screen {
	private static final int WIDTH = 260;
	private static final int TEXT = 0xFFEDE7F5;
	private static final int MUTED = 0xFF9C92AA;
	private static final int GOOD = 0xFF6BD68F;
	private static final int WARN = 0xFFE6C15A;

	private final AccountsScreen parent;
	private boolean fromList;
	private AccountGenerator.PasswordMode passwordMode = AccountGenerator.PasswordMode.NONE;
	private String countText = "10";
	private String listText = "";
	private String passwordText = "";
	private EditBox count;
	private MultiLineEditBox list;
	private EditBox password;
	private Button generate;
	private volatile boolean working;
	private String result = "";
	private int resultColor = MUTED;
	private int top;

	public GenerateAccountsScreen(AccountsScreen parent) {
		super(Component.literal("Generate accounts"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int x = (width - WIDTH) / 2;
		top = Math.max(6, (height - 232) / 2);
		int y = top + 16;
		addRenderableWidget(Button.builder(Component.literal(fromList ? "Names: from a list" : "Names: random"), b -> {
			saveFields();
			fromList = !fromList;
			result = "";
			rebuildWidgets();
		}).bounds(x, y, WIDTH, 20).build());
		y += 26;

		count = null;
		list = null;
		if (fromList) {
			list = MultiLineEditBox.builder().setX(x).setY(y)
					.setPlaceholder(Component.literal("One name per line, or name:password"))
					.build(font, WIDTH, 70, Component.literal("Names"));
			list.setCharacterLimit(100_000);
			list.setValue(listText);
			addRenderableWidget(list);
			y += 74;
			addRenderableWidget(Button.builder(Component.literal("Paste"), b -> list.setValue(minecraft.keyboardHandler.getClipboard()))
					.bounds(x, y, 60, 20).build());
		} else {
			count = new EditBox(font, x + WIDTH - 60, y + 1, 60, 18, Component.literal("How many"));
			count.setMaxLength(4);
			count.setValue(countText);
			addRenderableWidget(count);
		}
		y += 24;

		addRenderableWidget(Button.builder(Component.literal("Auto-login password: " + passwordMode.label), b -> {
			saveFields();
			passwordMode = AccountGenerator.PasswordMode.values()[(passwordMode.ordinal() + 1) % AccountGenerator.PasswordMode.values().length];
			rebuildWidgets();
		}).bounds(x, y, WIDTH, 20).build());
		y += 24;
		password = null;
		if (passwordMode == AccountGenerator.PasswordMode.SAME) {
			password = new EditBox(font, x, y, WIDTH, 18, Component.literal("Password"));
			password.setMaxLength(32);
			password.setHint(Component.literal("Password for all of them"));
			password.setValue(passwordText);
			addRenderableWidget(password);
		}
		y += 24;

		generate = addRenderableWidget(Button.builder(Component.literal("Generate"), b -> generate())
				.bounds(x, y, WIDTH / 2 - 2, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
				.bounds(x + WIDTH / 2 + 2, y, WIDTH / 2 - 2, 20).build());
	}

	private void saveFields() {
		if (count != null) {
			countText = count.getValue();
		}
		if (list != null) {
			listText = list.getValue();
		}
		if (password != null) {
			passwordText = password.getValue();
		}
	}

	private void generate() {
		if (working) {
			return;
		}
		saveFields();
		if (passwordMode == AccountGenerator.PasswordMode.SAME && (passwordText.isBlank() || passwordText.contains(" "))) {
			setResult("Type the password (no spaces) first.", WARN);
			return;
		}
		AccountGenerator.PasswordMode mode = passwordMode;
		String shared = passwordText;
		Runnable work;
		if (fromList) {
			if (listText.isBlank()) {
				setResult("Paste a list of names first.", WARN);
				return;
			}
			String raw = listText;
			work = () -> {
				AccountGenerator.ParsedList parsed = AccountGenerator.parseList(raw);
				int added = AccountGenerator.add(parsed.names(), parsed.passwords(), mode, shared);
				StringBuilder message = new StringBuilder("Added ").append(added).append(" account(s)");
				if (parsed.duplicates() > 0) {
					message.append(", ").append(parsed.duplicates()).append(" already saved");
				}
				if (parsed.invalid() > 0) {
					message.append(", ").append(parsed.invalid()).append(" invalid name(s)");
				}
				done(message.append('.').toString(), added > 0 ? GOOD : WARN);
			};
		} else {
			int wanted;
			try {
				wanted = Integer.parseInt(countText.trim());
			} catch (NumberFormatException e) {
				wanted = 0;
			}
			if (wanted < 1 || wanted > AccountGenerator.MAX_COUNT) {
				setResult("Pick a number from 1 to " + AccountGenerator.MAX_COUNT + ".", WARN);
				return;
			}
			int n = wanted;
			work = () -> {
				List<String> names = AccountGenerator.randomNames(n);
				int added = AccountGenerator.add(names, null, mode, shared);
				done("Added " + added + " account(s).", GOOD);
			};
		}
		working = true;
		generate.active = false;
		setResult("Generating...", MUTED);
		Thread thread = new Thread(() -> {
			try {
				work.run();
			} catch (Exception e) {
				done("Generating failed: " + e.getMessage(), WARN);
			}
		}, "amethystclient-account-generator");
		thread.setDaemon(true);
		thread.start();
	}

	private void done(String message, int color) {
		minecraft.execute(() -> {
			working = false;
			if (generate != null) {
				generate.active = true;
			}
			String note = passwordMode != AccountGenerator.PasswordMode.NONE && color == GOOD ? " Passwords saved for auto-login." : "";
			setResult(message + note, color);
		});
	}

	private void setResult(String message, int color) {
		result = message;
		resultColor = color;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		int x = (width - WIDTH) / 2;
		graphics.centeredText(font, "Generate cracked accounts", width / 2, top + 2, TEXT);
		if (!fromList) {
			graphics.text(font, "How many (1-" + AccountGenerator.MAX_COUNT + ")", x, top + 42 + 6, MUTED, false);
		}
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		if (!result.isEmpty()) {
			int y = top + 16 + 26 + (fromList ? 98 : 24) + 48 + 26;
			graphics.textWithWordWrap(font, Component.literal(result), x, y, WIDTH, resultColor, false);
		}
	}

	@Override
	public void onClose() {
		if (!result.isEmpty() && resultColor == GOOD) {
			parent.showStatus(result, GOOD);
		}
		minecraft.gui.setScreen(parent);
	}
}
