package de.tum.in.www1.artemis.config;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class ApiVersionFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(ApiVersionFilter.class);

    public static final String CONTENT_VERSION_HEADER = "Content-Version";

    @Value("${artemis.version}")
    private String VERSION;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        final HttpServletRequest httpRequest = (HttpServletRequest) request;
        final HttpServletResponse httpResponse = (HttpServletResponse) response;

        logger.debug("Adding Version to Response to {}", httpRequest.getRequestURI());

        httpResponse.addHeader(CONTENT_VERSION_HEADER, VERSION);

        chain.doFilter(httpRequest, httpResponse);
    }

    @Bean
    public FilterRegistrationBean<ApiVersionFilter> registerFilter() {
        logger.debug("Register API Version Filter.");

        final FilterRegistrationBean<ApiVersionFilter> bean = new FilterRegistrationBean<>();

        bean.setFilter(new ApiVersionFilter());
        bean.addUrlPatterns("/api/**");
        bean.addUrlPatterns("/management/**");

        return bean;
    }
}
