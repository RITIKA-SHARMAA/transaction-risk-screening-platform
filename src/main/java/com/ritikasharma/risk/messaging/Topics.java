package com.ritikasharma.risk.messaging;

public final class Topics {

    public static final String TXN_SUBMITTED = "txn.submitted";
    public static final String TXN_SCREENING_SIGNAL = "txn.screening-signal";
    public static final String TXN_RISK_SIGNAL = "txn.risk-signal";
    public static final String TXN_DECIDED = "txn.decided";

    public static final String DLT_SUFFIX = ".DLT";

    private Topics() {
    }
}
