import { BaseEntity } from 'app/shared/model/base-entity';
import { Course } from 'app/entities/course.model';
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
