import { FileChange } from 'app/entities/ace-editor/file-change.model';

export interface IFileDeleteDelegate {
    onFileDeleted(fileChange: FileChange): void;
}
