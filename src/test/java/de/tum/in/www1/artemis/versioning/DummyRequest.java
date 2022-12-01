package de.tum.in.www1.artemis.versioning;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

public class DummyRequest extends HttpServletRequestWrapper {

    private static final HttpServletRequest UNSUPPORTED_REQUEST = (HttpServletRequest) Proxy.newProxyInstance(DummyRequest.class.getClassLoader(),
            new Class[] { HttpServletRequest.class }, new UnsupportedOperationExceptionInvocationHandler());

    private String requestURI;

    public DummyRequest() {
        super(UNSUPPORTED_REQUEST);
    }

    @Override
    public String getRequestURI() {
        return this.requestURI;
    }

    public void setRequestURI(String requestURI) {
        this.requestURI = requestURI;
    }

    static final class UnsupportedOperationExceptionInvocationHandler implements InvocationHandler {

        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            throw new UnsupportedOperationException(method + " is not supported");
        }
    }
}
