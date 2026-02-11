package de.tum.cit.aet.artemis.core.config.valves;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;

import org.apache.catalina.Valve;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.junit.jupiter.api.Test;

class PublicTimeValveTest {

    @Test
    void invokeShouldHandleTimePathOnGetRequest() throws Exception {
        var request = mock(Request.class);
        when(request.getRequestURI()).thenReturn("/time");
        when(request.getMethod()).thenReturn("GET");

        var response = mock(Response.class);
        var responseBody = new StringWriter();
        var printWriter = new PrintWriter(responseBody);
        when(response.getWriter()).thenReturn(printWriter);

        var nextValve = mock(Valve.class);
        var valve = new PublicTimeValve();
        valve.setNext(nextValve);

        valve.invoke(request, response);
        printWriter.flush();

        verify(response).setStatus(200);
        verify(response).setContentType("text/plain");

        var body = responseBody.toString();
        assertThat(Instant.parse(body)).isNotNull();
        verifyNoInteractions(nextValve);
    }

    @Test
    void invokeShouldDelegateTimePathOnHeadRequest() throws Exception {
        var request = mock(Request.class);
        when(request.getRequestURI()).thenReturn("/time");
        when(request.getMethod()).thenReturn("HEAD");

        var response = mock(Response.class);
        var nextValve = mock(Valve.class);
        var valve = new PublicTimeValve();
        valve.setNext(nextValve);

        valve.invoke(request, response);

        verify(nextValve).invoke(request, response);
    }

    @Test
    void invokeShouldDelegateRequestForDifferentPath() throws Exception {
        var request = mock(Request.class);
        when(request.getRequestURI()).thenReturn("/api/core/public/profile-info");
        when(request.getMethod()).thenReturn("GET");

        var response = mock(Response.class);
        var nextValve = mock(Valve.class);
        var valve = new PublicTimeValve();
        valve.setNext(nextValve);

        valve.invoke(request, response);

        verify(nextValve).invoke(request, response);
    }

    @Test
    void invokeShouldDelegateRequestForUnsupportedMethod() throws Exception {
        var request = mock(Request.class);
        when(request.getRequestURI()).thenReturn("/time");
        when(request.getMethod()).thenReturn("POST");

        var response = mock(Response.class);
        var nextValve = mock(Valve.class);
        var valve = new PublicTimeValve();
        valve.setNext(nextValve);

        valve.invoke(request, response);

        verify(nextValve).invoke(request, response);
    }
}
