package de.tum.in.www1.artemis.exercise.quiz;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.domain.TempIdObject;
import de.tum.in.www1.artemis.service.quiz.QuizIdAssigner;

public class QuizIdAssignerTest {

    static class TestTempIdObject extends TempIdObject {

        public TestTempIdObject(Long id) {
            this.setId(id);
        }
    }

    @Test
    public void testAssignIds_EmptyCollection() {
        Collection<TestTempIdObject> items = new ArrayList<>();
        QuizIdAssigner.assignIds(items);
        assertThat(items).isEmpty();
    }

    @Test
    public void testAssignIds_AllItemsHaveIds() {
        Collection<TestTempIdObject> items = List.of(new TestTempIdObject(1L), new TestTempIdObject(2L));
        QuizIdAssigner.assignIds(items);
        for (TestTempIdObject item : items) {
            assertThat(item.getId()).isNotNull();
        }
    }

    @Test
    public void testAssignIds_SomeItemsHaveIds() {
        Collection<TestTempIdObject> items = new ArrayList<>();
        items.add(new TestTempIdObject(1L));
        items.add(new TestTempIdObject(null));
        items.add(new TestTempIdObject(3L));
        items.add(new TestTempIdObject(null));

        QuizIdAssigner.assignIds(items);

        List<Long> expectedIds = List.of(1L, 4L, 3L, 5L);
        List<Long> actualIds = new ArrayList<>();
        for (TestTempIdObject item : items) {
            actualIds.add(item.getId());
        }
        assertThat(expectedIds).isEqualTo(actualIds);
    }

    @Test
    public void testAssignIds_NoItemsHaveIds() {
        Collection<TestTempIdObject> items = new ArrayList<>();
        items.add(new TestTempIdObject(null));
        items.add(new TestTempIdObject(null));
        items.add(new TestTempIdObject(null));

        QuizIdAssigner.assignIds(items);

        List<Long> expectedIds = List.of(1L, 2L, 3L);
        List<Long> actualIds = new ArrayList<>();
        for (TestTempIdObject item : items) {
            actualIds.add(item.getId());
        }
        assertThat(expectedIds).isEqualTo(actualIds);
    }
}
