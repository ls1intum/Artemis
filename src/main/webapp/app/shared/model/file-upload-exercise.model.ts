export interface IFileUploadExercise {
    id?: number;
    filePattern?: string;
}

export class FileUploadExercise implements IFileUploadExercise {
    constructor(public id?: number, public filePattern?: string) {}
}
