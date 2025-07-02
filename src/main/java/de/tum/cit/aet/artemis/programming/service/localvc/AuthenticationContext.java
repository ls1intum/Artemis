package de.tum.cit.aet.artemis.programming.service.localvc;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.sshd.server.session.ServerSession;

public sealed interface AuthenticationContext {

    record Session(ServerSession session) implements AuthenticationContext {

        @Override
        public String getIpAddress() {
            return session.getClientAddress().toString();
        }
    }

    record Request(HttpServletRequest request) implements AuthenticationContext {

        @Override
        public String getIpAddress() {
            return request.getRemoteAddr();
        }
    }

    String getIpAddress();
}
