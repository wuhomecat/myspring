package com.wu.demo;

import com.wu.annotation.ioc.MyAutoWired;
import com.wu.annotation.ioc.MyController;
import com.wu.annotation.mvc.MyRequestMapping;

@MyController
@MyRequestMapping("/user")
public class UserControllerImpl {
    @MyAutoWired
    private UserService userService;

    @MyRequestMapping("/find")
    public String findAll() {
        userService.findAll();
        return "controller done";
    }
}
