package dev.prathamesh.service;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;

import dev.prathamesh.model.AccountModel;
import dev.prathamesh.model.UserModel;
import dev.prathamesh.repository.AccountRepository;
import dev.prathamesh.repository.UserRepository;
import jakarta.transaction.Transactional;

@Service
public class UserService{
	UserRepository userRepository;
	AccountRepository accountRepository;
	UserService(UserRepository userRepository, AccountRepository accountRepository){
		this.userRepository=userRepository;
		this.accountRepository=accountRepository;
	}
	
	@Transactional
	public UserModel createUser(UserModel user) {
		UserModel u=userRepository.save(user);
		AccountModel account = new AccountModel(u.getId());
		account.setBalance(new BigDecimal(2000));
		accountRepository.save(account);
		return u;
	}
	
	
}