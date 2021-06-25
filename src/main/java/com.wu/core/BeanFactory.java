package com.wu.core;

import com.wu.annotation.aop.MyAfter;
import com.wu.annotation.aop.MyAspect;
import com.wu.annotation.aop.MyBefore;
import com.wu.annotation.aop.MyPointcut;
import com.wu.annotation.ioc.MyAutoWired;
import com.wu.annotation.ioc.MyComponent;
import com.wu.annotation.ioc.MyController;
import com.wu.annotation.ioc.MyService;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/*
 * BeanFactory负责的是IOC的核心功能：
 *   - 将扫描到的类中使用指定注解的都做初始化，并将实例保存到IOC容器中
 * */
public class BeanFactory {
    //IOC容器：key/value=指定类的Class对象/指定类的实例
    private static Map<Class<?>, Object> ioc = new ConcurrentHashMap<>();
    /**
     * set存放带有 @AutoWired 注解修饰的属性的类（将set转成线程安全的set）：避免重复遍历整个classList
     */
    private static Set<Class<?>> beansHasAutoWiredField = Collections.synchronizedSet(new HashSet<>());

    /*
     * getBean：通过类的Class对象获取对应的实例
     * */
    public static Object getBean(Class<?> clazz) {
        return ioc.get(clazz);
    }

    /**
     * 1、遍历classList中的所有类，创建实例加入IOC容器
     * 2、处理AOP注解的bean对象，更新IOC容器中的被代理对象
     * 3、遍历set存放的所有标注了@AutoWired的Class对象，做注入处理
     */
    public static void initBean(List<Class<?>> classList) throws Exception {
        // 因为"类定义"在后续处理 类中的 @RequestMapping 注解，生成处理器时还要使用，
        // 因此这里要创建新容器，不能修改原引用
        List<Class<?>> classesToCreate = new ArrayList<>(classList);
        // 保存被 @Aspect 注解的切面类：避免重复遍历整个classList
        List<Class<?>> aspectClasses = new ArrayList<>();

        //1、遍历扫描到的所有类，都创建实例加入IOC容器
        for (Class<?> clazz : classesToCreate) {
            createBean(clazz);
            if(clazz.isAnnotationPresent(MyAspect.class))
                aspectClasses.add(clazz);
        }

        // 2、处理AOP注解的bean对象，更新IOC容器中的被代理对象
        resolveAOP(aspectClasses);

        // 3、遍历set存放的所有标注了@AutoWired的Class对象，做注入处理
        for (Class<?> clazz : beansHasAutoWiredField) {
            resolveAutowired(clazz);
        }
    }


    /**
     * 通过 Class 对象创建实例，加入IOC容器
     *  - 将属性标注了@Autowired的Class对象加入set：beansHasAutoWiredField
     * @param clazz 需要创建实例的 Class 对象
     */
    private static void createBean(Class<?> clazz) throws IllegalAccessException, InstantiationException {
        // 只处理 @MyComponent / @MyController / @MyService注解的类
        if (!clazz.isAnnotationPresent(MyComponent.class)
                && !clazz.isAnnotationPresent(MyController.class)
                && !clazz.isAnnotationPresent(MyService.class)) {
            return;
        }
        //将所有属性标注了@Autowired的Class对象加入set
        for (Field field : clazz.getDeclaredFields()) {
            if (field.isAnnotationPresent(MyAutoWired.class)) {
                beansHasAutoWiredField.add(clazz);
            }
        }
        // 初始化对象
        Object bean = clazz.newInstance();
        //加入IOC容器
        ioc.put(clazz, bean);

    }

    /**
     * 对于所有被 @Aspect 注解修饰的类：
     * 遍历他们定义的方法，处理 @Pointcut、@Before 以及 @After 注解
     * 创建被切入对象的代理对象，更新到IOC容器中
     */
    private static void resolveAOP(List<Class<?>> aspectClasses)
            throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        if (aspectClasses.size() == 0) {
            return;
        }
        // 初始化每个@Aspect标注的类，为简单起见，这里先假定每一个代理类都做如下处理
        // 并且每个切面类最多只有一个切点，一个前置以及一个后置处理器，
        for (Class<?> aClass : aspectClasses) {
            Method before = null;
            Method after = null;
            String methodName = null;//被切入的方法的方法名
            Object target = null;
            String pointcutName = null;//切入点的别名

            //处理@MyPointcut：该注解用于方法上
            //注解中指定的value是要切入的具体位置，例如：com.example.service.impl.UserServiceImpl.findAll()
            //从value我们可以得到被切入类的全限定名、方法名
            //从使用该注解的方法上可以得到该pointcut的方法名，可以作为该切入点的唯一标识pointcutName
            Object bean = aClass.newInstance();
            for (Method m : bean.getClass().getDeclaredMethods()) {
                if (m.isAnnotationPresent(MyPointcut.class)) {
                    // pointcut:指定要切入的全限定类名.方法名，例如：com.example.service.impl.UserServiceImpl.findAll()
                    String pointcut = m.getAnnotation(MyPointcut.class).value();
                    String classStr = pointcut.substring(0, pointcut.lastIndexOf("."));
                    target = Thread.currentThread().getContextClassLoader().loadClass(classStr).newInstance();
                    methodName = pointcut.substring(pointcut.lastIndexOf(".") + 1);
                    pointcutName = m.getName();
                }
            }
            for (Method m : bean.getClass().getDeclaredMethods()) {
                //处理@MyBefore：该注解用在方法上，希望在切点对象之前执行
                //注解中指定的value是切入点的唯一标识pointcutName，这样就能实现前置方法和切入点的绑定
                if (m.isAnnotationPresent(MyBefore.class)) {
                    String value = m.getAnnotation(MyBefore.class).value();
                    value = value.substring(0, value.indexOf("("));
                    if (value.equals(pointcutName)) {
                        before = m;
                    }
                }
                //处理@MyAfter：该注解用在方法上，希望在切点对象之后执行
                //注解中指定的value是切入点的唯一标识pointcutName，这样就能实现后置方法和切入点的绑定
                else if (m.isAnnotationPresent(MyAfter.class)) {
                    String value = m.getAnnotation(MyAfter.class).value();
                    value = value.substring(0, value.indexOf("("));
                    if (value.equals(pointcutName)) {
                        after = m;
                    }
                }
            }
            // 获取代理对象：这里用到了动态代理
            Object proxy = new AOPProxy().createProxy(bean, before, after,
                    target, methodName.substring(0, methodName.indexOf("(")));
            // 更新IOC容器
            ioc.put(target.getClass(), proxy);
        }
    }

    /*
     * 依赖注入，处理一个bean对象中的所有@Autowired属性，完成注入
     * */
    private static void resolveAutowired(Class<?> clazz) {
        //从IOC容器中获取clazz对应的bean
        Object bean = ioc.get(clazz);

        // 遍历类中所有定义的属性，如果属性带有 @AutoWired 注解，则需要注入对应依赖
        for (Field field : bean.getClass().getDeclaredFields()) {
            if (!field.isAnnotationPresent(MyAutoWired.class)) {
                continue;
            }
            Class<?> fieldType = field.getType();
            field.setAccessible(true);
            if (fieldType.isInterface()) {
                // 如果依赖的类型是接口，则查询其实现类,
                // class1.isAssignableFrom(class2) = true 代表class2是class1类型，可分配class2对象给class1
                for (Class<?> key : ioc.keySet()) {
                    if (fieldType.isAssignableFrom(key)) {
                        fieldType = key;
                        break;
                    }
                }
            }
            try {
                //依赖注入的关键，提取IOC容器里的最新的bean做注入
                field.set(bean, BeanFactory.getBean(fieldType));
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
    }
}
