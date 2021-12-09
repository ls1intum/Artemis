package de.tum.in.www1.artemis.migration;

import java.security.Permission;

/**
 * A way to catch when the system exits in tests
 * Source: https://stackoverflow.com/questions/309396/java-how-to-test-methods-that-call-system-exit
 */
public class NoExitSecurityManager extends SecurityManager {

    @Override
    public void checkPermission(Permission perm) {
        // Allow everything
    }

    @Override
    public void checkPermission(Permission perm, Object context) {
        // Allow everything
    }

    @Override
    public void checkExit(int status) {
        throw new ExitException(status);
    }
}
