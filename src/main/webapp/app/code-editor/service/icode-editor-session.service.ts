import { AnnotationArray } from 'app/entities/ace-editor';

export interface ICodeEditorSessionService {
    storeSession: (session: { errors: { [fileName: string]: AnnotationArray }; timestamp: number }) => void;
    loadSession: () => { errors: { [fileName: string]: AnnotationArray }; timestamp: number } | null;
}
