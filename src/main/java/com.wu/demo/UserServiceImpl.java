package com.wu.demo;

import com.wu.annotation.ioc.MyService;


@MyService
public class UserServiceImpl implements UserService {

    @Override
    public void findAll() {
        System.out.println("service done");
    }


}
