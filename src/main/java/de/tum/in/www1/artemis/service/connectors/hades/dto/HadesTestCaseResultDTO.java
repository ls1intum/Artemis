package de.tum.in.www1.artemis.service.connectors.hades.dto;

import java.util.List;

import de.tum.in.www1.artemis.service.dto.TestCaseDTOInterface;

public class HadesTestCaseResultDTO implements TestCaseDTOInterface {

    private String name;

    private List<String> message;

    private final boolean successful;

    public HadesTestCaseResultDTO(String name, List<String> message, boolean successful) {
        this.name = name;
        this.message = message;
        this.successful = successful;
    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public List<String> getMessage() {
        return null;
    }

    public boolean isSuccessful() {
        return successful;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setMessage(List<String> message) {
        this.message = message;
    }
}
