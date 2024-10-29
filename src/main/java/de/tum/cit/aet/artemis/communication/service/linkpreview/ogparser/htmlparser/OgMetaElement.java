package de.tum.cit.aet.artemis.communication.service.linkpreview.ogparser.htmlparser;

/**
 * Represents single og meta element.
 */
public class OgMetaElement {

    private static final String EXTRA_DATA_SEPARATOR = ":";

    private final String property;

    private final String content;

    private final String extraData;

    public OgMetaElement(String property, String content) {
        if (isExtraData(property)) {
            final String[] split = property.split(EXTRA_DATA_SEPARATOR);
            this.property = split[0];
            this.extraData = split[1];
        }
        else {
            this.property = property;
            this.extraData = null;
        }

        this.content = content;
    }

    public String getProperty() {
        return property;
    }

    public String getContent() {
        return content;
    }

    public String getExtraData() {
        return extraData;
    }

    public boolean hasExtraData() {
        return extraData != null;
    }

    private boolean isExtraData(String property) {
        return property.contains(EXTRA_DATA_SEPARATOR);
    }
}
