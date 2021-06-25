package com.wu.servlet.handler;

import com.wu.annotation.ioc.MyController;
import com.wu.annotation.mvc.MyRequestMapping;
import com.wu.annotation.mvc.MyRequestParam;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;

public class HandlerManager {

    // 类中各 @RequestMapping 注解修饰的方法对应的 MappingHandler 的集合
    public static List<MappingHandler> mappingHandlerList = new ArrayList<>();

    public static void resolveMappingHandler(List<Class<?>> classList) {
        //只处理标注@MyController的类
        classList.stream().filter(aClass -> aClass.isAnnotationPresent(MyController.class))
                .forEach(HandlerManager::parseHandlerFromController);
    }

    private static void parseHandlerFromController(Class<?> aClass) {
        Method[] methods = aClass.getDeclaredMethods();
        // 只处理包含了 @RequestMapping 注解的方法
        for (Method method : methods) {
            if (method.isAnnotationPresent(MyRequestMapping.class)) {
                // 获取赋值 @RequestMapping 注解的值，也就是客户端请求的路径，注意，uri不包括协议名和主机名
                String uri = aClass.getDeclaredAnnotation(MyRequestMapping.class).value();//类上的请求路径
                uri += method.getDeclaredAnnotation(MyRequestMapping.class).value();//方法上的请求路径
                // 存放@RequestParam注解的参数，value就是请求域里的变量名，和method方法的参数名可能不完全一致
                List<String> params = new ArrayList<>();
                for (Parameter parameter : method.getParameters()) {
                    if (parameter.isAnnotationPresent(MyRequestParam.class)) {
                        params.add(parameter.getAnnotation(MyRequestParam.class).value());
                    }
                }

                // List.toArray() 方法传入与 List.size() 恰好一样大的数组，可以提高效率
                String[] paramsStr = params.toArray(new String[params.size()]);
                MappingHandler mappingHandler = new MappingHandler(uri, aClass, method, paramsStr);
                HandlerManager.mappingHandlerList.add(mappingHandler);
            }
        }
    }
}
