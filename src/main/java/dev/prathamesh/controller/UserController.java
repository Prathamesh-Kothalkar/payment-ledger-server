package dev.prathamesh.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.prathamesh.model.UserModel;
import dev.prathamesh.service.UserService;

@RestController()
@RequestMapping("/api/v1/user")
public class UserController{
	
	UserService userService ;
	
	public UserController(UserService userService) {
		this.userService=userService;
	}
	
	@PostMapping()
	public UserModel create(@RequestBody UserModel user) {
		return userService.createUser(user);
	}
}