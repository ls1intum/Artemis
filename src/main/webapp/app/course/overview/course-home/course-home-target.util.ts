import { Params } from '@angular/router';
import { CourseHomeTarget } from './course-home.model';

/** Only server-selected resource identifiers can choose destinations; welcome prose cannot. */
export function courseHomeTarget(courseId: number, target: CourseHomeTarget): { commands: (string | number)[]; queryParams?: Params } {
    const base: (string | number)[] = ['/courses', courseId];
    switch (target.kind) {
        case 'EXERCISE':
            return { commands: [...base, 'exercises', target.id] };
        case 'LECTURE':
            return { commands: [...base, 'lectures', target.id] };
        case 'EXAM':
            return { commands: [...base, 'exams', target.id] };
        case 'FAQ':
            return { commands: [...base, 'faq'], queryParams: { faqId: target.id } };
        case 'THREAD':
            return { commands: [...base, 'communication'], queryParams: { conversationId: target.conversationId, messageId: target.id, focusReplyId: target.focusReplyId } };
        case 'IRIS_SESSION':
            return { commands: [...base, 'iris'], queryParams: { irisSession: target.id } };
        case 'STATISTICS':
            return { commands: [...base, 'statistics'] };
        case 'CALENDAR':
            return { commands: [...base, 'calendar'] };
        case 'LEARNING_PATH':
            return { commands: [...base, 'learning-path'] };
        default:
            return { commands: [...base, 'home'] };
    }
}
