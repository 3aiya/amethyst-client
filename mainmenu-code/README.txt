Main menu (custom title screen) code  -  package: accountmanager

SCREEN
 gui/screen/TitleScreen        - the custom main menu (logo, panorama, Singleplayer/Multiplayer/Realms/Options/Quit
                                 buttons with texture labels, Mod Menu / Replay / Flashback / language / accessibility
                                 icon buttons, support-link buttons, Spotify strip, module list)
 gui/screen/PanicTitleScreen   - plain fallback title screen used when the client is hidden

HOW IT IS INSTALLED (mixins)
 mixin/GuiSetScreenMixin        - when vanilla TitleScreen is about to open, swaps in TitleScreen (checks MenuPrefs.customMainMenuEnabled())
 mixin/TitleScreenSupportMixin  - adds extra buttons to the vanilla title screen (left: links, right: Modules/Matchmaking/Profiles)
 (the original also repairs stray vanilla title screens in MinecraftClientMixin.tick - not included)

SMALL SUPPORT CLASSES (included)
 util: Theme, ThemeTextures, UiScale, Links, MenuPrefs, Colors, Marquee, Perf
 gui/vanillaui: HoverFades, UiBounds, UiContexts, UiRenderer, assets/UiAssets, components/{UiSizing,UiText,UiTone,CompactTheme}

RESOURCES (assets/accountmanager)
 textures/gui/title/  background/panorama_0-5 + overlay, button_text/*.png, icons/*.png, client_logo.png(+.mcmeta), loading_logo.png
 sounds/gui/main_menu_click.ogg + sounds.json entry
 NOTE: client_logo.png / loading_logo.png are still the original artwork - replace with your own.

NOT INCLUDED (TitleScreen references these from the original client - remove or replace)
 - Spotify strip: AutismSpotify + HudManager.spotify* helpers  (TitleScreen ~lines 738-830)
 - Module list / Modules & Macros button: Module, ModuleCategory, ModuleRegistry, ModuleScreen (~lines 456-590)
 - Matchmaking / Profiles buttons: ClientModule, OverlayManager, IOverlay, OverlayHostScreen (~lines 490-515)
 - DiscordLogin.modVersionString() (version text, ~line 610), PackHideState, Config.customMainMenu, LiteVariant
 - Links.java: donate/discord URLs replaced with example.com placeholders.
 - Register in your mixins json: "GuiSetScreenMixin", "TitleScreenSupportMixin"
 - LICENSE is GPL-3.0, keep it if you distribute.
