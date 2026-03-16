package com.aiconsultant.consultant.controller;

import com.aiconsultant.consultant.entity.User;
import com.aiconsultant.consultant.pojo.LoginDTO;
import com.aiconsultant.consultant.pojo.UserDTO;
import com.aiconsultant.consultant.service.UserService;
import com.aiconsultant.consultant.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import com.aiconsultant.consultant.pojo.Result;

@RestController
@RequestMapping("/user")
public class UserContorller {
    @Autowired
    private UserService userService;

    @PostMapping("/login")
    public Result login(@RequestBody LoginDTO loginDTO) {
        String username = loginDTO.getUsername();
        String password = loginDTO.getPassword();
        return userService.login(username,password);
    }

    @PostMapping("/me")
    public Result info() {
        UserDTO userDTO = UserHolder.getUser();
        return Result.ok(userDTO);
    }

    @PostMapping("/register")
    public Result register(@RequestBody User user) {
        return userService.register(user);
    }
}
