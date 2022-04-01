import { BaseEntity } from 'app/shared/model/base-entity';

export class ProgrammingExerciseTestwiseCoverageEntry implements BaseEntity {
    public id?: number;

    public filePath?: string;
    public startLine?: number;
    public lineCount?: number;

    constructor() {}
}
