package de.tum.cit.aet.artemis.programming.localvcci;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

import de.tum.cit.aet.artemis.programming.domain.build.BuildJob;
import de.tum.cit.aet.artemis.programming.service.localci.SharedQueueManagementService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

class SharedQueueManagementServiceTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    @Autowired
    private SharedQueueManagementService sharedQueueManagementService;

    @Autowired
    @Qualifier("hazelcastInstance")
    private HazelcastInstance hazelcastInstance;

    @Test
    void testPushDockerImageCleanupInfo() {

        IMap<String, ZonedDateTime> dockerImageCleanupInfo = hazelcastInstance.getMap("dockerImageCleanupInfo");
        dockerImageCleanupInfo.clear();

        ZonedDateTime now = ZonedDateTime.now();

        BuildJob b1 = new BuildJob();
        b1.setDockerImage("image1");
        b1.setBuildStartDate(now);
        buildJobRepository.save(b1);

        BuildJob b2 = new BuildJob();
        b2.setDockerImage("image2");
        b2.setBuildStartDate(now.plusMinutes(1));
        buildJobRepository.save(b2);

        BuildJob b3 = new BuildJob();
        b3.setDockerImage("image3");
        b3.setBuildStartDate(now.plusMinutes(2));
        buildJobRepository.save(b3);

        sharedQueueManagementService.pushDockerImageCleanupInfo();

        // Verify that the dockerImageCleanupInfo map contains three entries
        assertThat(dockerImageCleanupInfo.size()).isEqualTo(3);

        // Verify that the dockerImageCleanupInfo map contains the correct entries
        assertThat(dockerImageCleanupInfo.get("image1").getSecond()).isEqualTo(now.getSecond());
        assertThat(dockerImageCleanupInfo.get("image2").getSecond()).isEqualTo(now.plusMinutes(1).getSecond());
        assertThat(dockerImageCleanupInfo.get("image3").getSecond()).isEqualTo(now.plusMinutes(2).getSecond());
    }
}
