package dev.prathamesh.types;

import java.math.BigDecimal;
import java.util.List;

public record LedgerVerifyResponse(
        BigDecimal totalNet,
        boolean zeroSum,
        List<Long> mismatchedAccountIds
) {}