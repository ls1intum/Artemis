import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProgrammingExerciseSolutionEntry } from 'app/entities/hestia/programming-exercise-solution-entry.model';
import { SolutionEntryComponent } from 'app/exercises/shared/exercise-hint/shared/solution-entry.component';
import { AceEditorComponent } from 'app/shared/markdown-editor/ace-editor/ace-editor.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe } from 'ng-mocks';
import { ArtemisTestModule } from '../../../test.module';

describe('Solution Entry Component', () => {
    let comp: SolutionEntryComponent;
    let fixture: ComponentFixture<SolutionEntryComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [SolutionEntryComponent, AceEditorComponent, MockPipe(ArtemisTranslatePipe)],
        }).compileComponents();
        fixture = TestBed.createComponent(SolutionEntryComponent);
        comp = fixture.componentInstance;

        comp.solutionEntry = new ProgrammingExerciseSolutionEntry();
        comp.solutionEntry.id = 123;
        comp.solutionEntry.filePath = '/src/de/Test.java';
        comp.solutionEntry.line = 1;
        comp.solutionEntry.code = 'ABC';
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should setup editors', () => {
        jest.spyOn(comp.editor.getEditor(), 'setOptions');
        jest.spyOn(comp.editor.getEditor().getSession(), 'setValue');

        comp.ngOnInit();

        expect(comp.editor.getEditor().setOptions).toHaveBeenCalledOnce();
        expect(comp.editor.getEditor().setOptions).toHaveBeenCalledWith({
            animatedScroll: true,
            maxLines: Infinity,
            showPrintMargin: false,
        });

        expect(comp.editor.getEditor().getSession().setValue).toHaveBeenCalledOnce();
        expect(comp.editor.getEditor().getSession().setValue).toHaveBeenCalledWith('ABC');
    });

    it('should give correct line for gutter', () => {
        comp.ngOnInit();
        const gutterRendererNow = comp.editor.getEditor().session.gutterRenderer;
        const config = { characterWidth: 1 };

        expect(gutterRendererNow.getText(null, 2)).toBe(3);
        expect(gutterRendererNow.getWidth(null, 2, config)).toBe(1);
    });
});
