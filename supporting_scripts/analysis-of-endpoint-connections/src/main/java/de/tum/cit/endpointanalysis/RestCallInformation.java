package de.tum.cit.endpointanalysis;

public record RestCallInformation(String method, String url, int line, String fileName) {

    public String buildCompleteRestCallURI() {
        return this.url.replace("`", "");
    }

    public String buildComparableRestCallUri() {
        // Replace arguments with placeholder
        String result = this.buildCompleteRestCallURI().replaceAll("\\$\\{.*?\\}", ":param:");

        // Remove query parameters
        result = result.split("\\?")[0];

        return result;
    }
}
