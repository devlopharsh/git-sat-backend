package com.gitsat.backend.config;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class StaticAssetsConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path assetsRoot = Paths.get("assets").toAbsolutePath().normalize();
        registry.addResourceHandler("/assets/**")
                .addResourceLocations(assetsRoot.toUri().toString());
    }
}
