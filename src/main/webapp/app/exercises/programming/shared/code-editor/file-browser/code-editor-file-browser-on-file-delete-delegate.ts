import { FileChange } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';

export interface IFileDeleteDelegate {
    onFileDeleted(fileChange: FileChange): void;
}
