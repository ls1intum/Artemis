package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.resource.ResourceTransformer;
import org.springframework.web.servlet.resource.ResourceTransformerChain;
import org.springframework.web.servlet.resource.TransformedResource;

@Profile(PROFILE_CORE)
public class IndexHtmlTransformer implements ResourceTransformer {

    private final Environment env;

    public IndexHtmlTransformer(Environment env) {
        this.env = env;
    }

    @Override
    public Resource transform(HttpServletRequest req, Resource resource, ResourceTransformerChain chain) throws IOException {
        Resource res = chain.transform(req, resource);
        if (!"index.html".equals(res.getFilename())) {
            return res;
        }
        String content = new String(res.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String joined = String.join(",", env.getActiveProfiles());

        // meta-tag injection
        content = content.replaceAll("(<meta\\s+name=[\"']active-features[\"']\\s+content=\")[^\"]*(\"\\s*/?>)", "$1" + joined + "$2");

        return new TransformedResource(res, content.getBytes(StandardCharsets.UTF_8));
    }
}
