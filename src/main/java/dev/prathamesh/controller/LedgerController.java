package dev.prathamesh.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.prathamesh.service.LedgerService;
import dev.prathamesh.types.AccountBalanceResponse;
import dev.prathamesh.types.LedgerVerifyResponse;

@RestController
@RequestMapping("/api/v1/ledger")
public class LedgerController {

    private final LedgerService ledgerService;

    public LedgerController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @GetMapping("/accounts/{id}")
    public AccountBalanceResponse balance(@PathVariable Long id) {
        return ledgerService.accountBalance(id);
    }

    @GetMapping("/verify")
    public LedgerVerifyResponse verify() {
        return ledgerService.verify();
    }
}