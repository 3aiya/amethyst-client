package accountmanager.util;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

import java.io.File;
import java.util.Iterator;
import java.util.List;

public final class AccountManager extends PersistentNbtManager<Account> implements Iterable<Account> {
    private static final AccountManager INSTANCE = new AccountManager();

    private AccountManager() {
    }

    public static AccountManager get() {
        INSTANCE.ensureLoaded();
        MeteorImport.ensureImported();
        INSTANCE.ensureStableIds();
        return INSTANCE;
    }

    private synchronized void ensureStableIds() {
        boolean changed = false;
        for (Account account : items) {
            if (account == null) continue;
            account.stableId();
            changed |= account.generatedStableId;
            account.generatedStableId = false;
        }
        if (changed) save();
    }

    public synchronized Account findById(String id) {
        if (id == null || id.isBlank()) return null;
        for (Account account : items) {
            if (id.equals(account.stableId())) return account;
        }
        return null;
    }

    public synchronized void applyResolvedCredentials(Account resolved) {
        if (resolved == null || resolved.id == null) return;
        Account stored = findById(resolved.id);
        if (stored == null) return;
        stored.label = resolved.label;
        stored.token = resolved.token;
        stored.sessionToken = resolved.sessionToken;
        stored.username = resolved.username;
        stored.uuid = resolved.uuid;
        stored.sessionTokenExpiresAt = resolved.sessionTokenExpiresAt;
        save();
    }

    public synchronized boolean rename(String accountId, String newLabel) {
        if (newLabel == null || newLabel.isBlank()) return false;
        Account stored = findById(accountId);
        if (stored == null || stored.type != AccountType.Cracked) return false;
        String trimmed = newLabel.trim();
        if (trimmed.equals(stored.label)) return true;
        for (Account account : items) {
            if (account != stored && trimmed.equalsIgnoreCase(account.label)) return false;
        }
        stored.label = trimmed;
        stored.username = trimmed;
        stored.uuid = net.minecraft.core.UUIDUtil.createOfflinePlayerUUID(trimmed).toString();
        save();
        return true;
    }

    public synchronized void invalidateSessionToken(String accountId) {
        Account stored = findById(accountId);
        if (stored == null || stored.type != AccountType.Microsoft) return;
        stored.token = "";
        stored.sessionTokenExpiresAt = 0L;
        save();
    }

    @Override
    protected File saveFile() {
        return new File(Minecraft.getInstance().gameDirectory, "accounts.nbt");
    }

    @Override
    protected String listKey() {
        return "accounts";
    }

    @Override
    protected Account fromTag(CompoundTag tag) {
        return new Account().fromTag(tag);
    }

    @Override
    protected CompoundTag toTag(Account item) {
        return item.toTag();
    }

    @Override
    protected String describe() {
        return "Accounts";
    }

    public synchronized void add(Account account) {
        if (account == null) return;
        items.add(account);
        save();
    }

    public synchronized int addAll(List<Account> accounts) {
        if (accounts == null || accounts.isEmpty()) return 0;
        int added = 0;
        for (Account account : accounts) {
            if (account != null) {
                items.add(account);
                added++;
            }
        }
        if (added > 0) save();
        return added;
    }

    public synchronized void remove(Account account) {
        if (items.remove(account)) save();
    }

    public synchronized int removeExpired() {
        int removed = 0;
        Iterator<Account> iterator = items.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().checkStatus == Account.CheckStatus.EXPIRED) {
                iterator.remove();
                removed++;
            }
        }
        if (removed > 0) save();
        return removed;
    }

    public void login(Account account) {
        if (account == null) return;
        Thread thread = new Thread(() -> {
            if (account.fetchInfo() && account.login()) {
                save();
                ClientMessaging.sendPrefixed("Logged in as " + account.displayName() + ".");
            } else {
                ClientMessaging.sendPrefixed("Failed to login account: " + account.displayName() + account.failureSuffix());
            }
        }, "Account-Login");
        thread.setDaemon(true);
        thread.start();
    }

    public void loginMicrosoft(Account account) {
        if (account == null || account.type != AccountType.Microsoft) return;
        MicrosoftLogin.getRefreshToken(refreshToken -> {
            if (refreshToken == null) {
                ClientMessaging.sendPrefixed("Microsoft login cancelled or failed.");
                return;
            }
            account.label = refreshToken;
            Thread thread = new Thread(() -> {
                if (account.fetchInfo() && account.login()) {
                    synchronized (this) {
                        if (!items.contains(account)) items.add(account);
                    }
                    save();
                    ClientMessaging.sendPrefixed("Logged in as " + account.displayName() + ".");
                } else {
                    ClientMessaging.sendPrefixed("Failed to login Microsoft account" + account.failureSuffix() + ".");
                }
            }, "Microsoft-Login");
            thread.setDaemon(true);
            thread.start();
        });
    }

    @Override
    public Iterator<Account> iterator() {
        return all().iterator();
    }
}
