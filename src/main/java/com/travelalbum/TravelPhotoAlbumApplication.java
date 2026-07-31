package com.travelalbum;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

@SpringBootApplication
public class TravelPhotoAlbumApplication {

    public static void main(String[] args) {
        // Đọc trực tiếp file .env.dev ở root dự án (song song với pom.xml)
        try (InputStream input = new FileInputStream(".env.dev")) {
            Properties prop = new Properties();
            prop.load(input);
            prop.forEach((key, value) -> System.setProperty(key.toString(), value.toString()));
            System.out.println(">>> Loaded .env.dev successfully from root!");
        } catch (Exception e) {
            System.err.println(">>> ERROR: Could not find .env.dev in root directory!");
        }

        System.out.println(">>> JWT_SECRET = " + System.getProperty("JWT_SECRET"));

        SpringApplication.run(TravelPhotoAlbumApplication.class, args);
    }
}