package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class EvidenceSourceTest {

    @Test
    void tableHeadersAndSeparatorsAreNotSubstantiveEvidence() {
        EvidenceSource evidence = EvidenceSource.from("E", """
                ## Design
                | Type | Role | Template status |
                | :--- | ---: | :---: |
                | Parser | learner-owned policy | student-creates |
                The parser remains student-owned.
                This prose remains evidence even before a malformed separator.
                | --- | --- |
                | Only column |
                | --- |
                """);

        assertThat(evidence.containsSubstantive(List.of("E1"))).isFalse();
        assertThat(evidence.containsSubstantive(List.of("E2"))).isFalse();
        assertThat(evidence.containsSubstantive(List.of("E3"))).isFalse();
        assertThat(evidence.containsSubstantive(List.of("E4"))).isTrue();
        assertThat(evidence.containsSubstantive(List.of("E5"))).isTrue();
        assertThat(evidence.containsSubstantive(List.of("E6"))).isTrue();
        assertThat(evidence.containsSubstantive(List.of("E7"))).isFalse();
        assertThat(evidence.containsSubstantive(List.of("E8"))).isFalse();
        assertThat(evidence.containsSubstantive(List.of("E9"))).isFalse();
        assertThat(evidence.containsSubstantive(List.of("E2", "E4"))).isFalse();
        assertThat(evidence.containsAllWithSubstantive(List.of("E1", "E4"))).isTrue();
        assertThat(evidence.containsAllWithSubstantive(List.of("E1", "E2"))).isFalse();
        assertThat(evidence.containsAllWithSubstantive(List.of("E1", "missing"))).isFalse();
    }

    @Test
    void headingCandidatesContainOnlyProseAndTableDataRows() {
        EvidenceSource evidence = EvidenceSource.from("E", """
                ## Testing Strategy
                | ID | Owner type | Observable responsibility |
                |---|---|---|
                | S1 | Parser | parses every token |
                The suite also checks the public collaboration.
                ## Decision Ledger
                | Decision | Reason |
                |---|---|
                """);

        assertThat(evidence.idsUnderHeading("## Testing Strategy")).containsExactly("E4", "E5");
    }
}
