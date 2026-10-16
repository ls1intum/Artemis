package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_AIWORKER;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;

import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.filter.TypeFilter;

/** Limits a worker-only Artemis process to the worker module; workload auto-configuration installs its own components. */
public class AiWorkerComponentScanFilter implements TypeFilter, EnvironmentAware {

    private boolean workerOnly;

    @Override
    public void setEnvironment(Environment environment) {
        workerOnly = environment.acceptsProfiles(Profiles.of(PROFILE_AIWORKER)) && !environment.acceptsProfiles(Profiles.of(PROFILE_CORE));
    }

    @Override
    public boolean match(MetadataReader metadataReader, MetadataReaderFactory metadataReaderFactory) throws IOException {
        return workerOnly && !metadataReader.getClassMetadata().getClassName().startsWith("de.tum.cit.aet.artemis.aiworker.");
    }
}
