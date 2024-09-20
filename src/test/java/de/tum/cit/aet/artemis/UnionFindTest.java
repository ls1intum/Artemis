package de.tum.cit.aet.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.atlas.service.util.UnionFind;

class UnionFindTest {

    private UnionFind<Integer> unionFind;

    @BeforeEach
    void setUp() {
        List<Integer> elements = Arrays.asList(1, 2, 3, 4, 5);
        unionFind = new UnionFind<>(elements);
    }

    @Test
    void testInitialization() {
        assertThat(unionFind.size()).isEqualTo(5);
        assertThat(unionFind.numberOfSets()).isEqualTo(5);
    }

    @Test
    void testAddElement() {
        unionFind.addElement(6);
        assertThat(unionFind.size()).isEqualTo(6);
        assertThat(unionFind.numberOfSets()).isEqualTo(6);
    }

    @Test
    void testFind() {
        assertThat(unionFind.find(1)).isEqualTo(1);
        assertThat(unionFind.find(2)).isEqualTo(2);
    }

    @Test
    void testUnion() {
        unionFind.union(1, 2);
        assertThat(unionFind.numberOfSets()).isEqualTo(4);
        assertThat(unionFind.inSameSet(1, 2)).isTrue();
    }

    @Test
    void testInSameSet() {
        unionFind.union(1, 2);
        assertThat(unionFind.inSameSet(1, 2)).isTrue();
        assertThat(unionFind.inSameSet(1, 3)).isFalse();
    }

    @Test
    void testNumberOfSets() {
        unionFind.union(1, 2);
        unionFind.union(3, 4);
        assertThat(unionFind.numberOfSets()).isEqualTo(3);
    }

    @Test
    void testSize() {
        assertThat(unionFind.size()).isEqualTo(5);
        unionFind.addElement(6);
        assertThat(unionFind.size()).isEqualTo(6);
    }

    @Test
    void testComplexSequence() {
        unionFind.union(1, 2);
        unionFind.union(3, 4);
        unionFind.union(1, 3);
        assertThat(unionFind.numberOfSets()).isEqualTo(2);
        assertThat(unionFind.inSameSet(1, 2)).isTrue();
        assertThat(unionFind.inSameSet(1, 3)).isTrue();
        assertThat(unionFind.inSameSet(1, 4)).isTrue();
        assertThat(unionFind.inSameSet(2, 3)).isTrue();
        assertThat(unionFind.inSameSet(2, 4)).isTrue();
        assertThat(unionFind.inSameSet(3, 4)).isTrue();
    }
}
