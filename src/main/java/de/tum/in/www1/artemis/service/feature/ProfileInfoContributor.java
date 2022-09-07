package de.tum.in.www1.artemis.service.feature;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

import com.hazelcast.core.HazelcastInstance;

@Component
public class ProfileInfoContributor implements InfoContributor {

    private final HazelcastInstance hazelcastInstance;

    public ProfileInfoContributor(HazelcastInstance hazelcastInstance) {
        this.hazelcastInstance = hazelcastInstance;
    }

    @Override
    public void contribute(Info.Builder builder) {
        Set<String> profiles = hazelcastInstance.getCluster().getMembers().stream().flatMap(member -> Stream.of(member.getAttributes().getOrDefault("profiles", "").split(",")))
                .collect(Collectors.toSet());

        builder.withDetail("combinedProfiles", profiles);
    }
}
