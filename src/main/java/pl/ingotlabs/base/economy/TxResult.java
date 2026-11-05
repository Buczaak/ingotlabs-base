package pl.ingotlabs.base.economy;

public record TxResult(Status status, long balance) {

    public enum Status {
        SUCCESS,
        NO_ACCOUNT,
        INVALID_AMOUNT,
        INSUFFICIENT_FUNDS,
        OVER_LIMIT
    }

    public boolean success() {
        return status == Status.SUCCESS;
    }
}
