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
        public faqState: FaqState,
        public questionTitle: string,
        public courseId?: number,
        public categories?: FaqCategory[],
        public questionAnswer?: string,
    ) {}

    public static toCreateFaqDto(faq: Faq): CreateFaqDTO {
        if (!faq?.faqState) {
            throw new Error('The state should be present to create FAQ');
        }

        return new CreateFaqDTO(faq.faqState, faq.questionTitle ?? '', faq.course?.id, faq.categories, faq.questionAnswer);
    }
}

export class UpdateFaqDTO {
    constructor(
        public id: number,
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

        return new UpdateFaqDTO(faq.id, faq.faqState, faq.questionTitle ?? '', faq.categories, faq.questionAnswer);
    }
}
