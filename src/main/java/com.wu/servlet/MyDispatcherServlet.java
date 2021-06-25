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
    //保存所有被扫描到的类名
    private List<String> classNames = new ArrayList<>();
    private List<Class<?>> classList = new ArrayList<>();
    //核心IOC容器，保存所有初始化的bean
    private Map<Class<?>, Object> ioc = new HashMap<>();
    //保存所有ulr和方法的映射关系
    private Map<String, Method> handlerMapping = new HashMap<>();

    //bean工厂，用于将所有注解标注的类初始化到IOC容器中
    BeanFactory beanFactory = new BeanFactory();


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
            logger.info(config.getInitParameter(LOCATION));
            //1、加载配置文件：通过ServletConfig参数可以获取web.xml里关于DispatcherServlet的配置信息
            doLoadConfig(config.getInitParameter(LOCATION));
            logger.info("load config success");

            //2、扫描指定包的所有类到classNames中
            doScannerCreateName(p.getProperty("scanPackage"));
            logger.info("ScannerCreateClass success");
            //3、初始化所有实例到IOC容器中，并完成依赖注入和AOP切入：交给beanFactory完成
            BeanFactory.initBean(classList);
            logger.info("initBean success");
            //4、保存url和方法的映射关系
            HandlerManager.resolveMappingHandler(classList);
            logger.info("resolveMappingHandler success");
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
        try {
            fis = this.getClass().getClassLoader().getResourceAsStream(location);
            p.load(fis);
        } catch (Exception e) {
            System.out.println("找不到文件");
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
    * 递归扫描指定包下的所有类，将全限定类名保存到classNames中
    * debug:功能正常
    * */
    private void doScannerCreateName(String packageName){
        //将所有的包路径转换为文件路径:因为是从输出的文件夹里读取的
        String path = "/" + packageName.replaceAll("\\.", "/");
        URL url = this.getClass().getClassLoader().getResource(path);
        File dir = new File(url.getFile());
        for(File file : dir.listFiles()){
            //如果是文件夹，继续递归
            if(file.isDirectory()){
                doScannerCreateName(packageName + "." + file.getName());
            } else{
                //加入classNames的类名为：全限定类名
                //classNames.add(packageName + "." + file.getName().replaceAll(".class", "").trim());
                String className = packageName + "." + file.getName().replaceAll(".class", "").trim();
                try {
                    classList.add(Class.forName(className));
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /*
     * 递归扫描指定包下的所有类，将类的Class对象保存到classNames中
     * */
    private void doScannerCreateClass(String packageName) throws IOException, ClassNotFoundException {
        //List<Class<?>> classList = new ArrayList<>();
        String path = packageName.replace(".", "/");
        // 线程上下文类加载器默认是应用类加载器，即 ClassLoader.getSystemClassLoader();
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

        // 使用类加载器对象的 getResources(ResourceName) 方法获取资源集
        Enumeration<URL> resources = classLoader.getResources(path);
        while (resources.hasMoreElements()) {
            URL url = resources.nextElement();
            // 获取协议类型，判断是否为 jar 包
            if (url.getProtocol().contains("jar")) {
                // 将打开的 url 返回的 URLConnection 转换成其子类 JarURLConnection 包连接
                JarURLConnection jarURLConnection = (JarURLConnection) url.openConnection();
                String jarFilePath = jarURLConnection.getJarFile().getName();
                classList.addAll(getClassesFromJar(jarFilePath, path));
            } else {
                // 简单起见，我们暂时仅实现扫描 jar 包中的类
                // todo
            }
        }
        //return classList;
    }

    private static List<Class<?>> getClassesFromJar(String jarFilePath, String path) throws IOException, ClassNotFoundException {
        List<Class<?>> classes = new ArrayList<>();
        JarFile jarFile = new JarFile(jarFilePath);
        Enumeration<JarEntry> jarEntrys = jarFile.entries();
        while (jarEntrys.hasMoreElements()) {
            JarEntry jarEntry = jarEntrys.nextElement();
            // com/caozhihu/spring/test/Test.class
            String entryName = jarEntry.getName();
            if (entryName.startsWith(path) && entryName.endsWith(".class")) {
                // 全限定类名
                String classFullName = entryName.replace("/", ".").substring(0, entryName.length() - 6);
                // 使用类的全限定类名初始化类，并将类对象保存
                classes.add(Class.forName(classFullName));
            }
        }
        return classes;
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
                logger.info("执行方法出错");
                e.printStackTrace();
            }
        }
        resp.getWriter().println("failed!");

    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doGet(req, resp);
    }
}
