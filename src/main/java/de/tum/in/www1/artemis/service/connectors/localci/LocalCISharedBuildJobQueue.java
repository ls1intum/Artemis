package de.tum.in.www1.artemis.service.connectors.localci;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.hazelcast.collection.IQueue;
import com.hazelcast.collection.ItemEvent;
import com.hazelcast.collection.ItemListener;
import com.hazelcast.core.HazelcastInstance;

@Service
@Profile("localci")
public class LocalCISharedBuildJobQueue {

    private final Logger log = LoggerFactory.getLogger(LocalCIService.class);

    private final HazelcastInstance hazelcastInstance;

    private final IQueue<String> queue;

    @Autowired
    public LocalCISharedBuildJobQueue(HazelcastInstance hazelcastInstance) {
        this.hazelcastInstance = hazelcastInstance;
        this.queue = this.hazelcastInstance.getQueue("buildJobQueue");
        this.queue.addItemListener(new BuildJobItemListener(), true);
    }

    public void addBuildJob(String buildJob) {
        queue.add(buildJob);
    }

    public void processBuildJob() {
        try {
            String buildJob = queue.take();
            log.info("Hazelcast, processing build job: " + buildJob);
        }
        catch (InterruptedException e) {
            log.error("Error while testing Hazelcast Queue: " + e.getMessage());
        }
    }

    private class BuildJobItemListener implements ItemListener<String> {

        @Override
        public void itemAdded(ItemEvent<String> item) {
            log.info("Hazelcast, item added: " + item.getItem());
        }

        @Override
        public void itemRemoved(ItemEvent<String> item) {
            log.info("Hazelcast, item removed: " + item.getItem());
        }
    }
}
