package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.ACTIVE_MODULE_FEATURES;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.boot.actuate.info.InfoEndpoint;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.resource.ResourceTransformer;
import org.springframework.web.servlet.resource.ResourceTransformerChain;
import org.springframework.web.servlet.resource.TransformedResource;

@Profile(PROFILE_CORE)
public class IndexHtmlTransformer implements ResourceTransformer {

    private final Environment env;

    private final InfoEndpoint infoEndpoint;

    public IndexHtmlTransformer(Environment env, InfoEndpoint infoEndpoint) {
        this.env = env;
        this.infoEndpoint = infoEndpoint;
    }

    @Override
    public Resource transform(HttpServletRequest req, Resource resource, ResourceTransformerChain chain) throws IOException {
        Resource res = chain.transform(req, resource);

        // Only process index.html
        if (!"index.html".equals(res.getFilename())) {
            return res;
        }

        String content = new String(res.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        String[] profiles = env.getActiveProfiles();
        String joinedProfiles = String.join(",", profiles);

        Map<String, Object> info = infoEndpoint.info();
        Object featuresObj = info.get(ACTIVE_MODULE_FEATURES);
        List<String> moduleFeatures = (featuresObj instanceof Collection) ? ((Collection<String>) featuresObj).stream().toList() : List.of();
        String joinedFeatures = String.join(",", moduleFeatures);

        // Pattern breakdown:
        // (1) match the start of a meta tag with name="active-profiles" up to 'content'
        // (2) optionally match an existing content="…"
        // (3) capture the rest of the tag to '>'
        String profilesRegex = "(<meta\\s+[^>]*\\bname=[\"']active-profiles[\"'][^>]*\\bcontent)" + "(?:\\s*=\\s*(?:\"[^\"]*\"|'[^']*'))?" + "([^>]*>)";

        content = content.replaceAll(profilesRegex, "$1=\"" + joinedProfiles + "\"$2");

        // same as above, but for active-module-features
        String featuresRegex = "(<meta\\s+[^>]*\\bname=[\"']active-module-features[\"'][^>]*\\bcontent)" + "(?:\\s*=\\s*(?:\"[^\"]*\"|'[^']*'))?" + "([^>]*>)";

        content = content.replaceAll(featuresRegex, "$1=\"" + joinedFeatures + "\"$2");

        return new TransformedResource(res, content.getBytes(StandardCharsets.UTF_8));
    }
}
