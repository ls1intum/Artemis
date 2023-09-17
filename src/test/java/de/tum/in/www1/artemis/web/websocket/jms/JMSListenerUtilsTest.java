package de.tum.in.www1.artemis.web.websocket.jms;

import static org.assertj.core.api.Assertions.assertThat;

import javax.jms.JMSException;

import org.apache.activemq.junit.EmbeddedActiveMQBroker;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class JMSListenerUtilsTest {

    static EmbeddedActiveMQBroker broker;

    @BeforeAll
    static void startBroker() {
        broker = new EmbeddedActiveMQBroker();
        broker.start();
    }

    @AfterAll
    static void stopBroker() {
        broker.stop();
    }

    @Test
    void extractUsernameFromMessageReturnsCorrectUsername() throws JMSException {

        var message = broker.createBytesMessage();
        message.setStringProperty("user-name", "user1");

        assertThat(JMSListenerUtils.extractUsernameFromMessage(message)).isEqualTo("user1");
    }

    @Test
    void extractUsernameFromMessageDoesNotFailIfUsernameIsMissing() throws JMSException {
        var message = broker.createBytesMessage();

        assertThat(JMSListenerUtils.extractUsernameFromMessage(message)).isNull();
    }

}
