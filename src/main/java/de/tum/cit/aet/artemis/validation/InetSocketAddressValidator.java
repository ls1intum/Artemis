package de.tum.cit.aet.artemis.validation;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

public class InetSocketAddressValidator {

    /**
     * Convert a given hostname:port input to an InetSocketAddress, if possible.
     * This works with IPv4, IPv6 and hostnames.
     *
     * @param hostnameAndPort the hostname and port, seperated by ':' (e.g. 'localhost:61613')
     * @return an Optional containing the InetSocketAddress if the conversion was possible, or an empty Optional if the input was invalid
     */
    public static Optional<InetSocketAddress> getValidAddress(String hostnameAndPort) {
        // https://stackoverflow.com/a/2347356/3802758
        try {
            // WORKAROUND: add any scheme to make the resulting URI valid.
            URI uri = new URI("my://" + hostnameAndPort); // may throw URISyntaxException
            String host = uri.getHost();
            int port = uri.getPort();

            if (uri.getHost() == null || uri.getPort() == -1) {
                return Optional.empty();
            }

            // validation succeeded
            return Optional.of(new InetSocketAddress(host, port));

        }
        catch (URISyntaxException ex) {
            return Optional.empty();
        }
    }
}
