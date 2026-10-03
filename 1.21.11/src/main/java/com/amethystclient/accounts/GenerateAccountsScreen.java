package com.amethystclient.accounts;

import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

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
	private TextFieldWidget count;
	private EditBoxWidget list;
	private TextFieldWidget password;
	private ButtonWidget generate;
	private volatile boolean working;
	private String result = "";
	private int resultColor = MUTED;
	private int top;

	public GenerateAccountsScreen(AccountsScreen parent) {
		super(Text.literal("Generate accounts"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int x = (width - WIDTH) / 2;
		top = Math.max(6, (height - 232) / 2);
		int y = top + 16;
		addDrawableChild(ButtonWidget.builder(Text.literal(fromList ? "Names: from a list" : "Names: random"), b -> {
			saveFields();
			fromList = !fromList;
			result = "";
			clearAndInit();
		}).dimensions(x, y, WIDTH, 20).build());
		y += 26;

		count = null;
		list = null;
		if (fromList) {
			list = EditBoxWidget.builder().x(x).y(y)
					.placeholder(Text.literal("One name per line, or name:password"))
					.build(textRenderer, WIDTH, 70, Text.literal("Names"));
			list.setMaxLength(100_000);
			list.setText(listText);
			addDrawableChild(list);
			y += 74;
			addDrawableChild(ButtonWidget.builder(Text.literal("Paste"), b -> list.setText(client.keyboard.getClipboard()))
					.dimensions(x, y, 60, 20).build());
		} else {
			count = new TextFieldWidget(textRenderer, x + WIDTH - 60, y + 1, 60, 18, Text.literal("How many"));
			count.setMaxLength(4);
			count.setText(countText);
			addDrawableChild(count);
		}
		y += 24;

		addDrawableChild(ButtonWidget.builder(Text.literal("Auto-login password: " + passwordMode.label), b -> {
			saveFields();
			passwordMode = AccountGenerator.PasswordMode.values()[(passwordMode.ordinal() + 1) % AccountGenerator.PasswordMode.values().length];
			clearAndInit();
		}).dimensions(x, y, WIDTH, 20).build());
		y += 24;
		password = null;
		if (passwordMode == AccountGenerator.PasswordMode.SAME) {
			password = new TextFieldWidget(textRenderer, x, y, WIDTH, 18, Text.literal("Password"));
			password.setMaxLength(32);
			password.setPlaceholder(Text.literal("Password for all of them"));
			password.setText(passwordText);
			addDrawableChild(password);
		}
		y += 24;

		generate = addDrawableChild(ButtonWidget.builder(Text.literal("Generate"), b -> generate())
				.dimensions(x, y, WIDTH / 2 - 2, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("Back"), b -> close())
				.dimensions(x + WIDTH / 2 + 2, y, WIDTH / 2 - 2, 20).build());
	}

	private void saveFields() {
		if (count != null) {
			countText = count.getText();
		}
		if (list != null) {
			listText = list.getText();
		}
		if (password != null) {
			passwordText = password.getText();
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
		client.execute(() -> {
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
	public void render(DrawContext graphics, int mouseX, int mouseY, float delta) {
		int x = (width - WIDTH) / 2;
		graphics.drawCenteredTextWithShadow(textRenderer, "Generate cracked accounts", width / 2, top + 2, TEXT);
		if (!fromList) {
			graphics.drawText(textRenderer, "How many (1-" + AccountGenerator.MAX_COUNT + ")", x, top + 42 + 6, MUTED, false);
		}
		super.render(graphics, mouseX, mouseY, delta);
		if (!result.isEmpty()) {
			int y = top + 16 + 26 + (fromList ? 98 : 24) + 48 + 26;
			graphics.drawWrappedText(textRenderer, Text.literal(result), x, y, WIDTH, resultColor, false);
		}
	}

	@Override
	public void close() {
		if (!result.isEmpty() && resultColor == GOOD) {
			parent.showStatus(result, GOOD);
		}
		client.setScreen(parent);
	}
}
