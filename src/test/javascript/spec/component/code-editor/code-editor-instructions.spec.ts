import { async, ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent } from 'ng-mocks';
import { TranslateModule } from '@ngx-translate/core';
import { By } from '@angular/platform-browser';
import { WindowRef } from 'app/core';
import { DebugElement } from '@angular/core';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { AceEditorModule } from 'ng2-ace-editor';
import { SinonStub, stub } from 'sinon';
import { of } from 'rxjs';
import { CodeEditorInstructionsComponent, CodeEditorRepositoryFileService } from 'app/code-editor';
import { ArTEMiSTestModule } from '../../test.module';
import { MockCodeEditorRepositoryFileService } from '../../mocks';
import {
    ProgrammingExercise,
    ProgrammingExerciseEditableInstructionComponent,
    ProgrammingExerciseInstructionComponent,
    ProgrammingExerciseService,
} from 'app/entities/programming-exercise';
import { ArTEMiSMarkdownEditorModule } from 'app/markdown-editor';

chai.use(sinonChai);
const expect = chai.expect;

describe('CodeEditorInstructionsComponent', () => {
    let comp: CodeEditorInstructionsComponent;
    let fixture: ComponentFixture<CodeEditorInstructionsComponent>;
    let debugElement: DebugElement;
    let programmingExerciseService: ProgrammingExerciseService;
    let updateProblemStatementStub: SinonStub;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), AceEditorModule, ArTEMiSMarkdownEditorModule, ArTEMiSTestModule],
            declarations: [CodeEditorInstructionsComponent, MockComponent(ProgrammingExerciseInstructionComponent), MockComponent(ProgrammingExerciseEditableInstructionComponent)],
            providers: [WindowRef, { provide: CodeEditorRepositoryFileService, useClass: MockCodeEditorRepositoryFileService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CodeEditorInstructionsComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;
                programmingExerciseService = debugElement.injector.get(ProgrammingExerciseService);
                updateProblemStatementStub = stub(programmingExerciseService, 'updateProblemStatement');
            });
    });

    afterEach(() => {
        updateProblemStatementStub.restore();
    });

    it('should update problem statement on save button click if editable', async((done: any) => {
        const problemStatement = 'lorem ipsum';
        const newProblemStatement = 'new lorem ipsum';
        const exercise = { id: 1, problemStatement } as ProgrammingExercise;
        updateProblemStatementStub.returns(of(null));
        comp.exercise = exercise;
        comp.editableInstructions = true;
        fixture.detectChanges();

        const saveInstructionsButton = debugElement.query(By.css('#save-instructions-button'));
        expect(saveInstructionsButton).to.exist;
        const instructionCompElem = debugElement.query(By.css('jhi-programming-exercise-instructions'));
        expect(instructionCompElem).not.to.exist;
        const editableInstructionComp = debugElement.query(By.css('jhi-programming-exercise-editable-instructions'));
        expect(editableInstructionComp).to.exist;

        comp.onProblemStatementEditorUpdate(newProblemStatement);
        expect(comp.exercise.problemStatement).to.equal(newProblemStatement);
        expect(comp.unsavedChanges).to.equal(true);
        fixture.detectChanges();

        saveInstructionsButton.nativeElement.click();
        fixture
            .whenStable()
            .then(() => {
                expect(updateProblemStatementStub).to.have.been.calledOnceWithExactly(exercise.id, newProblemStatement);
                expect(comp.unsavedChanges).to.be.false;
            })
            .catch(err => done.fail(err));
    }));

    it('should not show markdown-editor nor other elements that are restricted to edit mode', () => {
        const problemStatement = 'lorem ipsum';
        const exercise = { id: 1, problemStatement } as ProgrammingExercise;
        updateProblemStatementStub.returns(of(null));
        comp.exercise = exercise;
        fixture.detectChanges();

        const saveInstructionsButton = debugElement.query(By.css('#save-instructions-button'));
        expect(saveInstructionsButton).not.to.exist;
        const instructionsLoadingIndicator = debugElement.query(By.css('#instructions-status'));
        expect(instructionsLoadingIndicator).not.to.exist;
        const instructionCompElem = debugElement.query(By.css('jhi-programming-exercise-instructions'));
        expect(instructionCompElem).to.exist;
        const editableInstructionComp = debugElement.query(By.css('jhi-programming-exercise-editable-instructions'));
        expect(editableInstructionComp).not.to.exist;
    });
});
