package dev.prathamesh.types;

import java.math.BigDecimal;

public record AccountBalanceResponse(
        Long accountId,
        BigDecimal cachedBalance,
        BigDecimal ledgerBalance,
        boolean consistent
) {}