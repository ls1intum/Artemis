import { ArtemisTestModule } from '../../../test.module';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockPipe } from 'ng-mocks';
import { SolutionEntryComponent } from 'app/exercises/shared/exercise-hint/shared/solution-entry.component';
import { ProgrammingExerciseSolutionEntry } from 'app/entities/hestia/programming-exercise-solution-entry.model';
import { MonacoEditorComponent } from 'app/shared/monaco-editor/monaco-editor.component';

describe('Solution Entry Component', () => {
    let comp: SolutionEntryComponent;
    let fixture: ComponentFixture<SolutionEntryComponent>;
    let solutionEntry: ProgrammingExerciseSolutionEntry;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [SolutionEntryComponent, MockComponent(MonacoEditorComponent), MockPipe(ArtemisTranslatePipe)],
        }).compileComponents();
        fixture = TestBed.createComponent(SolutionEntryComponent);
        comp = fixture.componentInstance;

        comp.solutionEntry = new ProgrammingExerciseSolutionEntry();
        comp.solutionEntry.id = 123;
        comp.solutionEntry.filePath = '/src/de/Test.java';
        comp.solutionEntry.line = 1;
        comp.solutionEntry.code = 'ABC';

        solutionEntry = comp.solutionEntry;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should correctly setup editors', () => {
        const setStartLineNumberStub = jest.spyOn(comp.editor, 'setStartLineNumber').mockImplementation();
        const changeModelStub = jest.spyOn(comp.editor, 'changeModel').mockImplementation();
        fixture.detectChanges();

        expect(setStartLineNumberStub).toHaveBeenCalledExactlyOnceWith(solutionEntry.line);
        expect(changeModelStub).toHaveBeenCalledExactlyOnceWith(solutionEntry.filePath, solutionEntry.code);
    });

    it('should update the editor height', () => {
        const contentHeight = 123;
        comp.onContentSizeChange(contentHeight);
        expect(comp.editorHeight).toEqual(contentHeight);
    });

    it('should update the solution entry code', () => {
        const newCode = 'DEF';
        comp.onEditorContentChange(newCode);
        expect(comp.solutionEntry.code).toEqual(newCode);
    });
});
