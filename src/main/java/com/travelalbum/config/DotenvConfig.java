package com.travelalbum.config;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

// LƯU Ý: ĐÃ XÓA @Configuration (EnvironmentPostProcessor không chạy qua Spring Bean)
public class DotenvConfig implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment,
                                       SpringApplication application) {

        Dotenv dotenv = Dotenv.configure()
                .directory("./")
                .filename("...env.dev")
                .ignoreIfMissing()
                .load();

        System.out.println("Dotenv loaded successfully!");
        System.out.println("JWT_SECRET = " + dotenv.get("JWT_SECRET"));

        Map<String, Object> props = new HashMap<>();
        dotenv.entries().forEach(entry -> props.put(entry.getKey(), entry.getValue()));

        // Đổi sang addFirst để nâng độ ưu tiên lên cao nhất
        environment.getPropertySources()
                .addFirst(new MapPropertySource("dotenvProperties", props));
    }
}