export interface IFileUploadSubmission {
    id?: number;
    filePath?: string;
}

export class FileUploadSubmission implements IFileUploadSubmission {
    constructor(public id?: number, public filePath?: string) {}
}
