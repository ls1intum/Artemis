// IMPORTANT NOTICE: The following strings have to be consistent with
// the ones defined in LectureUnit.java
import { BaseEntity } from 'app/shared/model/base-entity';
import { Moment } from 'moment';
import { Lecture } from 'app/entities/lecture.model';
import { LearningGoal } from 'app/entities/learningGoal.model';

export enum LectureUnitType {
    ATTACHMENT = 'attachment',
    EXERCISE = 'exercise',
    TEXT = 'text',
    VIDEO = 'video',
}

export abstract class LectureUnit implements BaseEntity {
    public id?: number;
    public name?: string;
    public releaseDate?: Moment;
    public lecture?: Lecture;
    public learningGoals?: LearningGoal[];
    public type?: LectureUnitType;
    // calculated property
    public visibleToStudents?: boolean;

    protected constructor(type: LectureUnitType) {
        this.type = type;
    }
}
