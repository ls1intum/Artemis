import { BaseEntity } from 'app/shared/model/base-entity';
import { User } from 'app/core/user/user.model';
import { Course } from 'app/entities/course.model';

export const enum Weekday {
    MONDAY = 'MONDAY',
    TUESDAY = 'TUESDAY',
    WEDNESDAY = 'WEDNESDAY',
    THURSDAY = 'THURSDAY',
    FRIDAY = 'FRIDAY',
}

export const enum Language {
    ENGLISH = 'ENGLISH',
    GERMAN = 'GERMAN',
}

export class TutorGroup implements BaseEntity {
    id: number;
    name: string;
    capacity: number;
    weekday: Weekday;
    timeSlot: string;
    language: Language;
    room: string;
    tutor: User;
    students: User[];
    course: Course;

    constructor() {}
}
