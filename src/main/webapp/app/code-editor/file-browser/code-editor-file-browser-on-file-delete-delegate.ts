import { FileChange } from 'app/code-editor/model/file-change.model';

export interface IFileDeleteDelegate {
    onFileDeleted(fileChange: FileChange): void;
}
