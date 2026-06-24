package com.qarzbot.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.Locale;

/**
 * i18n konfiguratsiyasi. classpath'dagi messages_*.properties fayllardan tarjimalarni o'qiydi.
 * Standart til — uz. Kalit topilmasa, kalitning o'zi qaytariladi (setUseCodeAsDefaultMessage).
 */
@Configuration
public class I18nConfig {

    @Bean
    public MessageSource messageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        source.setUseCodeAsDefaultMessage(true);
        source.setDefaultLocale(new Locale("uz"));
        return source;
    }
}
