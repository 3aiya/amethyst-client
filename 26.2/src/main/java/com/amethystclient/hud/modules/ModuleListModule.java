package com.amethystclient.hud.modules;

import com.amethystclient.hud.Category;
import com.amethystclient.hud.ClientModule;
import com.amethystclient.hud.HudModule;
import com.amethystclient.hud.Modules;
import com.amethystclient.hud.setting.BoolSetting;
import com.amethystclient.hud.setting.ModeSetting;
import com.amethystclient.ui.AmethystTheme;
import com.amethystclient.ui.Draw;
import com.amethystclient.ui.Motion;
import com.amethystclient.ui.Ui;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The enabled modules, one per line, sliding in and out as they're toggled. Lines line up with
 * whichever side of the screen the list is on.
 */
public class ModuleListModule extends HudModule {
	private static final int LINE = 11;
	private static final int PAD = 3;

	private final ModeSetting sort;
	private final ModeSetting color;
	private final BoolSetting background;
	private final BoolSetting bar;
	private final Map<ClientModule, Motion> slides = new HashMap<>();
	private final List<ClientModule> shown = new ArrayList<>();

	public ModuleListModule() {
		super("module_list", "Module List", "Lists the modules you have on", Category.CLIENT, false,
				new Position(1, 0, -MARGIN, MARGIN));
		sort = mode("sort", "Sort by", "Length", "Length", "Name", "Category");
		color = mode("color", "Colour", "Gradient", "Gradient", "Accent", "White");
		background = bool("background", "Background", true);
		bar = bool("bar", "Side bar", true);
	}

	private Motion slide(ClientModule module) {
		return slides.computeIfAbsent(module, m -> new Motion(m.enabled() ? 1f : 0f, 180));
	}

	private boolean alignRight() {
		return settings().anchorX > 0.5;
	}

	private int lineWidth(Draw d, ClientModule module) {
		return d.textWidth(module.name) + PAD * 2 + (bar.get() ? 2 : 0);
	}

	@Override
	public void measure(Draw d, boolean preview) {
		shown.clear();
		for (ClientModule module : Modules.ALL) {
			if (module == this) {
				continue;
			}
			Motion slide = slide(module);
			slide.set(module.enabled() ? 1f : 0f);
			if (slide.get() > 0.01f) {
				shown.add(module);
			}
		}
		Comparator<ClientModule> order = switch (sort.get()) {
			case "Name" -> Comparator.comparing(m -> m.name);
			case "Category" -> Comparator.<ClientModule, Category>comparing(m -> m.category).thenComparing(m -> m.name);
			default -> Comparator.<ClientModule>comparingInt(m -> -d.textWidth(m.name)).thenComparing(m -> m.name);
		};
		shown.sort(order);
		width = 0;
		height = 0;
		for (ClientModule module : shown) {
			width = Math.max(width, lineWidth(d, module));
			height += Math.round(LINE * slide(module).get());
		}
		if (shown.isEmpty() && preview) {
			width = Ui.chipWidth(d, "", "Module List");
			height = LINE;
		}
	}

	@Override
	public void render(Draw d, AmethystTheme t, int x, int y, boolean preview) {
		if (shown.isEmpty()) {
			if (preview) {
				d.text("Module List", x + PAD, y + 2, AmethystTheme.TEXT_MUTED);
			}
			return;
		}
		boolean right = alignRight();
		int ly = y;
		for (int i = 0; i < shown.size(); i++) {
			ClientModule module = shown.get(i);
			float p = slide(module).get();
			int w = lineWidth(d, module);
			// Slides in from the screen edge it's lined up with.
			int travel = Math.round((1 - p) * (w + MARGIN));
			int lx = right ? x + width - w + travel : x - travel;
			int h = Math.round(LINE * p);
			int textColor = switch (color.get()) {
				case "Accent" -> t.accent();
				case "White" -> AmethystTheme.TEXT_PRIMARY;
				default -> AmethystTheme.lerp(t.accent(), t.accentPressed(), shown.size() > 1 ? (float) i / (shown.size() - 1) : 0f);
			};
			// Fades rather than clips: scissor rects don't follow the HUD scale in every version.
			d.alpha(p);
			if (background.get()) {
				d.fill(lx, ly, lx + w, ly + h, AmethystTheme.withAlpha(t.bgSidebar(), backgroundAlpha()));
			}
			if (bar.get()) {
				int bx = right ? lx + w - 2 : lx;
				d.fill(bx, ly, bx + 2, ly + h, textColor);
			}
			if (p > 0.3f) {
				int tx = lx + PAD + (bar.get() && !right ? 2 : 0);
				d.text(module.name, tx, ly + 2, textColor);
			}
			d.alpha(1f);
			ly += h;
		}
	}
}
