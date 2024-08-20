package de.tum.cit.endpointanalysis;

public record RestCallInformation(String method, String url, String filePath, int line) {

    public String buildCompleteRestCallURI() {
        return this.url.replace("`", "");
    }
    public String buildComparableRestCallUri() {
        // Replace arguments with placeholder
        String result = this.buildCompleteRestCallURI().replaceAll("\\$\\{.*?\\}", ":param:");

        // Remove query parameters
        result = result.split("\\?")[0];
        if (result.startsWith("/")) {
            result = result.substring(1);
        }

        return result;
    }
}
