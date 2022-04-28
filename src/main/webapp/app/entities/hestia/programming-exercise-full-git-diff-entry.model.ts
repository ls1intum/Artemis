import { BaseEntity } from 'app/shared/model/base-entity';

export class ProgrammingExerciseFullGitDiffEntry implements BaseEntity {
    public id?: number;

    public previousFilePath: string;
    public filePath: string;
    public previousLine?: number;
    public line?: number;
    public previousCode?: string;
    public code?: string;

    constructor() {}
}
