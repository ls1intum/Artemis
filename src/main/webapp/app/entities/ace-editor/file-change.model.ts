export enum FileChangeType {
    CREATE = 'CREATE',
    DELETE = 'DELETE',
    RENAME = 'RENAME',
}

export abstract class FileChange {
    public changeType: FileChangeType;
}

export class CreateFileChange extends FileChange {
    constructor(public fileName: string) {
        super();
    }
}

export class DeleteFileChange extends FileChange {
    constructor(public fileName: string) {
        super();
    }
}

export class RenameFileChange extends FileChange {
    constructor(public oldFileName: string, public newFileName: string) {
        super();
    }
}
