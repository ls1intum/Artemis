package de.tum.cit.endpointanalysis;

public class RestCallInformation {

    private String method;

    private String url;

    private int line;

    private String fileName;

    public RestCallInformation() {
    }

    public RestCallInformation(String method, String url, int line, String fileName) {
        this.method = method;
        this.url = url;
        this.line = line;
        this.fileName = fileName;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public int getLine() {
        return line;
    }

    public void setLine(int line) {
        this.line = line;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String buildCompleteRestCallURI() {
        String result = this.url.replace("`", "");
        return result;
    }

    public String buildComparableRestCallUri() {
        // Replace arguments with placeholder
        String result = this.buildCompleteRestCallURI().replaceAll("\\$\\{.*?\\}", ":param:");
        if (result.contains("?")) {
            result = result.substring(0, result.indexOf("?"));
        }
        return result;
    }
}
