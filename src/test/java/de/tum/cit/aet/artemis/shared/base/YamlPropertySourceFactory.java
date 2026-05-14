package de.tum.cit.aet.artemis.shared.base;

import java.io.IOException;
import java.util.Properties;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.core.io.support.PropertySourceFactory;

/**
 * {@link PropertySourceFactory} that allows loading {@code .yml} / {@code .yaml} files via
 * {@link org.springframework.test.context.TestPropertySource @TestPropertySource(locations = ...)}.
 * Spring's default {@code DefaultPropertySourceFactory} only understands properties / XML; without this factory,
 * a {@code locations = "classpath:application-saml2.yml"} silently loads no properties.
 */
public class YamlPropertySourceFactory implements PropertySourceFactory {

    @Override
    @NonNull
    public PropertySource<?> createPropertySource(@Nullable String name, @NonNull EncodedResource resource) throws IOException {
        String sourceName = name != null ? name : resource.getResource().getFilename();
        var loaded = new YamlPropertySourceLoader().load(sourceName, resource.getResource());
        if (loaded.isEmpty()) {
            return new PropertiesPropertySource(sourceName != null ? sourceName : "yaml", new Properties());
        }
        return loaded.getFirst();
    }
}
