package accountmanager;

import com.mojang.logging.LogUtils;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;

import java.io.File;

/** Minimal holder for the logger and config folder the account code expects. */
public final class AccountsMod {
    public static final Logger LOG = LogUtils.getLogger();
    public static final File FOLDER = FabricLoader.getInstance().getConfigDir().resolve("accounts").toFile();

    private AccountsMod() {
    }
}
