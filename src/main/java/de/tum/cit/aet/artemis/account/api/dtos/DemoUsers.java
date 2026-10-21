package de.tum.cit.aet.artemis.account.api.dtos;

import java.util.List;
import java.util.stream.Stream;

import de.tum.cit.aet.artemis.account.domain.User;

/**
 * The users of the demo course.
 *
 * @param student    the demo student, the account visitors use to explore the student view.
 * @param tutor      the demo tutor, who assesses submissions and leads the tutorial groups.
 * @param editor     the demo editor.
 * @param instructor the demo instructor, who owns the demo content.
 * @param peers      the fictional classmates of the demo student.
 */
public record DemoUsers(User student, User tutor, User editor, User instructor, List<User> peers) {

    /**
     * @return the demo student followed by their classmates.
     */
    public List<User> students() {
        return Stream.concat(Stream.of(student), peers.stream()).toList();
    }
}
