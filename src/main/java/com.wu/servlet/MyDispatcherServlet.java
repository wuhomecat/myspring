package com.wu.servlet;

import com.wu.core.BeanFactory;
import com.wu.servlet.handler.HandlerManager;
import com.wu.servlet.handler.MappingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;


/*
* 作为springmvc的启动入口，实现：
* 1. 加载配置文件
* */
public class MyDispatcherServlet extends HttpServlet {
    //日志工具
    private static Logger logger = LoggerFactory.getLogger(MyDispatcherServlet.class);

    //和web.xml中的param-name值一致
    private static final String LOCATION = "contextConfigLocation";
    //保存所有的配置信息
    private Properties p = new Properties();
    //保存所有被扫描到的类
    private List<Class<?>> classList = new ArrayList<>();


    public MyDispatcherServlet(){
        super();
    }

    /*
    * init方法，启动时会自动执行，所有需要启动执行的方法都在这里调用，包括：
    *   - 加载配置文件
    *   - 扫描注解标注的类
    *   - 初始化扫描到的类，将实例保存到IOC容器上
    *   - 依赖注入：处理@Autowired
    *   - 保存所有ulr和方法的映射关系
    * */
    @Override
    public void init(ServletConfig config){

        try {
            //1、加载配置文件：通过ServletConfig参数可以获取web.xml里关于DispatcherServlet的配置信息
            doLoadConfig(config.getInitParameter(LOCATION));
            //2、扫描指定包的所有类到classNames中
            doScanner(p.getProperty("scanPackage"));
            //3、初始化所有实例到IOC容器中，并完成依赖注入和AOP切入：交给beanFactory完成
            BeanFactory.initBean(classList);
            //4、保存url和方法的映射关系
            HandlerManager.resolveMappingHandler(classList);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /*
    * 从web.xml中指定的位置读取配置文件
    * */
    private void doLoadConfig(String location){
        InputStream fis = null;
        //读取到的文件位置为：classpath:application.properties，需要提取出application.properties
        location = location.substring(location.indexOf(":") + 1);
        try {
            fis = this.getClass().getClassLoader().getResourceAsStream(location);
            p.load(fis);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if(null != fis) {
                try {
                    fis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /*
    * 递归扫描指定包下的所有类，通过反射获取全限定类名的class对象，保存到classList中
    * */
    private void doScanner(String packageName){
        //将所有的包路径转换为文件路径:因为是从输出的文件夹里读取的
        String path = "/" + packageName.replaceAll("\\.", "/");
        URL url = this.getClass().getClassLoader().getResource(path);
        File dir = new File(url.getFile());
        for(File file : dir.listFiles()){
            //如果是文件夹，继续递归
            if(file.isDirectory()){
                doScanner(packageName + "." + file.getName());
            } else{
                //根据.class文件构造对应的全限定类名
                //例如：packageName=com.wu.demo，文件名为：UserAop.class，className=com.wu.demo.UserAop
                String className = packageName + "." + file.getName().replaceAll(".class", "").trim();
                try {
                    //通过全限定类名获取它的Class对象，存入classList
                    classList.add(Class.forName(className));
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }
    }


    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        for (MappingHandler mappingHandler : HandlerManager.mappingHandlerList) {
            // 遍历所有的handler，在它的handle方法里做uri匹配
            // 如果某个 handler 可以处理(返回true)，则返回即可
            try {
                if (mappingHandler.handle(req, resp)) {
                    resp.getWriter().println("success!");
                    return;
                }
            } catch (IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
            }
        }
        resp.getWriter().println("404 not found!");

    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doGet(req, resp);
    }
}
