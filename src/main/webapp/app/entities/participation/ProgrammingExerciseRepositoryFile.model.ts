import { FileType } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';

export class ProgrammingExerciseRepositoryFile {
    public filename: string;
    public fileType: FileType;
    public fileContent: string;

    public isFolder(): boolean {
        return this.fileType === FileType.FOLDER;
    }

    public isFile(): boolean {
        return this.fileType === FileType.FILE;
    }
}
