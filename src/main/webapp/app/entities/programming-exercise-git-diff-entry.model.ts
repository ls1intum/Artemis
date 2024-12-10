import { BaseEntity } from 'app/shared/model/base-entity';

export class ProgrammingExerciseGitDiffEntry implements BaseEntity {
    public id?: number;

    public previousFilePath?: string;
    public filePath?: string;
    public previousStartLine?: number;
    public startLine?: number;
    public previousLineCount?: number;
    public lineCount?: number;

    constructor() {}
}
