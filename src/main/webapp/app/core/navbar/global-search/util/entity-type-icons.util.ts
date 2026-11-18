import { IconDefinition } from '@fortawesome/fontawesome-svg-core';
import {
    faBook,
    faCalendarCheck,
    faCheckDouble,
    faComment,
    faFileUpload,
    faFont,
    faGraduationCap,
    faHashtag,
    faKeyboard,
    faProjectDiagram,
    faQuestion,
    faQuestionCircle,
} from '@fortawesome/free-solid-svg-icons';

/**
 * Icon for a searchable entity type, shared between the search palette and the Iris answer's entity
 * source chips so the same entity looks the same everywhere. Exercises are refined by their exercise
 * type, matching the palette's badge-based refinement.
 *
 * @param type the entity type discriminator (exercise, lecture, lecture_unit, exam, faq, channel, course, post, answer_post)
 * @param exerciseTypeHint the exercise type or badge text, case-insensitive (e.g. 'programming' or 'File Upload')
 */
export function iconForEntityType(type?: string, exerciseTypeHint?: string): IconDefinition {
    if (type === 'exercise') {
        switch (exerciseTypeHint?.toLowerCase()) {
            case 'programming':
                return faKeyboard;
            case 'modeling':
                return faProjectDiagram;
            case 'text':
                return faFont;
            case 'file upload':
            case 'file-upload':
                return faFileUpload;
            case 'quiz':
                return faCheckDouble;
            default:
                return faQuestion;
        }
    }
    if (type === 'lecture' || type === 'lecture_unit') {
        return faBook;
    }
    if (type === 'channel') {
        return faHashtag;
    }
    if (type === 'post' || type === 'answer_post') {
        return faComment;
    }
    if (type === 'faq') {
        return faQuestionCircle;
    }
    if (type === 'exam') {
        return faCalendarCheck;
    }
    if (type === 'course') {
        return faGraduationCap;
    }
    return faQuestion;
}
