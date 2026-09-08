package de.tum.cit.aet.artemis.core.util;

import javax.xml.XMLConstants;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;

import org.jspecify.annotations.NonNull;

/**
 * Factories for the JAXP components that are unsafe by default.
 *
 * <p>
 * A {@link TransformerFactory} obtained from {@code newInstance()} resolves external DTDs and external stylesheets, so
 * a document that Artemis writes out can be made to read a local file or reach a network address chosen by whoever
 * supplied the document. Both are switched off here, and secure processing is enabled so that the limits on entity
 * expansion apply as well.
 *
 * <p>
 * This lives in {@code core} rather than next to any one caller because the correct set of properties is not obvious
 * from the API, and getting it wrong is silent: a factory missing one of them still transforms every well-formed
 * document without complaint. Reach for this instead of {@code TransformerFactory.newInstance()}.
 */
public final class SecureXmlFactory {

    private SecureXmlFactory() {
        // static utility class
    }

    /**
     * Returns a {@link TransformerFactory} that neither resolves external DTDs nor loads external stylesheets.
     *
     * @return a transformer factory configured for secure processing
     */
    @NonNull
    public static TransformerFactory transformerFactory() {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        setFeatureIfSupported(transformerFactory);
        // An empty string is the documented way to forbid every protocol, rather than allowing a subset.
        transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        return transformerFactory;
    }

    /**
     * Returns a {@link Transformer} from a factory configured by {@link #transformerFactory()}.
     *
     * @return a transformer that will not resolve external entities
     * @throws TransformerConfigurationException if the transformer cannot be created
     */
    @NonNull
    public static Transformer transformer() throws TransformerConfigurationException {
        return transformerFactory().newTransformer();
    }

    /**
     * Secure processing is optional for a {@link TransformerFactory} implementation. Every implementation Artemis
     * ships with supports it; the guard exists so that a factory supplied by a different classpath cannot stop the
     * export from working, given the two access properties below already close the external-entity paths.
     */
    private static void setFeatureIfSupported(TransformerFactory transformerFactory) {
        try {
            transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        }
        catch (TransformerConfigurationException e) {
            throw new IllegalStateException("The XML transformer factory does not support secure processing", e);
        }
    }
}
