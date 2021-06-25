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
     * set存放带有 @AutoWired 注解修饰的属性的类（将set转成线程安全的set）
     */
    private static Set<Class<?>> beansHasAutoWiredField = Collections.synchronizedSet(new HashSet<>());

    /*
    * getBean：通过类的Class对象获取对应的实例
    * */
    public static Object getBean(Class<?> clazz){
        return ioc.get(clazz);
    }

    /**
     * 根据类列表 classList 来查找所有需要初始化的类并放入 Component 工厂，
     * 并且处理类中所有带 @AutoWired 注解的属性的依赖问题。
     */
    public static void initBean(List<Class<?>> classList) throws Exception {
        // 因为"类定义"在后续处理 类中的 @RequestMapping 注解，生成处理器时还要使用，
        // 因此这里要创建新容器，不能修改原引用
        List<Class<?>> classesToCreate = new ArrayList<>(classList);
        // 保存被 @Aspect 注解的切面类
        List<Class<?>> aspectClasses = new ArrayList<>();

        //1、遍历扫描到的所有类
        //2、对标注了@Autowired的属性做依赖注入（createBean()中）
        for (Class<?> clazz : classesToCreate) {
            if (clazz.isAnnotationPresent(MyAspect.class)) {
                aspectClasses.add(clazz);
            } else {
                createBean(clazz);
            }
        }
        // 3、使用动态代理处理AOP
        resolveAOP(aspectClasses);

        // 4、标注@Autowired的属性在上面的createBean已经注入过了，但在引入aop后可能有的属性发生了更新，需要重新注入
        for (Class<?> clazz : beansHasAutoWiredField) {
            createBean(clazz);
        }
    }

    /**
     * 通过 Class 对象创建实例
     *
     * @param clazz 需要创建实例的 Class 对象
     */
    private static void createBean(Class<?> clazz) throws IllegalAccessException, InstantiationException {
        // 只处理 @MyComponent / @MyController / @MyService注解的类
        if (!clazz.isAnnotationPresent(MyComponent.class)
                && !clazz.isAnnotationPresent(MyController.class)
                && !clazz.isAnnotationPresent(MyService.class)) {
            return;
        }

        Object bean = null;
        // 初始化对象
        bean = clazz.newInstance();

        // 遍历类中所有定义的属性，如果属性带有 @AutoWired 注解，则需要注入对应依赖
        for (Field field : bean.getClass().getDeclaredFields()) {
            if (!field.isAnnotationPresent(MyAutoWired.class)) {
                continue;
            }
            // 将需要注入其他 Bean 的类保存起来，因为等 AOP 代理类生成之后，需要更新它们
            BeanFactory.beansHasAutoWiredField.add(clazz);
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
            field.set(bean, BeanFactory.getBean(fieldType));
        }
        // todo 这里可能AutoWired注入失败，例如存在循环依赖，或者bean工厂中根本不存在，目前暂时先不处理
        ioc.put(clazz, bean);
    }

    /**
     * 对于所有被 @Aspect 注解修饰的类，
     * 遍历他们定义的方法，处理 @Pointcut、@Before 以及 @After 注解
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

}
