package de.tum.in.www1.artemis.config;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

import tech.jhipster.config.locale.AngularCookieLocaleResolver;

@Profile(PROFILE_CORE)
@Configuration
public class LocaleConfiguration implements WebMvcConfigurer {

    @Bean
    public LocaleResolver localeResolver() {
        return new AngularCookieLocaleResolver("NG_TRANSLATE_LANG_KEY");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        LocaleChangeInterceptor localeChangeInterceptor = new LocaleChangeInterceptor();
        localeChangeInterceptor.setParamName("language");
        registry.addInterceptor(localeChangeInterceptor);
    }
}
