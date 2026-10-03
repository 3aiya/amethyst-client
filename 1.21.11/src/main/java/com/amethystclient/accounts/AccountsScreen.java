package com.amethystclient.accounts;

import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.session.Session;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.PlayerSkinWidget;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Util;
import net.minecraft.entity.player.SkinTextures;
import org.lwjgl.glfw.GLFW;

/** The account manager: add, switch, check and remove accounts. Opened from the multiplayer screen. */
public class AccountsScreen extends Screen {
	// Amethyst palette.
	private static final int PANEL = 0xD0141019;
	private static final int PANEL_EDGE = 0xFF3B2F4D;
	private static final int ACCENT = 0xFF9B6BD6;
	private static final int TEXT = 0xFFEDE7F5;
	private static final int MUTED = 0xFF9C92AA;
	private static final int GOOD = 0xFF6BD68F;
	private static final int WARN = 0xFFE6C15A;
	private static final int BAD = 0xFFE66B6B;

	private static final int MARGIN = 8;
	private static final int TOP_Y = 28;
	private static final int TOP_HEIGHT = 92;
	private static final int PREVIEW_WIDTH = 104;
	private static final int GAP = 6;
	private static final int ROW_HEIGHT = 22;
	private static final int TOOLBAR_HEIGHT = 24;
	private static final long CANCEL_AFTER_MILLIS = 3000;
	private static final long DELETE_CONFIRM_MILLIS = 3000;
	private static final AccountType[] TABS = {AccountType.MICROSOFT, AccountType.CRACKED, AccountType.SESSION, AccountType.THE_ALTENING};
	private static final AccountType[] FILTERS = {null, AccountType.MICROSOFT, AccountType.CRACKED, AccountType.SESSION, AccountType.THE_ALTENING, AccountType.GENERATED};

	private static final ExecutorService WORKERS = Executors.newCachedThreadPool(daemon("amethystclient-accounts"));
	private static final ExecutorService SKIN_LOOKUPS = Executors.newFixedThreadPool(2, daemon("amethystclient-account-skins"));
	// Skins stay cached while the game runs, so reopening the screen doesn't look them all up again.
	private static final Map<String, SkinEntry> SKINS = new LinkedHashMap<>(64, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, SkinEntry> eldest) {
			return size() > 300;
		}
	};

	private enum Operation { NONE, ADD, MICROSOFT, LOGIN, CHECK }

	private enum Style { NORMAL, PRIMARY, DANGER, TAB, TAB_ON }

	private enum Icon { NONE, COPY, EDIT, DELETE }

	private final Screen parent;
	private final List<Button> buttons = new ArrayList<>();
	private TextFieldWidget input;
	private TextFieldWidget search;
	private PlayerSkinWidget preview;

	private AccountType tab = AccountType.MICROSOFT;
	private AccountType filter;
	private int scroll;
	private boolean draggingScrollbar;
	private Row selected;
	private Account renaming;
	private Account armedDelete;
	private long armedDeleteAt;

	private Operation operation = Operation.NONE;
	private int operationId;
	private long operationStartedAt;
	private Future<?> task;
	private String status = "";
	private int statusColor = MUTED;

	private List<Row> rows = List.of();
	private long rowsRevision = -1;
	private String rowsQuery = "";
	private AccountType rowsFilter;

	/** A list row: a saved account, or the launcher's own account ({@code account == null}). */
	private record Row(Account account) {
		boolean launcher() {
			return account == null;
		}
	}

	private static final class Button {
		final int x;
		final int y;
		final int width;
		final int height;
		final String label;
		final Runnable action;
		Style style = Style.NORMAL;
		Icon icon = Icon.NONE;
		boolean enabled = true;
		String tooltip;

		Button(int x, int y, int width, int height, String label, Runnable action) {
			this.x = x;
			this.y = y;
			this.width = width;
			this.height = height;
			this.label = label;
			this.action = action;
		}

		boolean contains(double mouseX, double mouseY) {
			return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
		}

		Button style(Style value) {
			style = value;
			return this;
		}

		Button icon(Icon value, String tip) {
			icon = value;
			tooltip = tip;
			return this;
		}

		Button enabled(boolean value) {
			enabled = value;
			return this;
		}
	}

	private static final class SkinEntry {
		final SkinTextures fallback;
		volatile SkinTextures skin;

		SkinEntry(SkinTextures fallback) {
			this.fallback = fallback;
		}

		SkinTextures get() {
			SkinTextures loaded = skin;
			return loaded != null ? loaded : fallback;
		}
	}

	public AccountsScreen(Screen parent) {
		super(Text.literal("Accounts"));
		this.parent = parent;
	}

	// ---- layout ----

	private int contentX() {
		return (width - contentWidth()) / 2;
	}

	private int contentWidth() {
		return Math.min(width - MARGIN * 2, 620);
	}

	private boolean showPreview() {
		return contentWidth() >= 400;
	}

	private int addWidth() {
		return contentWidth() - (showPreview() ? PREVIEW_WIDTH + GAP : 0);
	}

	private int listY() {
		return TOP_Y + TOP_HEIGHT + GAP;
	}

	private int listHeight() {
		return Math.max(TOOLBAR_HEIGHT + ROW_HEIGHT, height - MARGIN - listY());
	}

	private int rowsY() {
		return listY() + TOOLBAR_HEIGHT + 2;
	}

	private int visibleRows() {
		return Math.max(1, (listY() + listHeight() - 4 - rowsY()) / ROW_HEIGHT);
	}

	private int maxScroll() {
		return Math.max(0, rows().size() - visibleRows());
	}

	@Override
	protected void init() {
		int x = contentX() + 8;
		String typed = input == null ? "" : input.getText();
		String query = search == null ? "" : search.getText();
		input = new TextFieldWidget(textRenderer, x, TOP_Y + 26, addWidth() - 16 - 66, 18, Text.literal("Account"));
		input.setMaxLength(4096);
		input.setText(typed);
		addDrawableChild(input);

		search = new TextFieldWidget(textRenderer, contentX() + 6, listY() + 4, 100, 16, Text.literal("Search"));
		search.setMaxLength(64);
		search.setPlaceholder(Text.literal("Search..."));
		search.setText(query);
		search.setChangedListener(value -> scroll = 0);
		addDrawableChild(search);

		preview = new PlayerSkinWidget(PREVIEW_WIDTH - 8, TOP_HEIGHT - 20, client.getLoadedEntityModels(), this::previewSkin);
		preview.setPosition(contentX() + addWidth() + GAP + 4, TOP_Y + 16);
		addDrawableChild(preview);
		updateWidgets();
	}

	private void updateWidgets() {
		boolean typing = tab != AccountType.MICROSOFT;
		input.visible = typing;
		input.active = typing && !busy();
		if (!input.active) {
			input.setFocused(false);
		}
		input.setPlaceholder(Text.literal(switch (tab) {
			case CRACKED, GENERATED -> renaming != null ? "New name" : "Username";
			case SESSION -> "Minecraft access token";
			case THE_ALTENING -> "TheAltening alt token";
			case MICROSOFT -> "";
		}));
		preview.visible = showPreview();

		// The search box shares the toolbar with the buttons on its right.
		int toolbarButtons = toolbarButtonsWidth();
		search.setWidth(Math.max(40, contentWidth() - 12 - toolbarButtons - 4));
	}

	private int toolbarButtonsWidth() {
		return 76 + 4 + 56 + 4 + 44 + 4 + 40 + (expiredCount() > 0 ? 4 + 76 : 0);
	}

	/** Rebuilt for every frame and click, so the buttons always match the current state. */
	private void layoutButtons() {
		buttons.clear();
		buttons.add(new Button(MARGIN, 6, 46, 16, "Back", this::close));

		int x = contentX() + 8;
		int y = TOP_Y + 6;
		int tabWidth = (addWidth() - 16 - (TABS.length - 1) * 3) / TABS.length;
		for (int i = 0; i < TABS.length; i++) {
			AccountType type = TABS[i];
			String label = type == AccountType.THE_ALTENING ? "Altening" : type.label;
			buttons.add(new Button(x + i * (tabWidth + 3), y, tabWidth, 16, label, () -> selectTab(type))
					.style(tab == type ? Style.TAB_ON : Style.TAB).enabled(!busy()));
		}

		int formY = TOP_Y + 26;
		if (tab == AccountType.MICROSOFT) {
			String label = operation == Operation.MICROSOFT ? "Waiting for the browser..." : "Sign in with Microsoft";
			buttons.add(new Button(x, formY, addWidth() - 16, 18, label, this::startMicrosoftLogin).style(Style.PRIMARY).enabled(!busy()));
		} else {
			buttons.add(new Button(x + addWidth() - 16 - 62, formY, 62, 18, renaming != null ? "Rename" : "Add", this::submitInput)
					.style(Style.PRIMARY).enabled(!busy()));
		}
		if (busy() && System.currentTimeMillis() - operationStartedAt >= CANCEL_AFTER_MILLIS) {
			buttons.add(new Button(x + addWidth() - 16 - 52, TOP_Y + TOP_HEIGHT - 20, 52, 14, "Cancel", this::cancelOperation).style(Style.DANGER));
		} else if (renaming != null) {
			buttons.add(new Button(x + addWidth() - 16 - 52, TOP_Y + TOP_HEIGHT - 20, 52, 14, "Cancel", this::cancelRename));
		}

		// Toolbar, right to left.
		int right = contentX() + contentWidth() - 6;
		int ty = listY() + 4;
		right = addToolbarButton(right, ty, 40, "Clear", Style.DANGER, this::confirmClear, !busy() && !rows().stream().allMatch(Row::launcher));
		right = addToolbarButton(right, ty, 44, operation == Operation.CHECK ? "..." : "Check", Style.NORMAL, this::checkAccounts, !busy());
		right = addToolbarButton(right, ty, 56, "Generate", Style.NORMAL, () -> client.setScreen(new GenerateAccountsScreen(this)), !busy());
		if (expiredCount() > 0) {
			right = addToolbarButton(right, ty, 76, "Clear expired", Style.DANGER, this::clearExpired, !busy());
		}
		addToolbarButton(right, ty, 76, "Show: " + (filter == null ? "All" : filterLabel(filter)), Style.NORMAL, this::cycleFilter, true);

		// Row buttons.
		List<Row> list = rows();
		int rowX = contentX() + 4;
		int rowRight = contentX() + contentWidth() - 10;
		for (int i = 0; i < visibleRows() && scroll + i < list.size(); i++) {
			Row row = list.get(scroll + i);
			int ry = rowsY() + i * ROW_HEIGHT;
			int by = ry + 3;
			int bx = rowRight - 2;
			Account account = row.account();
			if (!row.launcher()) {
				boolean armed = account == armedDelete && System.currentTimeMillis() - armedDeleteAt < DELETE_CONFIRM_MILLIS;
				bx -= 16;
				buttons.add(new Button(bx, by, 16, 16, "", () -> deleteAccount(account)).style(armed ? Style.DANGER : Style.NORMAL)
						.icon(Icon.DELETE, armed ? "Click again to delete" : "Delete").enabled(!busy()));
				if (account.type.isCracked()) {
					bx -= 18;
					buttons.add(new Button(bx, by, 16, 16, "", () -> startRename(account)).icon(Icon.EDIT, "Rename").enabled(!busy()));
				}
				if (!account.type.isCracked()) {
					bx -= 18;
					buttons.add(new Button(bx, by, 16, 16, "", () -> copyToken(account)).icon(Icon.COPY, "Copy session token").enabled(!busy()));
				}
			}
			boolean current = isCurrent(row);
			bx -= 46;
			buttons.add(new Button(bx, by, 44, 16, current ? "Active" : "Login", () -> login(row))
					.style(current ? Style.TAB_ON : Style.PRIMARY).enabled(!busy() && !current));
		}
	}

	private int addToolbarButton(int right, int y, int buttonWidth, String label, Style style, Runnable action, boolean enabled) {
		int x = right - buttonWidth;
		buttons.add(new Button(x, y, buttonWidth, 16, label, action).style(style).enabled(enabled));
		return x - 4;
	}

	// ---- rows ----

	private List<Row> rows() {
		AccountStore store = AccountStore.get();
		String query = search == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
		if (store.revision() != rowsRevision || !query.equals(rowsQuery) || filter != rowsFilter) {
			List<Row> list = new ArrayList<>();
			if (filter == null && query.isEmpty()) {
				list.add(new Row(null));
			}
			for (Account account : store.all()) {
				if (filter != null && account.type != filter) {
					continue;
				}
				if (!query.isEmpty() && !account.displayName().toLowerCase(Locale.ROOT).contains(query)) {
					continue;
				}
				list.add(new Row(account));
			}
			rows = list;
			rowsRevision = store.revision();
			rowsQuery = query;
			rowsFilter = filter;
			scroll = Math.min(scroll, maxScroll());
		}
		return rows;
	}

	private int expiredCount() {
		int count = 0;
		for (Account account : AccountStore.get().all()) {
			if (account.status == Account.Status.EXPIRED) {
				count++;
			}
		}
		return count;
	}

	private boolean isCurrent(Row row) {
		Session user = client.getSession();
		if (row.launcher()) {
			Session original = SessionSwitcher.originalUser();
			return original.getUsername().equals(user.getUsername()) && Objects.equals(original.getUuidOrNull(), user.getUuidOrNull());
		}
		Account account = row.account();
		return account.username().equals(user.getUsername()) && Objects.equals(user.getUuidOrNull(), account.uuid());
	}

	private Row selectedRow() {
		if (selected != null && (selected.launcher() || AccountStore.get().all().contains(selected.account()))) {
			return selected;
		}
		for (Row row : rows()) {
			if (isCurrent(row)) {
				return row;
			}
		}
		return new Row(null);
	}

	private String rowName(Row row) {
		return row.launcher() ? SessionSwitcher.originalUser().getUsername() : row.account().displayName();
	}

	// ---- rendering ----

	@Override
	public void render(DrawContext graphics, int mouseX, int mouseY, float delta) {
		updateWidgets();
		layoutButtons();

		graphics.drawText(textRenderer, "Accounts", (width - textRenderer.getWidth("Accounts")) / 2, 10, TEXT, true);
		String current = "Logged in as " + client.getSession().getUsername();
		graphics.drawText(textRenderer, clip(current, width / 2 - 70), width - MARGIN - Math.min(textRenderer.getWidth(current), width / 2 - 70), 10, MUTED, true);

		int x = contentX();
		panel(graphics, x, TOP_Y, addWidth(), TOP_HEIGHT);
		if (showPreview()) {
			int px = x + addWidth() + GAP;
			panel(graphics, px, TOP_Y, PREVIEW_WIDTH, TOP_HEIGHT);
			Row row = selectedRow();
			String name = clip(rowName(row), PREVIEW_WIDTH - 12);
			text(graphics, name, px + (PREVIEW_WIDTH - textRenderer.getWidth(name)) / 2, TOP_Y + 5, isCurrent(row) ? GOOD : TEXT);
		}
		text(graphics, clip(hint(), addWidth() - 16), x + 8, TOP_Y + 50, MUTED);
		if (!status.isEmpty()) {
			text(graphics, clip(status, addWidth() - 16 - 58), x + 8, TOP_Y + TOP_HEIGHT - 16, statusColor);
		}

		panel(graphics, x, listY(), contentWidth(), listHeight());
		renderRows(graphics, mouseX, mouseY);
		renderScrollbar(graphics);

		for (Button button : buttons) {
			renderButton(graphics, button, mouseX, mouseY);
		}
		super.render(graphics, mouseX, mouseY, delta);
		for (Button button : buttons) {
			if (button.tooltip != null && button.contains(mouseX, mouseY)) {
				graphics.drawTooltip(textRenderer, Text.literal(button.tooltip), mouseX, mouseY);
			}
		}
	}

	private String hint() {
		if (renaming != null) {
			return "Renaming " + renaming.displayName() + ".";
		}
		return switch (tab) {
			case MICROSOFT -> "Opens your browser to sign in to a Microsoft account.";
			case CRACKED, GENERATED -> "Offline-mode name: 3-16 letters, digits or _.";
			case SESSION -> "A game access token, e.g. copied from another launcher.";
			case THE_ALTENING -> "An alt token from thealtening.com.";
		};
	}

	private void renderRows(DrawContext graphics, int mouseX, int mouseY) {
		List<Row> list = rows();
		int rowX = contentX() + 4;
		int rowWidth = contentWidth() - 14;
		if (list.isEmpty()) {
			text(graphics, "No accounts match.", rowX + 6, rowsY() + 6, MUTED);
			return;
		}
		Row selectedRow = selectedRow();
		for (int i = 0; i < visibleRows() && scroll + i < list.size(); i++) {
			Row row = list.get(scroll + i);
			int y = rowsY() + i * ROW_HEIGHT;
			boolean current = isCurrent(row);
			boolean hovered = mouseX >= rowX && mouseX < rowX + rowWidth && mouseY >= y && mouseY < y + ROW_HEIGHT - 2;
			boolean isSelected = selectedRow.equals(row);
			int fill = isSelected ? 0x409B6BD6 : hovered ? 0x30FFFFFF : 0x18FFFFFF;
			graphics.fill(rowX, y, rowX + rowWidth, y + ROW_HEIGHT - 2, fill);
			if (current) {
				graphics.fill(rowX, y, rowX + 2, y + ROW_HEIGHT - 2, GOOD);
			}
			drawHead(graphics, skin(row).get(), rowX + 5, y + 2, 16);

			int buttonsLeft = buttonsLeftOf(y + 3, rowX + rowWidth);
			int textX = rowX + 26;
			int space = Math.max(20, buttonsLeft - 6 - textX);
			int nameWidth = space * 55 / 100;
			Account account = row.account();
			int nameColor = current ? GOOD : account != null && account.status == Account.Status.EXPIRED ? BAD : TEXT;
			text(graphics, clip(rowName(row), nameWidth), textX, y + 7, nameColor);
			String type = row.launcher() ? "Launcher" : account.type.label;
			String state = rowState(row, current);
			int typeX = textX + nameWidth + 6;
			text(graphics, clip(type, space - nameWidth - 6), typeX, y + 7, row.launcher() ? ACCENT : MUTED);
			if (!state.isEmpty() && typeX + textRenderer.getWidth(type) + 8 + textRenderer.getWidth(state) <= buttonsLeft - 4) {
				text(graphics, state, buttonsLeft - 6 - textRenderer.getWidth(state), y + 7, stateColor(row, current));
			}
		}
	}

	private int buttonsLeftOf(int buttonY, int fallback) {
		int left = fallback;
		for (Button button : buttons) {
			if (button.y == buttonY) {
				left = Math.min(left, button.x);
			}
		}
		return left;
	}

	private static String rowState(Row row, boolean current) {
		if (current) {
			return "CURRENT";
		}
		if (row.launcher()) {
			return "";
		}
		return switch (row.account().status) {
			case CHECKING -> "CHECKING";
			case VALID -> "VALID";
			case EXPIRED -> "EXPIRED";
			case UNKNOWN -> "";
		};
	}

	private static int stateColor(Row row, boolean current) {
		if (current || row.launcher()) {
			return GOOD;
		}
		return switch (row.account().status) {
			case CHECKING -> WARN;
			case VALID -> GOOD;
			case EXPIRED -> BAD;
			case UNKNOWN -> MUTED;
		};
	}

	private void renderScrollbar(DrawContext graphics) {
		int max = maxScroll();
		if (max <= 0) {
			return;
		}
		int[] bar = scrollbar();
		graphics.fill(bar[0], rowsY(), bar[0] + 4, rowsY() + visibleRows() * ROW_HEIGHT - 2, 0x30FFFFFF);
		graphics.fill(bar[0], bar[1], bar[0] + 4, bar[1] + bar[2], draggingScrollbar ? 0xFFB792E6 : ACCENT);
	}

	/** {x, thumbY, thumbHeight} of the list's scrollbar. */
	private int[] scrollbar() {
		int track = visibleRows() * ROW_HEIGHT - 2;
		int total = rows().size();
		int thumb = Math.max(12, track * visibleRows() / Math.max(1, total));
		int y = rowsY() + (maxScroll() == 0 ? 0 : (track - thumb) * scroll / maxScroll());
		return new int[] {contentX() + contentWidth() - 8, y, thumb};
	}

	private void panel(DrawContext graphics, int x, int y, int w, int h) {
		graphics.fill(x, y, x + w, y + h, PANEL);
		graphics.fill(x, y, x + w, y + 1, PANEL_EDGE);
		graphics.fill(x, y + h - 1, x + w, y + h, PANEL_EDGE);
		graphics.fill(x, y, x + 1, y + h, PANEL_EDGE);
		graphics.fill(x + w - 1, y, x + w, y + h, PANEL_EDGE);
	}

	private void renderButton(DrawContext graphics, Button button, int mouseX, int mouseY) {
		boolean hovered = button.enabled && button.contains(mouseX, mouseY);
		int background = switch (button.style) {
			case PRIMARY -> hovered ? 0xFFAE82E3 : 0xFF8456BF;
			case DANGER -> hovered ? 0xFFC45454 : 0xFF8E3B46;
			case TAB_ON -> 0xFF4B3A66;
			case NORMAL, TAB -> hovered ? 0xFF3E3450 : 0xFF2A2335;
		};
		if (!button.enabled && button.style != Style.TAB_ON) {
			background = 0xFF231E2B;
		}
		int x = button.x;
		int y = button.y;
		graphics.fill(x, y, x + button.width, y + button.height, background);
		int edge = button.style == Style.TAB_ON ? ACCENT : hovered ? 0xFF7A6A92 : 0xFF41364F;
		graphics.fill(x, y, x + button.width, y + 1, edge);
		graphics.fill(x, y + button.height - 1, x + button.width, y + button.height, edge);
		graphics.fill(x, y, x + 1, y + button.height, edge);
		graphics.fill(x + button.width - 1, y, x + button.width, y + button.height, edge);
		int color = button.enabled ? TEXT : 0xFF6E6578;
		if (button.icon != Icon.NONE) {
			drawIcon(graphics, button.icon, x + button.width / 2 - 4, y + button.height / 2 - 4, color);
			return;
		}
		String label = clip(button.label, button.width - 6);
		text(graphics, label, x + (button.width - textRenderer.getWidth(label)) / 2, y + (button.height - 8) / 2, color);
	}

	private static void drawIcon(DrawContext graphics, Icon icon, int x, int y, int color) {
		switch (icon) {
			case COPY -> {
				outline(graphics, x + 2, y, 6, 6, color);
				graphics.fill(x, y + 2, x + 6, y + 8, 0xFF2A2335);
				outline(graphics, x, y + 2, 6, 6, color);
			}
			case EDIT -> {
				for (int i = 0; i < 7; i++) {
					graphics.fill(x + i, y + 7 - i, x + i + 2, y + 8 - i, color);
				}
				graphics.fill(x, y + 7, x + 2, y + 8, 0xFFE6C15A);
			}
			case DELETE -> {
				for (int i = 0; i < 8; i++) {
					graphics.fill(x + i, y + i, x + i + 1, y + i + 1, color);
					graphics.fill(x + 7 - i, y + i, x + 8 - i, y + i + 1, color);
				}
			}
			case NONE -> {
			}
		}
	}

	private static void outline(DrawContext graphics, int x, int y, int w, int h, int color) {
		graphics.fill(x, y, x + w, y + 1, color);
		graphics.fill(x, y + h - 1, x + w, y + h, color);
		graphics.fill(x, y, x + 1, y + h, color);
		graphics.fill(x + w - 1, y, x + w, y + h, color);
	}

	// ---- input ----

	@Override
	public boolean mouseClicked(Click event, boolean doubleClick) {
		double mouseX = event.x();
		double mouseY = event.y();
		if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			layoutButtons();
			for (Button button : buttons) {
				if (button.contains(mouseX, mouseY)) {
					if (button.enabled) {
						playClick();
						button.action.run();
					}
					return true;
				}
			}
			if (maxScroll() > 0) {
				int[] bar = scrollbar();
				if (mouseX >= bar[0] - 1 && mouseX < bar[0] + 5 && mouseY >= rowsY() && mouseY < rowsY() + visibleRows() * ROW_HEIGHT) {
					draggingScrollbar = true;
					dragScrollbar(mouseY);
					return true;
				}
			}
			Row row = rowAt(mouseX, mouseY);
			if (row != null) {
				setFocused(null);
				selected = row;
				if (doubleClick && !busy() && !isCurrent(row)) {
					login(row);
				}
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(Click event, double dx, double dy) {
		if (draggingScrollbar) {
			dragScrollbar(event.y());
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(Click event) {
		draggingScrollbar = false;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (mouseY >= listY() && mouseY < listY() + listHeight()) {
			scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(scrollY)));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyInput event) {
		if ((event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) && input.isFocused()) {
			submitInput();
			return true;
		}
		return super.keyPressed(event);
	}

	private void dragScrollbar(double mouseY) {
		int track = visibleRows() * ROW_HEIGHT - 2;
		int thumb = scrollbar()[2];
		double fraction = (mouseY - rowsY() - thumb / 2.0) / Math.max(1, track - thumb);
		scroll = (int) Math.round(Math.max(0, Math.min(1, fraction)) * maxScroll());
	}

	private Row rowAt(double mouseX, double mouseY) {
		int rowX = contentX() + 4;
		if (mouseX < rowX || mouseX >= rowX + contentWidth() - 14 || mouseY < rowsY()) {
			return null;
		}
		int index = (int) ((mouseY - rowsY()) / ROW_HEIGHT);
		List<Row> list = rows();
		return index < visibleRows() && scroll + index < list.size() ? list.get(scroll + index) : null;
	}

	// ---- actions ----

	private void selectTab(AccountType type) {
		tab = type;
		renaming = null;
		input.setText("");
		updateWidgets();
	}

	private void cycleFilter() {
		int index = 0;
		for (int i = 0; i < FILTERS.length; i++) {
			if (FILTERS[i] == filter) {
				index = i;
			}
		}
		filter = FILTERS[(index + 1) % FILTERS.length];
		scroll = 0;
	}

	private static String filterLabel(AccountType type) {
		return type == AccountType.THE_ALTENING ? "Altening" : type.label;
	}

	private void submitInput() {
		if (busy() || tab == AccountType.MICROSOFT) {
			return;
		}
		String value = input.getText().trim();
		if (renaming != null) {
			if (!AccountGenerator.isValidName(value)) {
				setStatus("Use 3-16 letters, digits or _.", WARN);
				return;
			}
			boolean wasCurrent = isCurrent(new Row(renaming));
			if (!AccountStore.get().rename(renaming, value)) {
				setStatus("A cracked account named " + value + " already exists.", WARN);
				return;
			}
			Account renamed = renaming;
			cancelRename();
			setStatus("Renamed to " + value + ".", GOOD);
			if (wasCurrent) {
				login(new Row(renamed));
			}
			return;
		}
		if (value.isEmpty()) {
			setStatus(tab == AccountType.CRACKED ? "Type a username first." : "Paste a token first.", WARN);
			return;
		}
		Account account = switch (tab) {
			case CRACKED, GENERATED -> {
				if (!AccountGenerator.isValidName(value)) {
					setStatus("Use 3-16 letters, digits or _.", WARN);
					yield null;
				}
				yield Account.cracked(value, AccountType.CRACKED);
			}
			case SESSION -> Account.session(value);
			case THE_ALTENING -> Account.theAltening(value);
			case MICROSOFT -> null;
		};
		if (account != null) {
			addAndLogin(account, Operation.ADD);
		}
	}

	private void addAndLogin(Account account, Operation kind) {
		int id = begin(kind, kind == Operation.MICROSOFT ? "Signing in..." : "Adding...");
		task = WORKERS.submit(() -> {
			if (!account.refresh()) {
				finish(id, "Couldn't add the account: " + account.lastError(), BAD);
				return;
			}
			if (cancelled(id)) {
				return;
			}
			AccountStore store = AccountStore.get();
			Account existing = store.findSame(account);
			Account target = account;
			if (existing != null) {
				if (account.type != AccountType.MICROSOFT) {
					finish(id, existing.displayName() + " is already saved.", WARN);
					return;
				}
				// Signing in again to a saved Microsoft account renews its tokens.
				existing.adoptSignIn(account);
				target = existing;
			} else {
				store.add(account);
			}
			boolean loggedIn = target.login();
			store.save();
			if (cancelled(id)) {
				return;
			}
			Account added = target;
			client.execute(() -> {
				selected = new Row(added);
				input.setText("");
			});
			finish(id, loggedIn ? "Logged in as " + target.displayName() + "." : "Saved, but login failed: " + target.lastError(), loggedIn ? GOOD : WARN);
		});
	}

	private void startMicrosoftLogin() {
		startMicrosoftLogin(null);
	}

	/** {@code relogin}: a saved account whose sign-in expired; the new sign-in renews it. */
	private void startMicrosoftLogin(Account relogin) {
		if (busy() && operation != Operation.LOGIN) {
			return;
		}
		int id = operation == Operation.LOGIN ? operationId : begin(Operation.MICROSOFT, "");
		operation = Operation.MICROSOFT;
		try {
			String url = MicrosoftLogin.startBrowserLogin(refreshToken -> client.execute(() -> {
				if (cancelled(id)) {
					return;
				}
				if (refreshToken == null) {
					finish(id, "Microsoft sign-in was cancelled or failed.", BAD);
					return;
				}
				operation = Operation.NONE;
				if (relogin != null) {
					relogin.setRefreshToken(refreshToken);
					AccountStore.get().save();
					login(new Row(relogin));
				} else {
					addAndLogin(Account.microsoft(refreshToken), Operation.MICROSOFT);
				}
			}));
			Util.getOperatingSystem().open(url);
			setStatus(relogin != null ? "The sign-in for " + relogin.displayName() + " expired - sign in again in your browser."
					: "Finish signing in in your browser.", WARN);
		} catch (IOException e) {
			finish(id, "Couldn't start the sign-in (is port 9675 in use?).", BAD);
		}
	}

	private void login(Row row) {
		if (busy()) {
			return;
		}
		selected = row;
		Account account = row.account();
		int id = begin(Operation.LOGIN, "Logging in as " + rowName(row) + "...");
		task = WORKERS.submit(() -> {
			boolean ok = row.launcher() ? SessionSwitcher.restoreOriginal() : account.login();
			if (cancelled(id)) {
				return;
			}
			if (ok) {
				if (account != null) {
					account.status = Account.Status.VALID;
					AccountStore.get().save();
				}
				finish(id, "Logged in as " + rowName(row) + ".", GOOD);
				return;
			}
			String error = row.launcher() ? SessionSwitcher.lastError() : account.lastError();
			if (account != null && account.type == AccountType.MICROSOFT && signInExpired(error)) {
				// The saved sign-in no longer works; ask for a new one instead of just failing.
				client.execute(() -> {
					if (!cancelled(id)) {
						startMicrosoftLogin(account);
					}
				});
				return;
			}
			if (account != null) {
				account.status = Account.Status.EXPIRED;
			}
			finish(id, "Login failed: " + error, BAD);
		});
	}

	private static boolean signInExpired(String error) {
		String lower = error.toLowerCase(Locale.ROOT);
		return lower.contains("invalid_grant") || lower.contains("expired") || lower.contains("refresh token")
				|| lower.contains("unauthorized_client") || lower.contains("aadsts");
	}

	private void checkAccounts() {
		List<Account> checking = new ArrayList<>();
		for (Account account : AccountStore.get().all()) {
			if (account.type.isCracked()) {
				account.status = Account.Status.VALID;
			} else {
				account.status = Account.Status.CHECKING;
				checking.add(account);
			}
		}
		if (checking.isEmpty()) {
			setStatus("Cracked accounts don't expire; nothing to check.", MUTED);
			return;
		}
		int id = begin(Operation.CHECK, "Checking " + checking.size() + " account(s)...");
		task = WORKERS.submit(() -> {
			ExecutorService pool = Executors.newFixedThreadPool(Math.min(4, checking.size()), daemon("amethystclient-account-check"));
			AtomicInteger valid = new AtomicInteger();
			try {
				List<Future<?>> jobs = new ArrayList<>();
				for (Account account : checking) {
					jobs.add(pool.submit(() -> {
						if (cancelled(id)) {
							return;
						}
						boolean ok = account.refresh();
						account.status = ok ? Account.Status.VALID : Account.Status.EXPIRED;
						if (ok) {
							valid.incrementAndGet();
						}
					}));
				}
				for (Future<?> job : jobs) {
					job.get();
				}
			} catch (Exception e) {
				// Cancelled; statuses are reset below.
			} finally {
				pool.shutdownNow();
			}
			if (cancelled(id)) {
				return;
			}
			AccountStore.get().save();
			int expired = checking.size() - valid.get();
			finish(id, valid.get() + " valid, " + expired + " expired.", expired == 0 ? GOOD : WARN);
		});
	}

	private void clearExpired() {
		int removed = AccountStore.get().removeIf(account -> account.status == Account.Status.EXPIRED);
		setStatus("Removed " + removed + " expired account(s).", MUTED);
	}

	private void confirmClear() {
		AccountType scope = filter;
		long count = AccountStore.get().all().stream().filter(account -> scope == null || account.type == scope).count();
		if (count == 0) {
			setStatus("Nothing to clear.", MUTED);
			return;
		}
		String what = scope == null ? "all " + count + " saved accounts" : "all " + count + " " + filterLabel(scope) + " accounts";
		client.setScreen(new ConfirmScreen(confirmed -> {
			if (confirmed) {
				int removed = AccountStore.get().removeIf(account -> scope == null || account.type == scope);
				setStatus("Removed " + removed + " account(s).", MUTED);
			}
			client.setScreen(this);
		}, Text.literal("Clear accounts?"), Text.literal("This deletes " + what + ". It can't be undone.")));
	}

	private void deleteAccount(Account account) {
		long now = System.currentTimeMillis();
		if (account != armedDelete || now - armedDeleteAt >= DELETE_CONFIRM_MILLIS) {
			armedDelete = account;
			armedDeleteAt = now;
			return;
		}
		armedDelete = null;
		AccountStore.get().remove(account);
		if (account == renaming) {
			cancelRename();
		}
		setStatus("Deleted " + account.displayName() + ".", MUTED);
	}

	private void startRename(Account account) {
		tab = AccountType.CRACKED;
		renaming = account;
		input.setText(account.username());
		updateWidgets();
		setFocused(input);
		input.setFocused(true);
		input.setCursorToEnd(false);
	}

	private void cancelRename() {
		renaming = null;
		input.setText("");
	}

	private void copyToken(Account account) {
		String token = account.gameToken();
		if (token.isBlank()) {
			setStatus("No session token yet - log in once first.", WARN);
			return;
		}
		client.keyboard.setClipboard(token);
		setStatus("Copied the session token of " + account.displayName() + ".", GOOD);
	}

	// ---- operations ----

	private boolean busy() {
		return operation != Operation.NONE;
	}

	private int begin(Operation kind, String message) {
		operation = kind;
		operationId++;
		operationStartedAt = System.currentTimeMillis();
		setStatus(message, WARN);
		return operationId;
	}

	private boolean cancelled(int id) {
		return id != operationId || Thread.currentThread().isInterrupted();
	}

	/** Ends operation {@code id} (from any thread) with a status message. */
	private void finish(int id, String message, int color) {
		client.execute(() -> {
			if (id != operationId) {
				return;
			}
			operation = Operation.NONE;
			task = null;
			setStatus(message, color);
		});
	}

	private void cancelOperation() {
		if (!busy()) {
			return;
		}
		operationId++;
		if (task != null) {
			task.cancel(true);
			task = null;
		}
		MicrosoftLogin.stopBrowserLogin();
		for (Account account : AccountStore.get().all()) {
			if (account.status == Account.Status.CHECKING) {
				account.status = Account.Status.UNKNOWN;
			}
		}
		operation = Operation.NONE;
		setStatus("Cancelled.", WARN);
	}

	private void setStatus(String message, int color) {
		status = message;
		statusColor = color;
	}

	/** Called by the generator screen when it closes. */
	void showStatus(String message, int color) {
		setStatus(message, color);
	}

	@Override
	public void close() {
		cancelOperation();
		client.setScreen(parent);
	}

	// ---- skins ----

	private SkinTextures previewSkin() {
		return skin(selectedRow()).get();
	}

	private SkinEntry skin(Row row) {
		UUID uuid;
		String name;
		boolean byName;
		if (row.launcher()) {
			Session original = SessionSwitcher.originalUser();
			uuid = original.getUuidOrNull();
			name = original.getUsername();
			byName = original.getAccessToken().isBlank() || uuid == null;
		} else {
			Account account = row.account();
			uuid = account.uuid();
			name = account.username();
			byName = account.type.isCracked() || uuid == null;
		}
		String key = (byName ? "name:" + name.toLowerCase(Locale.ROOT) : "id:" + uuid);
		synchronized (SKINS) {
			SkinEntry entry = SKINS.get(key);
			if (entry != null) {
				return entry;
			}
			UUID fallbackId = uuid != null ? uuid : Account.offlineUuid(name);
			entry = new SkinEntry(DefaultSkinHelper.getSkinTextures(fallbackId));
			SKINS.put(key, entry);
			SkinEntry target = entry;
			SKIN_LOOKUPS.execute(() -> {
				Optional<MojangProfiles.Textured> profile = byName ? MojangProfiles.byName(name) : MojangProfiles.byId(uuid);
				profile.ifPresent(textured -> client.execute(() -> client.getSkinProvider().fetchSkinTextures(gameProfile(textured))
						.thenAccept(skin -> skin.ifPresent(found -> target.skin = found))));
			});
			return entry;
		}
	}

	private static GameProfile gameProfile(MojangProfiles.Textured textured) {
		Property textures = new Property("textures", textured.textures(), textured.signature().isEmpty() ? null : textured.signature());
		return new GameProfile(textured.id(), textured.name(), new PropertyMap(ImmutableMultimap.of("textures", textures)));
	}

	private static void drawHead(DrawContext graphics, SkinTextures skin, int x, int y, int size) {
		Identifier texture = skin.body().texturePath();
		graphics.drawTexture(RenderPipelines.GUI_TEXTURED, texture, x, y, 8, 8, size, size, 8, 8, 64, 64);
		graphics.drawTexture(RenderPipelines.GUI_TEXTURED, texture, x, y, 40, 8, size, size, 8, 8, 64, 64);
	}

	// ---- small helpers ----

	private void text(DrawContext graphics, String value, int x, int y, int color) {
		graphics.drawText(textRenderer, value, x, y, color, false);
	}

	private String clip(String value, int maxWidth) {
		if (textRenderer.getWidth(value) <= maxWidth) {
			return value;
		}
		return textRenderer.trimToWidth(value, Math.max(0, maxWidth - textRenderer.getWidth("..."))) + "...";
	}

	private void playClick() {
		client.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.UI_BUTTON_CLICK, 1.0f));
	}

	private static ThreadFactory daemon(String name) {
		return runnable -> {
			Thread thread = new Thread(runnable, name);
			thread.setDaemon(true);
			return thread;
		};
	}
}
