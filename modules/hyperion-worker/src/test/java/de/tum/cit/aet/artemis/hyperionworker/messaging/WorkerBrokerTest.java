package de.tum.cit.aet.artemis.hyperionworker.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.UUID;

import jakarta.jms.JMSContext;
import jakarta.jms.JMSSecurityRuntimeException;

import org.apache.activemq.artemis.core.config.FileDeploymentManager;
import org.apache.activemq.artemis.core.config.impl.FileConfiguration;
import org.apache.activemq.artemis.core.config.impl.SecurityConfiguration;
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.spi.core.security.ActiveMQSecurityManagerImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkerBrokerTest {

    private static final String COMMANDS = "hyperion.worker.worker-1.commands";

    private static final String EVENTS = "hyperion.worker.worker-1.events";

    @TempDir
    Path directory;

    private ActiveMQServerImpl broker;

    private ActiveMQConnectionFactory connections;

    private final String password = UUID.randomUUID().toString();

    @BeforeEach
    void startBrokerWithProductionPermissions() throws Exception {
        FileConfiguration config = new FileConfiguration();
        new FileDeploymentManager("hyperion-broker.xml").addDeployable(config).readConfiguration();
        // Only the transport and storage location differ; permissions, queues and delivery policy come from the deployment file.
        config.getAcceptorConfigurations().clear();
        config.addAcceptorConfiguration("test", "vm://0");
        config.setJournalDirectory(directory.resolve("journal").toString());
        config.setBindingsDirectory(directory.resolve("bindings").toString());
        config.setPagingDirectory(directory.resolve("paging").toString());
        config.setLargeMessagesDirectory(directory.resolve("large").toString());
        config.setJMXManagementEnabled(false);
        SecurityConfiguration users = new SecurityConfiguration();
        users.addUser("core", password);
        users.addRole("core", "hyperion-core");
        users.addUser("worker", password);
        users.addRole("worker", "hyperion-worker-1");
        users.addUser("other-worker", password);
        users.addRole("other-worker", "hyperion-worker-2");
        broker = new ActiveMQServerImpl(config, new ActiveMQSecurityManagerImpl(users));
        broker.start();
        connections = new ActiveMQConnectionFactory("vm://0");
        connections.setBlockOnDurableSend(true);
        connections.setBlockOnNonDurableSend(true);
        connections.setCallTimeout(5_000);
    }

    @AfterEach
    void stopBroker() throws Exception {
        if (connections != null) {
            connections.close();
        }
        if (broker != null) {
            broker.stop();
        }
    }

    @Test
    void credentialsAllowOnlyTheTwoIntendedDirections() throws Exception {
        broker.createQueue(org.apache.activemq.artemis.api.core.QueueConfiguration.of("artemis.production").setAutoCreateAddress(true)
                .setRoutingType(org.apache.activemq.artemis.api.core.RoutingType.ANYCAST));
        try (JMSContext core = connections.createContext("core", password);
                JMSContext worker = connections.createContext("worker", password);
                JMSContext other = connections.createContext("other-worker", password)) {
            core.createProducer().send(core.createQueue(COMMANDS), "start");
            assertThat(worker.createConsumer(worker.createQueue(COMMANDS)).receiveBody(String.class, 5_000)).isEqualTo("start");
            worker.createProducer().send(worker.createQueue(EVENTS), "finished");
            assertThat(core.createConsumer(core.createQueue(EVENTS)).receiveBody(String.class, 5_000)).isEqualTo("finished");
            assertThatThrownBy(() -> worker.createProducer().send(worker.createQueue(COMMANDS), "forged start")).isInstanceOf(JMSSecurityRuntimeException.class);
            assertThatThrownBy(() -> worker.createConsumer(worker.createQueue(EVENTS))).isInstanceOf(JMSSecurityRuntimeException.class);
            assertThatThrownBy(() -> core.createProducer().send(core.createQueue(EVENTS), "forged result")).isInstanceOf(JMSSecurityRuntimeException.class);
            assertThatThrownBy(() -> other.createConsumer(other.createQueue(COMMANDS))).isInstanceOf(JMSSecurityRuntimeException.class);
            assertThatThrownBy(() -> other.createProducer().send(other.createQueue(EVENTS), "forged result")).isInstanceOf(JMSSecurityRuntimeException.class);
            assertThatThrownBy(() -> worker.createProducer().send(worker.createQueue("artemis.production"), "escape")).isInstanceOf(JMSSecurityRuntimeException.class);
        }
    }

    @Test
    void persistentEventSurvivesBrokerRestart() throws Exception {
        try (JMSContext worker = connections.createContext("worker", password)) {
            worker.createProducer().setDeliveryMode(jakarta.jms.DeliveryMode.PERSISTENT).send(worker.createQueue(EVENTS), "checkpoint");
        }
        connections.close();
        broker.stop();
        broker = new ActiveMQServerImpl(broker.getConfiguration(), broker.getSecurityManager());
        broker.start();
        connections = new ActiveMQConnectionFactory("vm://0");
        try (JMSContext core = connections.createContext("core", password)) {
            assertThat(core.createConsumer(core.createQueue(EVENTS)).receiveBody(String.class, 5_000)).isEqualTo("checkpoint");
        }
    }

    @Test
    void rolledBackCommandIsRedeliveredAndEventuallyDeadLettered() {
        try (JMSContext core = connections.createContext("core", password); JMSContext worker = connections.createContext("worker", password, JMSContext.SESSION_TRANSACTED)) {
            core.createProducer().send(core.createQueue(COMMANDS), "invalid command");
            var consumer = worker.createConsumer(worker.createQueue(COMMANDS));
            for (int attempt = 1; attempt <= 5; attempt++) {
                var message = consumer.receive(5_000);
                assertThat(message).isNotNull();
                try {
                    assertThat(message.getIntProperty("JMSXDeliveryCount")).isEqualTo(attempt);
                }
                catch (jakarta.jms.JMSException e) {
                    throw new AssertionError(e);
                }
                worker.rollback();
            }
            assertThat(core.createConsumer(core.createQueue("hyperion.dead-letter")).receiveBody(String.class, 5_000)).isEqualTo("invalid command");
            assertThat(consumer.receiveNoWait()).isNull();
        }
    }
}
