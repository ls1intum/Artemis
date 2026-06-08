import { describe, expect, it } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CodeEditorHeaderComponent } from 'app/programming/manage/code-editor/header/code-editor-header.component';

import { MAX_TAB_SIZE } from 'app/editor/monaco-editor/monaco-editor.component';

describe('CodeEditorHeaderComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<CodeEditorHeaderComponent>;
    let comp: CodeEditorHeaderComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [],
            providers: [],
        });
        fixture = TestBed.createComponent(CodeEditorHeaderComponent);
        comp = fixture.componentInstance;
    });

    it('should only allow tab sizes between 1 and the maximum size', () => {
        fixture.componentRef.setInput('tabSize', 4);
        comp.validateTabSize();
        expect(comp.tabSize()).toBe(4);

        fixture.componentRef.setInput('tabSize', -1);
        comp.validateTabSize();
        expect(comp.tabSize()).toBe(1);

        fixture.componentRef.setInput('tabSize', MAX_TAB_SIZE + 10);
        comp.validateTabSize();
        expect(comp.tabSize()).toBe(MAX_TAB_SIZE);
    });

    it('should notify when the tab size was validated', () => {
        let emitted: number | undefined;
        comp.onValidateTabSize.subscribe((tabSize) => {
            emitted = tabSize;
        });

        fixture.componentRef.setInput('tabSize', 5);
        comp.validateTabSize();

        expect(emitted).toBe(5);
    });
});
