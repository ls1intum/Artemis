import { MonacoEditorAction } from './monaco-editor-action.model';

/**
 * Class representing domain actions for Artemis-specific use cases.
 * TODO: In the future, each domain action should have its own logic and a unique identifier (e.g. multiple choice questions, drag and drop questions).
 */
export abstract class MonacoEditorDomainAction extends MonacoEditorAction {}
