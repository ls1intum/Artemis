package de.tum.cit.aet.artemis.core.config.valves;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.apache.catalina.Valve;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.junit.jupiter.api.Test;

class HealthCheckValveTest {

    @Test
    void invokeShouldReturnPongForPingPath() throws Exception {
        var request = mock(Request.class);
        when(request.getRequestURI()).thenReturn("/ping");

        var response = mock(Response.class);
        var responseBody = new StringWriter();
        var printWriter = new PrintWriter(responseBody);
        when(response.getWriter()).thenReturn(printWriter);

        var nextValve = mock(Valve.class);
        var valve = new HealthCheckValve();
        valve.setNext(nextValve);

        valve.invoke(request, response);
        printWriter.flush();

        verify(response).setStatus(200);
        verify(response).setContentType("text/plain;charset=UTF-8");
        assertThat(responseBody.toString()).isEqualTo("pong");
        verifyNoInteractions(nextValve);
    }

    @Test
    void invokeShouldDelegateRequestForDifferentPath() throws Exception {
        var request = mock(Request.class);
        when(request.getRequestURI()).thenReturn("/api/core/public/profile-info");

        var response = mock(Response.class);
        var nextValve = mock(Valve.class);
        var valve = new HealthCheckValve();
        valve.setNext(nextValve);

        valve.invoke(request, response);

        verify(nextValve).invoke(request, response);
    }
}
