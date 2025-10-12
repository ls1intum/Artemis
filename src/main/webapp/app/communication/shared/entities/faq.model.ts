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
        return new UpdateFaqDTO(faq.id!, faq.course?.id!, faq.faqState!, faq.questionTitle ?? '', faq.categories, faq.questionAnswer);
    }
}
