export enum FileChangeType {
    CREATE = 'CREATE',
    DELETE = 'DELETE',
    RENAME = 'RENAME',
}

export enum FileType {
    FILE = 'FILE',
    FOLDER = 'FOLDER',
}

export abstract class FileChange {}

export class CreateFileChange extends FileChange {
    constructor(public fileType: FileType, public fileName: string) {
        super();
    }
}

export class DeleteFileChange extends FileChange {
    constructor(public fileType: FileType, public fileName: string) {
        super();
    }
}

export class RenameFileChange extends FileChange {
    constructor(public fileType: FileType, public oldFileName: string, public newFileName: string) {
        super();
    }
}
