package com.wu.demo.controller;

import com.wu.annotation.ioc.MyService;


@MyService
public class UserServiceImpl implements UserService {

    @Override
    public void findAll() {
        System.out.println("service done");
    }


}
