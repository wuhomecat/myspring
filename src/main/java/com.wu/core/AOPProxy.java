package com.wu.core;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/*
* 根据传入的具体前置、后置方法、切入对象、切入点创建切入后的代理对象
* */
public class AOPProxy implements InvocationHandler {

    private Object aspect;
    private Method before;
    private Method after;
    private Object target;
    private String targetMethod;

    /**
     * @param aspect       织面对象，也就是 before 和 after 方法的调用者
     * @param before       具体前置方法：如果没有前置，则before对象为null
     * @param after        具体后置方法：如果没有后置，则after对象为null
     * @param target       被代理对象(目标对象)
     * @param targetMethod 被代理方法(目标方法)
     * @return 代理对象
     */
    public Object createProxy(Object aspect, Method before, Method after,
                              Object target, String targetMethod) {
        this.aspect = aspect;
        this.before = before;
        this.after = after;
        this.target = target;
        this.targetMethod = targetMethod;
        return Proxy.newProxyInstance(target.getClass().getClassLoader(),
                target.getClass().getInterfaces(), this);
    }

    /**
     * 被代理对象(proxy)的每一个方法(method)都会进入此方法进行处理
     *
     * @param proxy  被代理对象
     * @param method 被代理对象的某个方法
     * @param args   方法参数
     * @return 代理对象
     * @throws Throwable 不处理任何异常
     */
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // 我们只需要对 targetMethod 方法进行处理，其他方法直接返回原来的调用
        if (!method.getName().equals(targetMethod)) {
            return method.invoke(this.target, args);
        }

        Object result;
        if (before != null) {
            // before() 简单起见，方法没有参数
            before.invoke(this.aspect);
        }
        result = method.invoke(target);
        if (after != null) {
            after.invoke(this.aspect);
        }
        return result;
    }
}
