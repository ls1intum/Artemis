package de.tum.cit.aet.artemis.buildagent.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class BuildAgentCapacityAdjustmentDTOTest {

    @Test
    void testValidConstruction() {
        BuildAgentCapacityAdjustmentDTO dto = new BuildAgentCapacityAdjustmentDTO("test-agent", 5);

        assertThat(dto.getBuildAgentName()).isEqualTo("test-agent");
        assertThat(dto.getNewCapacity()).isEqualTo(5);
    }

    @Test
    void testValidConstructionWithNullAgentName() {
        BuildAgentCapacityAdjustmentDTO dto = new BuildAgentCapacityAdjustmentDTO(null, 3);

        assertThat(dto.getBuildAgentName()).isNull();
        assertThat(dto.getNewCapacity()).isEqualTo(3);
    }

    @Test
    void testInvalidCapacityZero() {
        assertThatThrownBy(() -> new BuildAgentCapacityAdjustmentDTO("test-agent", 0)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("New capacity must be greater than 0");
    }

    @Test
    void testInvalidCapacityNegative() {
        assertThatThrownBy(() -> new BuildAgentCapacityAdjustmentDTO("test-agent", -1)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("New capacity must be greater than 0");
    }

    @Test
    void testValidCapacityOne() {
        BuildAgentCapacityAdjustmentDTO dto = new BuildAgentCapacityAdjustmentDTO("agent1", 1);

        assertThat(dto.getBuildAgentName()).isEqualTo("agent1");
        assertThat(dto.getNewCapacity()).isEqualTo(1);
    }

    @Test
    void testValidCapacityLargeNumber() {
        BuildAgentCapacityAdjustmentDTO dto = new BuildAgentCapacityAdjustmentDTO("agent-large", 1000);

        assertThat(dto.getBuildAgentName()).isEqualTo("agent-large");
        assertThat(dto.getNewCapacity()).isEqualTo(1000);
    }

    @Test
    void testEmptyAgentName() {
        BuildAgentCapacityAdjustmentDTO dto = new BuildAgentCapacityAdjustmentDTO("", 2);

        assertThat(dto.getBuildAgentName()).isEmpty();
        assertThat(dto.getNewCapacity()).isEqualTo(2);
    }
}
