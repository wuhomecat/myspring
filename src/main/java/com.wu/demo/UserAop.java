package com.wu.demo;

import com.wu.annotation.aop.MyAfter;
import com.wu.annotation.aop.MyAspect;
import com.wu.annotation.aop.MyBefore;
import com.wu.annotation.aop.MyPointcut;
import com.wu.annotation.ioc.MyComponent;

@MyAspect
@MyComponent
public class UserAop {

    @MyPointcut("com.wu.demo.UserServiceImpl.findAll()")
    public void mypointcut(){

    }

    @MyBefore("mypointcut()")
    public void before(){
        System.out.println("before...");
    }

    @MyAfter("mypointcut()")
    public void after(){
        System.out.println("after...");
    }

}
