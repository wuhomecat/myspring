package com.wu.servlet;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class TestGetResource {

    public void getFile(){
        InputStream in = null;
        in = this.getClass().getClassLoader().getResourceAsStream("application.properties");
        Properties p = new Properties();
        try {
            p.load(in);
            System.out.println(p.getProperty("scanPackage"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public static void main(String[] args) {
        TestGetResource testGetResource = new TestGetResource();
        testGetResource.getFile();
    }
}
