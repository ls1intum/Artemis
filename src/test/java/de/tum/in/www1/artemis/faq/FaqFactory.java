package de.tum.in.www1.artemis.faq;

import java.util.HashSet;
import java.util.Set;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Faq;
import de.tum.in.www1.artemis.domain.FaqState;

public class FaqFactory {

    public static Faq generateFaq(Long id, Course course) {
        Faq faq = new Faq();
        faq.setId(id);
        faq.setCourse(course);
        faq.setFaqState(FaqState.ACCEPTED);
        faq.setQuestionAnswer("Answer");
        faq.setQuestionTitle("Title");
        faq.setCategories(generateFaqCategories());
        return faq;
    }

    public static Set<String> generateFaqCategories() {
        HashSet<String> categories = new HashSet<>();
        categories.add("this is a category");
        categories.add("this is also a category");
        return categories;
    }
}
