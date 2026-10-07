package dev.prathamesh.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.prathamesh.model.AccountModel;
import dev.prathamesh.model.UserModel;
import dev.prathamesh.repository.AccountRepository;
import dev.prathamesh.repository.UserRepository;
import dev.prathamesh.types.AccountType;

@Component
public class SystemAccountInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;

    public SystemAccountInitializer(UserRepository userRepository, AccountRepository accountRepository) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (accountRepository.findFirstByType(AccountType.SYSTEM).isEmpty()) {
            UserModel systemUser = userRepository.save(new UserModel("SYSTEM", "system@ledger.internal"));
            accountRepository.save(new AccountModel(systemUser.getId(), AccountType.SYSTEM));
        }
    }
}