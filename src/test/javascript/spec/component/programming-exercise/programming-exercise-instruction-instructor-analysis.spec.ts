import { DebugElement, SimpleChange } from '@angular/core';
import { async, ComponentFixture, TestBed } from '@angular/core/testing';
import { AceEditorModule } from 'ng2-ace-editor';
import * as chai from 'chai';
import { ArtemisTestModule } from '../../test.module';
import { TranslateModule } from '@ngx-translate/core';
import { By } from '@angular/platform-browser';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { ProgrammingExerciseInstructionInstructorAnalysisComponent } from '../../../../../main/webapp/app/entities/programming-exercise';
import { TaskCommand } from '../../../../../main/webapp/app/markdown-editor/domainCommands/programming-exercise/task.command';
import { ExerciseHintService, IExerciseHintService } from 'app/entities/exercise-hint';
import { MockExerciseHintService } from '../../mocks';
import { SinonStub, stub } from 'sinon';
import { ExerciseHint } from 'app/entities/exercise-hint/exercise-hint.model';
import { Observable, of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { ProgrammingExerciseInstructionAnalysisService } from 'app/entities/programming-exercise/instructions/programming-exercise-instruction-analysis.service';

const expect = chai.expect;

describe('ProgrammingExerciseInstructionInstructionAnalysis', () => {
    let comp: ProgrammingExerciseInstructionInstructorAnalysisComponent;
    let fixture: ComponentFixture<ProgrammingExerciseInstructionInstructorAnalysisComponent>;
    let debugElement: DebugElement;

    let exerciseHintService: IExerciseHintService;

    let getHintsForExerciseStub: SinonStub;

    const taskCommand = new TaskCommand();
    const taskRegex = taskCommand.getTagRegex('g');
    const exerciseTestCases = ['test1', 'test2', 'test6', 'test7'];
    const problemStatement =
        '1. [task][SortStrategy Interface](test1,test2) \n 2. [task][SortStrategy Interface](test3) \n lorem ipsum \n lorem \n  3. [task][SortStrategy Interface](test2,test4)';
    const problemStatementTasks = ['[task][SortStrategy Interface](test1,test2)', '[task][SortStrategy Interface](test3)', '[task][SortStrategy Interface](test2,test4)'];
    const problemStatementTestCases = ['test1', 'test2', 'test3', 'test4'];

    const missingTestCases = ['test6', 'test7'];
    const invalidTestCases = ['test3', 'test4'];

    const exerciseHints = [{ id: 1 }, { id: 2 }] as ExerciseHint[];

    beforeEach(async(() => {
        TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArtemisTestModule, NgbModule],
            declarations: [ProgrammingExerciseInstructionInstructorAnalysisComponent],
            providers: [{ provide: ExerciseHintService, useClass: MockExerciseHintService }, ProgrammingExerciseInstructionAnalysisService],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ProgrammingExerciseInstructionInstructorAnalysisComponent);
                debugElement = fixture.debugElement;
                comp = fixture.componentInstance as ProgrammingExerciseInstructionInstructorAnalysisComponent;

                exerciseHintService = debugElement.injector.get(ExerciseHintService);

                getHintsForExerciseStub = stub(exerciseHintService, 'findByExerciseId').returns(of({ body: exerciseHints }) as Observable<HttpResponse<ExerciseHint[]>>);
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
        comp.exerciseHints = exerciseHints;
        const changes = { problemStatement: new SimpleChange(undefined, problemStatement, false), exerciseTestCases: new SimpleChange(undefined, exerciseTestCases, false) };
        comp.ngOnChanges(changes);
        fixture.detectChanges();

        expect(debugElement.nativeElement.innerHtml).not.to.equal('');
        expect(debugElement.query(By.css('fa-icon'))).to.exist;
        expect(comp.missingTestCases).to.be.deep.equal(missingTestCases);
        expect(comp.invalidTestCases).to.be.deep.equal(invalidTestCases);
    });
});
