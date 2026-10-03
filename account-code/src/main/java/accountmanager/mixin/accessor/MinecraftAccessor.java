package accountmanager.mixin.accessor;

import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.ProfileResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;
import net.minecraft.client.gui.screens.social.RemoteFriendListUpdateHandler;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.multiplayer.chat.report.ReportingContext;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.server.Services;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.concurrent.CompletableFuture;

@Mixin(Minecraft.class)
public interface MinecraftAccessor {
    @Mutable
    @Accessor("user")
    void acc$setUser(User user);

    @Mutable
    @Accessor("profileKeyPairManager")
    void acc$setProfileKeyPairManager(ProfileKeyPairManager manager);

    @Mutable
    @Accessor("userApiService")
    void acc$setUserApiService(UserApiService service);

    @Mutable
    @Accessor("skinManager")
    void acc$setSkinManager(SkinManager manager);

    @Mutable
    @Accessor("playerSocialManager")
    void acc$setPlayerSocialManager(PlayerSocialManager manager);

    @Mutable
    @Accessor("remoteFriendListUpdateHandler")
    void acc$setRemoteFriendListUpdateHandler(RemoteFriendListUpdateHandler handler);

    @Mutable
    @Accessor("reportingContext")
    void acc$setReportingContext(ReportingContext context);

    @Mutable
    @Accessor("profileFuture")
    void acc$setProfileFuture(CompletableFuture<ProfileResult> future);

    @Mutable
    @Accessor("services")
    void acc$setServices(Services services);

    @Accessor("rightClickDelay")
    int acc$getRightClickDelay();

    @Accessor("rightClickDelay")
    void acc$setRightClickDelay(int delay);

    @Accessor("missTime")
    int acc$getMissTime();

    @Invoker("startAttack")
    boolean acc$startAttack();
}
