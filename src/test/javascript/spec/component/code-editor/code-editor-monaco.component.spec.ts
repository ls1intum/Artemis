import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';

import { CodeEditorMonacoComponent } from 'app/exercises/programming/shared/code-editor/monaco/code-editor-monaco.component';
import { MockComponent } from 'ng-mocks';
import { CodeEditorTutorAssessmentInlineFeedbackComponent } from 'app/exercises/programming/assess/code-editor-tutor-assessment-inline-feedback.component';
import { MonacoEditorModule } from 'app/shared/monaco-editor/monaco-editor.module';
import { MonacoEditorComponent } from 'app/shared/monaco-editor/monaco-editor.component';
import { MockResizeObserver } from '../../helpers/mocks/service/mock-resize-observer';
import { CodeEditorFileService } from 'app/exercises/programming/shared/code-editor/service/code-editor-file.service';
import { CodeEditorRepositoryFileService } from 'app/exercises/programming/shared/code-editor/service/code-editor-repository.service';
import { MockCodeEditorRepositoryFileService } from '../../helpers/mocks/service/mock-code-editor-repository-file.service';
import { MockLocalStorageService } from '../../helpers/mocks/service/mock-local-storage.service';
import { LocalStorageService } from 'ngx-webstorage';
import { Annotation } from 'app/exercises/programming/shared/code-editor/ace/code-editor-ace.component';
import { SimpleChange } from '@angular/core';
describe('CodeEditorMonacoComponent', () => {
    let comp: CodeEditorMonacoComponent;
    let fixture: ComponentFixture<CodeEditorMonacoComponent>;
    let getInlineFeedbackNodeStub: jest.SpyInstance;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MonacoEditorModule],
            declarations: [CodeEditorMonacoComponent, MockComponent(CodeEditorTutorAssessmentInlineFeedbackComponent), MonacoEditorComponent],
            providers: [
                CodeEditorFileService,
                { provide: CodeEditorRepositoryFileService, useClass: MockCodeEditorRepositoryFileService },
                { provide: LocalStorageService, useClass: MockLocalStorageService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CodeEditorMonacoComponent);
                comp = fixture.componentInstance;
                getInlineFeedbackNodeStub = jest.spyOn(comp, 'getInlineFeedbackNode').mockReturnValue(document.createElement('div'));
                global.ResizeObserver = jest.fn().mockImplementation((callback: ResizeObserverCallback) => {
                    return new MockResizeObserver(callback);
                });
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(comp).toBeDefined();
    });

    it('should hide the editor if no file is selected', () => {
        fixture.detectChanges();
        const element = document.getElementById('monaco-editor');
        expect(element).not.toBeNull();
        expect(element!.hidden).toBeTrue();
    });

    it('should hide the editor if a file is being loaded', () => {
        comp.selectedFile = 'file';
        fixture.detectChanges();
        comp.isLoading = true;
        fixture.detectChanges();
        const element = document.getElementById('monaco-editor');
        expect(element).not.toBeNull();
        expect(element!.hidden).toBeTrue();
    });

    it('should display the usable editor when a file is selected', () => {
        comp.selectedFile = 'file';
        comp.isLoading = false;
        comp.isTutorAssessment = false;
        fixture.detectChanges();
        const element = document.getElementById('monaco-editor');
        expect(element).not.toBeNull();
        expect(element!.hidden).toBeFalse();
        expect(comp.editor.isReadOnly()).toBeFalse();
    });

    it('should display build annotations for the current file', async () => {
        const setAnnotationsStub = jest.spyOn(comp.editor, 'setAnnotations').mockImplementation();
        const selectFileInEditorStub = jest.spyOn(comp, 'selectFileInEditor').mockImplementation();
        const buildAnnotations: Annotation[] = [
            {
                fileName: 'file1',
                text: 'error',
                type: 'error',
                hash: 'file111error',
                timestamp: 0,
                row: 1,
                column: 1,
            },
            {
                fileName: 'file2',
                text: 'error',
                type: 'error',
                hash: 'file211error',
                timestamp: 0,
                row: 1,
                column: 1,
            },
        ];
        comp.annotationsArray = buildAnnotations;
        comp.selectedFile = 'file1';
        fixture.detectChanges();
        await comp.ngOnChanges({ selectedFile: new SimpleChange(undefined, 'file1', false) });
        comp.selectedFile = 'file2';
        fixture.detectChanges();
        await comp.ngOnChanges({ selectedFile: new SimpleChange('file1', 'file2', false) });
        expect(setAnnotationsStub).toHaveBeenCalledTimes(2);
        expect(selectFileInEditorStub).toHaveBeenCalledTimes(2);
        expect(setAnnotationsStub).toHaveBeenNthCalledWith(1, [buildAnnotations[0]], false);
        expect(setAnnotationsStub).toHaveBeenNthCalledWith(2, [buildAnnotations[1]], false);
    });

    it('should display feedback when viewing a tutor assessment', async () => {
        const addLineWidgetStub = jest.spyOn(comp.editor, 'addLineWidget').mockImplementation();
        const selectFileInEditorStub = jest.spyOn(comp, 'selectFileInEditor').mockImplementation();
        comp.isTutorAssessment = true;
        comp.selectedFile = 'file1.java';
        comp.feedbacks = [
            {
                id: 1,
                reference: 'file:file1.java_line:1',
            },
            {
                id: 2,
                reference: 'file:file1.java_line:2',
            },
            {
                id: 3,
                reference: 'file:file2.java_line:9',
            },
        ];
        fixture.detectChanges();
        await comp.ngOnChanges({ selectedFile: new SimpleChange(undefined, 'file1', false) });
        expect(addLineWidgetStub).toHaveBeenCalledTimes(2);
        expect(addLineWidgetStub).toHaveBeenNthCalledWith(1, 2, `feedback-1`, document.createElement('div'));
        expect(addLineWidgetStub).toHaveBeenNthCalledWith(2, 3, `feedback-2`, document.createElement('div'));
        expect(getInlineFeedbackNodeStub).toHaveBeenCalledTimes(2);
        expect(selectFileInEditorStub).toHaveBeenCalledOnce();
    });
});
