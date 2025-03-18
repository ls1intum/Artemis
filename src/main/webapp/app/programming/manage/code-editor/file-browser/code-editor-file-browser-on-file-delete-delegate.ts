import { FileChange } from 'app/programming/shared/code-editor/model/code-editor.model';

export interface IFileDeleteDelegate {
    onFileDeleted(fileChange: FileChange): void;
}
