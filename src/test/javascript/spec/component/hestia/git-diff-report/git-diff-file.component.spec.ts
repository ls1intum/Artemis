import { ArtemisTestModule } from '../../../test.module';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { GitDiffFileComponent } from 'app/exercises/programming/hestia/git-diff-report/git-diff-file.component';
import { MonacoEditorModule } from 'app/shared/monaco-editor/monaco-editor.module';
import { MockResizeObserver } from '../../../helpers/mocks/service/mock-resize-observer';

describe('ProgrammingExerciseGitDiffEntry Component', () => {
    let comp: GitDiffFileComponent;
    let fixture: ComponentFixture<GitDiffFileComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MonacoEditorModule],
            declarations: [GitDiffFileComponent],
            providers: [],
        }).compileComponents();
        // Required because Monaco uses the ResizeObserver for the diff editor.
        global.ResizeObserver = jest.fn().mockImplementation((callback: ResizeObserverCallback) => {
            return new MockResizeObserver(callback);
        });
        fixture = TestBed.createComponent(GitDiffFileComponent);
        comp = fixture.componentInstance;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        comp.diffEntries = [];
        fixture.detectChanges();
        expect(comp).toBeDefined();
        // TODO write actual tests or delete this file
    });
});
