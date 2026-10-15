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
import { describe, expect, it } from 'vitest';
import { iconForEntityType } from './entity-type-icons.util';

describe('iconForEntityType', () => {
    it('refines exercises by exercise type, case-insensitively', () => {
        expect(iconForEntityType('exercise', 'programming')).toBe(faKeyboard);
        expect(iconForEntityType('exercise', 'modeling')).toBe(faProjectDiagram);
        expect(iconForEntityType('exercise', 'text')).toBe(faFont);
        expect(iconForEntityType('exercise', 'Quiz')).toBe(faCheckDouble);
        expect(iconForEntityType('exercise', 'File Upload')).toBe(faFileUpload);
        expect(iconForEntityType('exercise', 'file-upload')).toBe(faFileUpload);
        expect(iconForEntityType('exercise', undefined)).toBe(faQuestion);
    });

    it('maps the remaining entity types like the palette', () => {
        expect(iconForEntityType('lecture')).toBe(faBook);
        expect(iconForEntityType('lecture_unit')).toBe(faBook);
        expect(iconForEntityType('channel')).toBe(faHashtag);
        expect(iconForEntityType('post')).toBe(faComment);
        expect(iconForEntityType('answer_post')).toBe(faComment);
        expect(iconForEntityType('faq')).toBe(faQuestionCircle);
        expect(iconForEntityType('exam')).toBe(faCalendarCheck);
        expect(iconForEntityType('course')).toBe(faGraduationCap);
        expect(iconForEntityType(undefined)).toBe(faQuestion);
    });
});
