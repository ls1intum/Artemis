import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { BaseEntity } from 'app/foundation/model/base-entity';

export class ExerciseGroup implements BaseEntity {
    public id?: number;
    public title?: string;
    public isMandatory?: boolean;
    public exam?: Exam;
    public exercises?: Exercise[];
}

/**
 * Minimal reference used to persist the order of an exam's exercise groups. The client sends the group ids in the desired
 * order and receives the persisted order back as the same id list; it re-applies that order to its already-loaded,
 * fully-detailed groups so no exercise detail (quiz questions, template/solution participations) is lost.
 */
export interface ExerciseGroupOrderDTO {
    id?: number;
}
