import { Injectable, OnDestroy } from '@angular/core';

export type EditorConfig = {
    buildable: boolean;
    editableInstructions: boolean;
};

@Injectable({ providedIn: 'root' })
export class CodeEditorConfigService {
    private config: EditorConfig;
    setConfig(config: EditorConfig) {
        this.config = config;
    }
    getConfig() {
        return this.config;
    }
}
