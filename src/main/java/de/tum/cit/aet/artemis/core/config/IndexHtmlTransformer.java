package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.ACTIVE_MODULE_FEATURES;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
        if (!"index.html".equals(res.getFilename())) {
            return res;
        }
        String content = new String(res.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String[] profiles = env.getActiveProfiles();
        Map<String, Object> info = infoEndpoint.info();
        Object featuresObj = info.get(ACTIVE_MODULE_FEATURES);
        List<String> moduleFeatures = (featuresObj instanceof Collection) ? ((Collection<String>) featuresObj).stream().toList() : List.of();

        // 4) Combine both lists into a single comma-separated string
        String joined = Stream.concat(Arrays.stream(profiles), moduleFeatures.stream()).collect(Collectors.joining(","));

        // meta-tag injection
        // match <meta … name="active-features" … content[=…]? …>
        // $1 = everything up through “content”
        // optionally = "…" or = '…' (or nothing)
        // $2 = the rest of the tag up to '>'
        String regex = "(<meta\\s+[^>]*?\\bname=[\"']active-features[\"'][^>]*?\\bcontent)" + "(?:\\s*=\\s*(?:\"[^\"]*\"|'[^']*'))?" + "([^>]*>)";

        content = content.replaceAll(regex, "$1=\"" + joined + "\"$2");

        return new TransformedResource(res, content.getBytes(StandardCharsets.UTF_8));
    }
}
