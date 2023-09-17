package de.tum.in.www1.artemis.web.websocket.jms;

import static org.assertj.core.api.Assertions.assertThat;

import javax.jms.JMSException;

import org.apache.activemq.junit.EmbeddedActiveMQBroker;
import org.junit.Rule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JMSListenerUtilsTest {

    @Rule
    public EmbeddedActiveMQBroker broker = new EmbeddedActiveMQBroker();

    @BeforeEach
    void startBroker() {
        broker.start();
    }

    @AfterEach
    void stopBroker() {
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
