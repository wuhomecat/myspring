package com.wu.annotation.aop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface MyPointcut {
    /**
     * 定义切点的名字，spring中这里还会用到 execution() 等表达式来解析
     * 简单起见，我们定义一个全限定类名+方法名来表示切点位置
     */
    String value() default "";
}
