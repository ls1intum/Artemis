import { Operation } from 'fast-json-patch';
import { BaseEntity } from 'app/shared/model/base-entity';
import { SubmissionExerciseType } from 'app/entities/submission.model';
import { Participation } from 'app/entities/participation/participation.model';

export abstract class SubmissionPatch implements BaseEntity {
    public id?: number;
    public submissionExerciseType?: SubmissionExerciseType;
    public participation?: Participation;
    public patch: Operation[];

    protected constructor(patch: Operation[], submissionExerciseType: SubmissionExerciseType) {
        this.id = parseInt(Math.random().toFixed(16).substring(2));
        this.patch = patch;
        this.submissionExerciseType = submissionExerciseType;
    }
}
