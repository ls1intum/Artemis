import { FileChange } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';

export interface IFileDeleteDelegate {
    /**
     * Method signature of the on File Deleted method
     * Classes which implement this interface have to comply with this method signature
     * @param fileChange
     */
    onFileDeleted(fileChange: FileChange): void;
}
