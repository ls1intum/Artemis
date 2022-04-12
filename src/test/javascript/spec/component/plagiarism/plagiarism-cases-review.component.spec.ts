import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { TranslatePipeMock } from '../../helpers/mocks/service/mock-translate.service';
import { MockComponent, MockDirective } from 'ng-mocks';
import { ArtemisTestModule } from '../../test.module';
import { PlagiarismCasesReviewComponent } from 'app/course/plagiarism-cases/plagiarism-cases-review.component';
import { PlagiarismSplitViewComponent } from 'app/exercises/shared/plagiarism/plagiarism-split-view/plagiarism-split-view.component';
import { ActivatedRoute } from '@angular/router';
import { PlagiarismCasesService, StatementEntityResponseType } from 'app/course/plagiarism-cases/shared/plagiarism-cases.service';
import { NgModel } from '@angular/forms';
import { MockRouterLinkDirective } from '../../helpers/mocks/directive/mock-router-link.directive';
import { PlagiarismStatus } from 'app/exercises/shared/plagiarism/types/PlagiarismStatus';
import { PlagiarismComparison } from 'app/exercises/shared/plagiarism/types/PlagiarismComparison';
import { TextSubmissionElement } from 'app/exercises/shared/plagiarism/types/text/TextSubmissionElement';
import { ModelingSubmissionElement } from 'app/exercises/shared/plagiarism/types/modeling/ModelingSubmissionElement';
import { ExerciseType } from 'app/entities/exercise.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { of } from 'rxjs';
import { PlagiarismCase } from 'app/exercises/shared/plagiarism/types/PlagiarismCase';

describe('Plagiarism Cases Review Component', () => {
    let comp: PlagiarismCasesReviewComponent;
    let fixture: ComponentFixture<PlagiarismCasesReviewComponent>;
    let plagiarismCasesService: PlagiarismCasesService;
    let getPlagiarismComparisonForStudentStub: jest.SpyInstance;
    let sendStatementStub: jest.SpyInstance;

    const studentLoginA = 'student1A';
    const studentLoginB = 'student1B';

    const instructorStatementA = 'instructorStatement for student 1A';
    const instructorStatementB = 'instructorStatement for student 1B';

    const studentStatementA = 'studentStatement A';
    const studentStatementB = 'studentStatement B';

    const plagiarismComparisonA = {
        id: 1,
        submissionA: { studentLogin: studentLoginA },
        instructorStatementA,
        statusA: PlagiarismStatus.CONFIRMED,
        studentStatementA,
        similarity: 0.5,
        status: PlagiarismStatus.CONFIRMED,
    } as PlagiarismComparison<TextSubmissionElement | ModelingSubmissionElement>;

    const plagiarismComparisonB = {
        id: 1,
        submissionB: { studentLogin: studentLoginB },
        instructorStatementB,
        statusB: PlagiarismStatus.CONFIRMED,
        studentStatementB,
        similarity: 0.5,
        status: PlagiarismStatus.CONFIRMED,
    } as PlagiarismComparison<TextSubmissionElement | ModelingSubmissionElement>;

    const textExercise = { id: 1, type: ExerciseType.TEXT } as TextExercise;

    const plagiarismCaseA = {
        exercise: textExercise,
        comparisons: [plagiarismComparisonA],
    } as PlagiarismCase;

    const plagiarismCaseB = {
        exercise: textExercise,
        comparisons: [plagiarismComparisonB],
    } as PlagiarismCase;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [TranslatePipeMock, PlagiarismCasesReviewComponent, MockRouterLinkDirective, MockDirective(NgModel), MockComponent(PlagiarismSplitViewComponent)],
            providers: [{ provide: ActivatedRoute, useValue: { params: of({ courseId: 1, plagiarismComparisonId: 1 }) } }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(PlagiarismCasesReviewComponent);
                comp = fixture.componentInstance;

                plagiarismCasesService = TestBed.inject(PlagiarismCasesService);
                getPlagiarismComparisonForStudentStub = jest.spyOn(plagiarismCasesService, 'getPlagiarismComparisonForStudent');
                sendStatementStub = jest.spyOn(plagiarismCasesService, 'saveStudentStatement').mockReturnValue(of({ body: '' } as StatementEntityResponseType));
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize for student A', fakeAsync(() => {
        getPlagiarismComparisonForStudentStub.mockReturnValue(of({ body: plagiarismCaseA }));

        comp.ngOnInit();
        tick();

        expect(comp.comparisonId).toBe(1);
        expect(getPlagiarismComparisonForStudentStub).toHaveBeenCalledWith(1, 1);
        expect(comp.exercise).toEqual(textExercise);
        expect(comp.isStudentA).toBe(true);
        expect(comp.instructorStatement).toBe(instructorStatementA);
        expect(comp.studentStatement).toBe(studentStatementA);
    }));

    it('should initialize for student B', fakeAsync(() => {
        getPlagiarismComparisonForStudentStub.mockReturnValue(of({ body: plagiarismCaseB }));

        comp.ngOnInit();
        tick();

        expect(comp.comparisonId).toBe(1);
        expect(getPlagiarismComparisonForStudentStub).toHaveBeenCalledWith(1, 1);
        expect(comp.exercise).toEqual(textExercise);
        expect(comp.isStudentA).toBe(false);
        expect(comp.instructorStatement).toBe(instructorStatementB);
        expect(comp.studentStatement).toBe(studentStatementB);
    }));

    it('should be able to save statement for student A', () => {
        comp.isStudentA = true;
        comp.comparison = Object.assign({}, plagiarismComparisonA);
        comp.comparison.studentStatementA = undefined;

        expect(comp.canSaveStudentStatement()).toBe(true);
    });

    it('should not be able to save statement for student A', () => {
        comp.isStudentA = true;
        comp.comparison = plagiarismComparisonA;

        expect(comp.canSaveStudentStatement()).toBe(false);
    });

    it('should be able to save statement for student B', () => {
        comp.isStudentA = false;
        comp.comparison = Object.assign({}, plagiarismComparisonB);
        comp.comparison.studentStatementB = undefined;

        expect(comp.canSaveStudentStatement()).toBe(true);
    });

    it('should not be able to save statement for student B', () => {
        comp.isStudentA = false;
        comp.comparison = plagiarismComparisonB;

        expect(comp.canSaveStudentStatement()).toBe(false);
    });

    it('should save statement for student A', fakeAsync(() => {
        comp.isStudentA = true;
        comp.comparison = plagiarismComparisonA;
        comp.comparisonId = 1;
        comp.studentStatement = 'statement test';
        comp.courseId = 1;

        comp.saveStudentStatement();
        tick();

        expect(comp.comparison.studentStatementA).toBe('statement test');
        expect(sendStatementStub).toHaveBeenCalledWith(1, 1, 'statement test');
    }));

    it('should save statement for student B', fakeAsync(() => {
        comp.isStudentA = false;
        comp.comparison = plagiarismComparisonB;
        comp.comparisonId = 2;
        comp.studentStatement = 'statement test';
        comp.courseId = 1;

        comp.saveStudentStatement();
        tick();

        expect(comp.comparison.studentStatementB).toBe('statement test');
        expect(sendStatementStub).toHaveBeenCalledWith(1, 2, 'statement test');
    }));

    it('should be confirmed for student A', () => {
        comp.isStudentA = true;
        comp.comparison = plagiarismComparisonA;

        expect(comp.isConfirmed()).toBe(true);
    });

    it('should not be confirmed status for student A', () => {
        comp.isStudentA = true;
        comp.comparison = Object.assign({}, plagiarismComparisonA);
        comp.comparison.statusA = PlagiarismStatus.DENIED;

        expect(comp.isConfirmed()).toBe(false);
    });

    it('should be confirmed for student B', () => {
        comp.isStudentA = false;
        comp.comparison = plagiarismComparisonB;

        expect(comp.isConfirmed()).toBe(true);
    });

    it('should not be confirmed for student B', () => {
        comp.isStudentA = false;
        comp.comparison = Object.assign({}, plagiarismComparisonB);
        comp.comparison.statusB = PlagiarismStatus.DENIED;

        expect(comp.isConfirmed()).toBe(false);
    });

    it('should have status for student A', () => {
        comp.isStudentA = true;
        comp.comparison = plagiarismComparisonA;

        expect(comp.hasStatus()).toBe(true);
    });

    it('should not have status for student A', () => {
        comp.isStudentA = true;
        comp.comparison = Object.assign({}, plagiarismComparisonA);
        comp.comparison.statusA = PlagiarismStatus.NONE;

        expect(comp.hasStatus()).toBe(false);
    });

    it('should have status for student B', () => {
        comp.isStudentA = false;
        comp.comparison = plagiarismComparisonB;

        expect(comp.hasStatus()).toBe(true);
    });

    it('should not have status for student B', () => {
        comp.isStudentA = false;
        comp.comparison = Object.assign({}, plagiarismComparisonB);
        comp.comparison.statusB = PlagiarismStatus.NONE;

        expect(comp.hasStatus()).toBe(false);
    });
});
