package de.tum.in.www1.artemis.security;

import org.springframework.security.config.annotation.SecurityConfigurerAdapter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

public class CustomUserConfigurer extends SecurityConfigurerAdapter<DefaultSecurityFilterChain, HttpSecurity> {

    public CustomUserConfigurer() {
    }

    @Override
    public void configure(HttpSecurity http) {
        CustomStudentFilter customStudentFilter = new CustomStudentFilter();
        http.addFilterAfter(customStudentFilter, UsernamePasswordAuthenticationFilter.class);
    }
}
