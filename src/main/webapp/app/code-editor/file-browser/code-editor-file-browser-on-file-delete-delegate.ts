import { FileChange } from 'app/code-editor/model/code-editor.model';

export interface IFileDeleteDelegate {
    onFileDeleted(fileChange: FileChange): void;
}
