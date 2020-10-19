// IMPORTANT NOTICE: The following strings have to be consistent with
// the ones defined in LectureModule.java
import { BaseEntity } from 'app/shared/model/base-entity';
import { Moment } from 'moment';
import { Lecture } from 'app/entities/lecture.model';
import { LearningGoal } from 'app/entities/learningGoal.model';

export enum LectureModuleType {
    ATTACHMENT = 'attachment',
    EXERCISE = 'exercise',
    HTML = 'html',
    VIDEO = 'video',
}

export abstract class LectureModule implements BaseEntity {
    public id?: number;
    public name?: string;
    public releaseDate?: Moment;
    public lecture?: Lecture;
    public learningGoals?: LearningGoal[];
    public type?: LectureModuleType;

    protected constructor(type: LectureModuleType) {
        this.type = type;
    }
}
