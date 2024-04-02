package de.tum.in.www1.artemis.service.connectors.pyris.dto.status;

import java.util.Objects;

public final class PyrisStageDTO {

    private final String name;

    private final int weight;

    private PyrisStageStateDTO state;

    private final String message;

    public PyrisStageDTO(String name, int weight, PyrisStageStateDTO state, String message) {
        this.name = name;
        this.weight = weight;
        this.state = state;
        this.message = message;
    }

    public String getName() {
        return name;
    }

    public int getWeight() {
        return weight;
    }

    public PyrisStageStateDTO getState() {
        return state;
    }

    public void setState(PyrisStageStateDTO state) {
        this.state = state;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (obj == null || obj.getClass() != this.getClass())
            return false;
        var that = (PyrisStageDTO) obj;
        return Objects.equals(this.name, that.name) && this.weight == that.weight && Objects.equals(this.state, that.state) && Objects.equals(this.message, that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, weight, state, message);
    }

    @Override
    public String toString() {
        return "PyrisStageDTO[" + "name=" + name + ", " + "weight=" + weight + ", " + "state=" + state + ", " + "message=" + message + ']';
    }
}
