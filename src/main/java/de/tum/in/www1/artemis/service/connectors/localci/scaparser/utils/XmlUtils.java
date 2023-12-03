package de.tum.in.www1.artemis.service.connectors.localci.scaparser.utils;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Optional;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public final class XmlUtils {

    private XmlUtils() {
    }

    /**
     * Creates a new document builder.
     *
     * @return the DocumentBuilder
     * @throws ParserConfigurationException if secure processing feature cannot be set
     */
    public static DocumentBuilder createDocumentBuilder() throws ParserConfigurationException {
        final DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
        domFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        domFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        domFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        domFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        domFactory.setXIncludeAware(false);
        domFactory.setExpandEntityReferences(false);
        return domFactory.newDocumentBuilder();
    }

    /**
     * Gets the first child with the given name of the given parent or Optional.empty if no such child exists
     *
     * @param parent parent to search
     * @param name   name of the child
     * @return an optional containing the first child or empty if none exists
     */
    public static Optional<Element> getFirstChild(Element parent, String name) {
        Iterator<Element> iterator = getChildElements(parent, name).iterator();
        if (iterator.hasNext()) {
            return Optional.of(iterator.next());
        }
        else {
            return Optional.empty();
        }
    }

    /**
     * Gets all child elements of the given parent
     *
     * @param parent of the elements
     * @return a list of all children with any name in the empty namespace
     */
    public static Iterable<Element> getChildElements(Element parent) {
        return getChildElements(parent, null);
    }

    /**
     * Gets all child elements of the given parent with the given properties
     *
     * @param parent of the elements
     * @param name   name of the tag of the elements
     * @return a list of all children with the name in the empty namespace
     */
    public static Iterable<Element> getChildElements(Element parent, String name) {
        return () -> new Iterator<>() {

            final NodeList children = parent.getElementsByTagName(name != null ? name : "*");

            int index = skipIndirectChildren();

            @Override
            public boolean hasNext() {
                return index < children.getLength();
            }

            @Override
            public Element next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }

                Element result = (Element) children.item(index++);
                skipIndirectChildren();

                return result;
            }

            private int skipIndirectChildren() {
                while (hasNext() && !children.item(index).getParentNode().isEqualNode(parent)) {
                    index++;
                }

                // a bit hacky solution to allow to execute the method on initialization
                return index;
            }
        };
    }
}
