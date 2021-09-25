import { BaseEntity } from 'app/shared/model/base-entity';
import dayjs from 'dayjs';
import { Lecture } from 'app/entities/lecture.model';
import { LearningGoal } from 'app/entities/learningGoal.model';

// IMPORTANT NOTICE: The following strings have to be consistent with
// the ones defined in LectureUnit.java
export enum LectureUnitType {
    ATTACHMENT = 'attachment',
    EXERCISE = 'exercise',
    TEXT = 'text',
    VIDEO = 'video',
}

export abstract class LectureUnit implements BaseEntity {
    public id?: number;
    public name?: string;
    public releaseDate?: dayjs.Dayjs;
    public lecture?: Lecture;
    public learningGoals?: LearningGoal[];
    public type?: LectureUnitType;
    // calculated property
    public visibleToStudents?: boolean;

    protected constructor(type: LectureUnitType) {
        this.type = type;
    }
}
