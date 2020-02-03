import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent } from 'ng-mocks';
import { TranslateModule } from '@ngx-translate/core';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { By } from '@angular/platform-browser';
import { WindowRef } from 'app/core/websocket/window.service';
import { DebugElement } from '@angular/core';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { AceEditorModule } from 'ng2-ace-editor';
import { CodeEditorGridService, CodeEditorInstructionsComponent, CodeEditorRepositoryFileService } from 'app/code-editor';
import { ArtemisTestModule } from '../../test.module';
import { MockCodeEditorRepositoryFileService } from '../../mocks';
import { ProgrammingExercise } from 'app/entities/programming-exercise';
import { ArtemisMarkdownEditorModule } from 'app/markdown-editor';
import { MockCodeEditorGridService } from '../../mocks/mock-code-editor-grid.service';
import { ProgrammingExerciseInstructionComponent } from 'app/entities/programming-exercise/instructions/instructions-render';
import { ProgrammingExerciseEditableInstructionComponent } from 'app/entities/programming-exercise/instructions/instructions-editor';

chai.use(sinonChai);
const expect = chai.expect;

describe('CodeEditorInstructionsComponent', () => {
    let comp: CodeEditorInstructionsComponent;
    let fixture: ComponentFixture<CodeEditorInstructionsComponent>;
    let debugElement: DebugElement;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), AceEditorModule, ArtemisMarkdownEditorModule, ArtemisTestModule, NgbModule],
            declarations: [CodeEditorInstructionsComponent, MockComponent(ProgrammingExerciseInstructionComponent), MockComponent(ProgrammingExerciseEditableInstructionComponent)],
            providers: [
                WindowRef,
                { provide: CodeEditorRepositoryFileService, useClass: MockCodeEditorRepositoryFileService },
                { provide: CodeEditorGridService, useClass: MockCodeEditorGridService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CodeEditorInstructionsComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;
            });
    });

    it('should update problem statement if is editable', () => {
        const problemStatement = 'lorem ipsum';
        const newProblemStatement = 'new lorem ipsum';
        const exercise = { id: 1, problemStatement } as ProgrammingExercise;
        comp.exercise = exercise;
        comp.editable = true;
        fixture.detectChanges();

        const editableInstructionComp = debugElement.query(By.css('jhi-programming-exercise-editable-instructions'));
        expect(editableInstructionComp).to.exist;
        const instructionCompElem = debugElement.query(By.css('jhi-programming-exercise-instructions'));
        expect(instructionCompElem).not.to.exist;

        comp.onProblemStatementEditorUpdate(newProblemStatement);
        expect(comp.exercise.problemStatement).to.equal(newProblemStatement);
    });

    it('should not show markdown-editor nor other elements that are restricted to edit mode', () => {
        const problemStatement = 'lorem ipsum';
        const exercise = { id: 1, problemStatement } as ProgrammingExercise;
        comp.exercise = exercise;
        fixture.detectChanges();

        const saveInstructionsButton = debugElement.query(By.css('#save-instructions-button'));
        expect(saveInstructionsButton).not.to.exist;
        const instructionsLoadingIndicator = debugElement.query(By.css('.instructions-status'));
        expect(instructionsLoadingIndicator).not.to.exist;
        const instructionCompElem = debugElement.query(By.css('jhi-programming-exercise-instructions'));
        expect(instructionCompElem).to.exist;
        const editableInstructionComp = debugElement.query(By.css('jhi-programming-exercise-editable-instructions'));
        expect(editableInstructionComp).not.to.exist;
    });
});
