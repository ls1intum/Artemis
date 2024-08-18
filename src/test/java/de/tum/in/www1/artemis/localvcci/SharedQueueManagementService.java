package de.tum.in.www1.artemis.localvcci;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationLocalCILocalVCTest;
import de.tum.in.www1.artemis.domain.BuildJob;
import de.tum.in.www1.artemis.repository.BuildJobRepository;
import de.tum.in.www1.artemis.service.connectors.localci.SharedQueueManagementService;

class SharedQueueManagementServiceTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    @Autowired
    private SharedQueueManagementService sharedQueueManagementService;

    @Autowired
    private BuildJobRepository buildJobRepository;

    @Autowired
    private RedissonClient redissonClient;

    @BeforeEach
    void clearBuildJobs() {
        buildJobRepository.deleteAll();
    }

    @Test
    void testPushDockerImageCleanupInfo() {

        Map<String, ZonedDateTime> dockerImageCleanupInfo = redissonClient.getMap("dockerImageCleanupInfo");
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
