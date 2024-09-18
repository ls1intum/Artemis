package de.tum.cit.aet.artemis;

import java.util.HashSet;
import java.util.Set;

import de.tum.cit.aet.artemis.communication.domain.Faq;
import de.tum.cit.aet.artemis.communication.domain.FaqState;
import de.tum.cit.aet.artemis.core.domain.Course;

public class FaqFactory {

    public static Faq generateFaq(Course course) {
        Faq faq = new Faq();
        faq.setCourse(course);
        faq.setFaqState(FaqState.ACCEPTED);
        faq.setQuestionAnswer("Answer");
        faq.setQuestionTitle("Title");
        faq.setCategories(generateFaqCategories());
        return faq;
    }

    public static Set<String> generateFaqCategories() {
        Set<String> categories = new HashSet<>();
        categories.add("this is a category");
        categories.add("this is also a category");
        return categories;
    }

    private FaqFactory() {
    }
}
