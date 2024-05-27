package analysisOfEndpointConnections;

public class RestCallInformation {
    private String method;
    private String url;
    private int line;
    private String filePath;

    public RestCallInformation() {
    }

    public RestCallInformation(String method, String url, int line, String filePath) {
        this.method = method;
        this.url = url;
        this.line = line;
        this.filePath = filePath;
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

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String buildCompleteRestCallURI() {
        String result = this.url.replace("`", "");
        return result;
    }

    public String buildComparableRestCallUri() {
        // Replace arguments with placeholder
        return this.buildCompleteRestCallURI().replaceAll("\\$\\{.*?\\}", ":param:");
    }
}
