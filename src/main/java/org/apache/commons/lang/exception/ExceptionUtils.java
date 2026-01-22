package org.apache.commons.lang.exception;

// TODO: this is a workaround for Eureka Client to avoid the commons lang dependency which has security issues.
// Remove this class when we upgrade to a later Eureka Client which uses Commons Lang 3.x or when we switch to another service discovery mechanism.
@Deprecated(forRemoval = true)
public class ExceptionUtils extends org.apache.commons.lang3.exception.ExceptionUtils {
}
