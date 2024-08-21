package de.tum.cit.endpointanalysis;

public record RestCallInformation(String method, String url, String filePath, int line) {

    String buildCompleteRestCallURI() {
        return this.url.replace("`", "");
    }

    String buildComparableRestCallUri() {
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
