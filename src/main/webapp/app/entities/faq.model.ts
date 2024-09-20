import { BaseEntity } from 'app/shared/model/base-entity';
import { Course } from 'app/entities/course.model';
import { FAQCategory } from './faq-category.model';

export enum FAQState {
    ACCEPTED,
    REJECTED,
    PROPOSED,
}

export class FAQ implements BaseEntity {
    public id?: number;
    public questionTitle?: string;
    public questionAnswer?: string;
    public faqState?: FAQState;
    public course?: Course;
    public categories?: FAQCategory[];
}
