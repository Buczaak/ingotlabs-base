package pl.ingotlabs.base.economy;

import java.util.UUID;

public record TxRecord(long timestamp, UUID account, TxType type, long delta, long balanceAfter,
                       String other, String note) {
}
