package pl.ingotlabs.base.economy;

import java.util.UUID;

public final class Account {
    private final UUID uuid;
    private volatile String name;
    private volatile long balance;

    Account(UUID uuid, String name, long balance) {
        this.uuid = uuid;
        this.name = name;
        this.balance = balance;
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public long balance() {
        return balance;
    }

    void setName(String name) {
        this.name = name;
    }

    void setBalance(long balance) {
        this.balance = balance;
    }
}
