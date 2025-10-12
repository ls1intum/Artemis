import { BaseEntity } from 'app/shared/model/base-entity';
import { Course } from 'app/core/course/shared/entities/course.model';
import { FaqCategory } from './faq-category.model';

export enum FaqState {
    ACCEPTED = 'ACCEPTED',
    REJECTED = 'REJECTED',
    PROPOSED = 'PROPOSED',
}

export class Faq implements BaseEntity {
    public id?: number;
    public questionTitle?: string;
    public questionAnswer?: string;
    public faqState?: FaqState;
    public course?: Course;
    public categories?: FaqCategory[];
}

export class CreateFaqDTO {
    constructor(
        public courseId: number,
        public faqState: FaqState,
        public questionTitle: string,
        public categories?: FaqCategory[],
        public questionAnswer?: string,
    ) {}
}

export class UpdateFaqDTO {
    constructor(
        public id: number,
        public courseId: number,
        public faqState: FaqState,
        public questionTitle: string,
        public categories?: FaqCategory[],
        public questionAnswer?: string,
    ) {}

    public static toUpdateDto(faq: Faq): UpdateFaqDTO {
        if (!faq?.id) {
            throw new Error('The id should be present to update FAQ');
        }
        if (!faq?.faqState) {
            throw new Error('The state should be present to update FAQ');
        }

        const courseId = faq?.course?.id;
        if (!courseId) {
            throw new Error('The course should be present to update FAQ');
        }

        return new UpdateFaqDTO(faq.id, courseId, faq.faqState, (faq.questionTitle ?? '').trim(), faq.categories, faq.questionAnswer);
    }
}
