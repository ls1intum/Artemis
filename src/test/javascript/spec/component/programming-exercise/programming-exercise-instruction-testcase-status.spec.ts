import { DebugElement, SimpleChange } from '@angular/core';
import { async, ComponentFixture, TestBed } from '@angular/core/testing';
import { AceEditorModule } from 'ng2-ace-editor';
import * as chai from 'chai';
import { ParticipationType, ProgrammingExerciseInstructorStatusComponent } from 'app/entities/programming-exercise';
import { ArTEMiSTestModule } from '../../test.module';
import { TranslateModule } from '@ngx-translate/core';
import { By } from '@angular/platform-browser';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { ProgrammingExerciseInstructionInstructorAnalysisComponent } from '../../../../../main/webapp/app/entities/programming-exercise';
import { TaskCommand } from '../../../../../main/webapp/app/markdown-editor/domainCommands/programming-exercise/task.command';

const expect = chai.expect;

describe('ProgrammingExerciseInstructionTestcaseStatusComponent', () => {
    let comp: ProgrammingExerciseInstructorStatusComponent;
    let fixture: ComponentFixture<ProgrammingExerciseInstructorStatusComponent>;
    let debugElement: DebugElement;

    const taskCommand = new TaskCommand();
    const taskRegex = taskCommand.getTagRegex('g');
    const exerciseTestCases = ['test1', 'test2', 'test6', 'test7'];
    const problemStatement =
        '1. [task][SortStrategy Interface](test1,test2) \n 2. [task][SortStrategy Interface](test3) \n lorem ipsum \n lorem \n  3. [task][SortStrategy Interface](test2,test4)';
    const problemStatementTasks = ['[task][SortStrategy Interface](test1,test2)', '[task][SortStrategy Interface](test3)', '[task][SortStrategy Interface](test2,test4)'];
    const problemStatementTestCases = ['test1', 'test2', 'test3', 'test4'];

    const missingTestCases = ['test6', 'test7'];
    const invalidTestCases = ['test3', 'test4'];

    beforeEach(async(() => {
        TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArTEMiSTestModule, AceEditorModule, NgbModule],
            declarations: [ProgrammingExerciseInstructionInstructorAnalysisComponent],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ProgrammingExerciseInstructionInstructorAnalysisComponent);
                debugElement = fixture.debugElement;
                comp = fixture.componentInstance as ProgrammingExerciseInstructionInstructorAnalysisComponent;
            });
    }));

    it('should not render if no test cases were provided', () => {
        comp.problemStatement = problemStatement;
        comp.taskRegex = taskRegex;
        const changes = [{ problemStatement: new SimpleChange(undefined, problemStatement, true) }];
        comp.ngOnChanges(changes);
        fixture.detectChanges();

        expect(debugElement.nativeElement.innerHtml).to.be.undefined;
        expect(comp.missingTestCases).to.be.empty;
        expect(comp.invalidTestCases).to.be.empty;
    });

    it('should render warnings on missing and invalid test cases', () => {
        comp.problemStatement = problemStatement;
        comp.taskRegex = taskRegex;
        comp.exerciseTestCases = exerciseTestCases;
        const changes = { problemStatement: new SimpleChange(undefined, problemStatement, false), exerciseTestCases: new SimpleChange(undefined, exerciseTestCases, false) };
        comp.ngOnChanges(changes);
        fixture.detectChanges();

        expect(debugElement.nativeElement.innerHtml).not.to.equal('');
        expect(debugElement.query(By.css('fa-icon'))).to.exist;
        expect(comp.missingTestCases).to.be.deep.equal(missingTestCases);
        expect(comp.invalidTestCases).to.be.deep.equal(invalidTestCases);
    });
});
