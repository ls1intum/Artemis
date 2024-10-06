package de.tum.cit.aet.artemis.communication;

import java.util.HashSet;
import java.util.Set;

import de.tum.cit.aet.artemis.communication.domain.Faq;
import de.tum.cit.aet.artemis.communication.domain.FaqState;
import de.tum.cit.aet.artemis.core.domain.Course;

public class FaqFactory {

    public static Faq generateFaq(Course course, FaqState state, String title, String answer) {
        Faq faq = new Faq();
        faq.setCourse(course);
        faq.setFaqState(state);
        faq.setQuestionTitle(title);
        faq.setQuestionAnswer(answer);
        faq.setCategories(generateFaqCategories());
        return faq;
    }

    public static Set<String> generateFaqCategories() {
        Set<String> categories = new HashSet<>();
        categories.add("this is a category");
        categories.add("this is also a category");
        return categories;
    }
}
