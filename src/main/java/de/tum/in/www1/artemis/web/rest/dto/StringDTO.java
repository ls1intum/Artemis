package de.tum.in.www1.artemis.web.rest.dto;

// Spring doesn't support sending text/plain, so create a wrapper object
public class StringDTO {

    private String response;

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }

    // Dummy constructor for the tests
    public StringDTO() {}

    public StringDTO(String value) {
        this.response = value;
    }
}
