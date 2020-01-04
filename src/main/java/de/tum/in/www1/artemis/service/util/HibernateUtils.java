package de.tum.in.www1.artemis.service.util;

import org.hibernate.Hibernate;

public class HibernateUtils {

    /**
     * Unproxies a {@link org.hibernate.proxy.HibernateProxy} and casts the result to the implied type.
     * If you don't want to cast the object at all, just use {@link Hibernate#unproxy(Object)}
     *
     * @param proxy The object that should get unproxied
     * @param <T> The type of the object after unproxying it
     * @return the proxy's underlying implementation object
     * @see Hibernate#unproxy(Object)
     */
    @SuppressWarnings("unchecked")
    public static <T> T unproxy(Object proxy) {
        // Note: This unchecked cast is fine in this case, since the caller would have to force cast the unproxied object anyway.
        // It doesn't really matter if we throw the exception here, or in the calling block if the cast fails, the result is the same.
        return (T) Hibernate.unproxy(proxy);
    }
}
