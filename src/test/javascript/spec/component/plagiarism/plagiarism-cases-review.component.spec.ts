import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { TranslatePipeMock } from '../../helpers/mocks/service/mock-translate.service';
import { MockComponent, MockDirective } from 'ng-mocks';
import { ArtemisTestModule } from '../../test.module';
import { PlagiarismCasesReviewComponent } from 'app/course/plagiarism-cases/plagiarism-cases-review.component';
import { PlagiarismSplitViewComponent } from 'app/exercises/shared/plagiarism/plagiarism-split-view/plagiarism-split-view.component';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { PlagiarismCasesService } from 'app/course/plagiarism-cases/plagiarism-cases.service';
import { NgModel } from '@angular/forms';
import { MockRouterLinkDirective } from '../admin/user-management.component.spec';
import { PlagiarismStatus } from 'app/exercises/shared/plagiarism/types/PlagiarismStatus';
import { PlagiarismComparison } from 'app/exercises/shared/plagiarism/types/PlagiarismComparison';
import { TextSubmissionElement } from 'app/exercises/shared/plagiarism/types/text/TextSubmissionElement';
import { ModelingSubmissionElement } from 'app/exercises/shared/plagiarism/types/modeling/ModelingSubmissionElement';
import { ExerciseType } from 'app/entities/exercise.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { PlagiarismCase } from 'app/course/plagiarism-cases/types/PlagiarismCase';
import { Notification } from 'app/entities/notification.model';
import { of } from 'rxjs';

describe('Plagiarism Cases Review', () => {
    let comp: PlagiarismCasesReviewComponent;
    let fixture: ComponentFixture<PlagiarismCasesReviewComponent>;
    let plagiarismCasesService: PlagiarismCasesService;
    let getAnonymousPlagiarismComparisonStub: jest.SpyInstance;
    let sendStatementStub: jest.SpyInstance;

    const studentLoginA = 'student1A';
    const studentLoginB = 'student1B';

    const notificationA = {
        text: 'notification for student 1A',
    } as Notification;
    const notificationB = {
        text: 'notification for student 1B',
    } as Notification;

    const statementA = 'statement A';
    const statementB = 'statement B';

    const plagiarismComparisonA = {
        id: 1,
        submissionA: { studentLogin: studentLoginA },
        notificationA,
        statusA: PlagiarismStatus.CONFIRMED,
        statementA,
        similarity: 0.5,
        status: PlagiarismStatus.CONFIRMED,
    } as PlagiarismComparison<TextSubmissionElement | ModelingSubmissionElement>;

    const plagiarismComparisonB = {
        id: 1,
        submissionB: { studentLogin: studentLoginB },
        notificationB,
        statusB: PlagiarismStatus.CONFIRMED,
        statementB,
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
            providers: [{ provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ plagiarismComparisonId: 1 }) } } }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(PlagiarismCasesReviewComponent);
                comp = fixture.componentInstance;

                plagiarismCasesService = TestBed.inject(PlagiarismCasesService);
                getAnonymousPlagiarismComparisonStub = jest.spyOn(plagiarismCasesService, 'getAnonymousPlagiarismComparison');
                sendStatementStub = jest.spyOn(plagiarismCasesService, 'sendStatement').mockReturnValue(of(''));
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize for student A', fakeAsync(() => {
        getAnonymousPlagiarismComparisonStub.mockReturnValue(of(plagiarismCaseA));

        comp.ngOnInit();
        tick();

        expect(comp.comparisonId).toBe(1);
        expect(getAnonymousPlagiarismComparisonStub).toHaveBeenCalledWith(1);
        expect(comp.exercise).toEqual(textExercise);
        expect(comp.isStudentA).toBe(true);
        expect(comp.instructorMessage).toEqual(notificationA.text);
        expect(comp.response).toEqual(statementA);
    }));

    it('should initialize for student B', fakeAsync(() => {
        getAnonymousPlagiarismComparisonStub.mockReturnValue(of(plagiarismCaseB));

        comp.ngOnInit();
        tick();

        expect(comp.comparisonId).toBe(1);
        expect(getAnonymousPlagiarismComparisonStub).toHaveBeenCalledWith(1);
        expect(comp.exercise).toEqual(textExercise);
        expect(comp.isStudentA).toBe(false);
        expect(comp.instructorMessage).toEqual(notificationB.text);
        expect(comp.response).toEqual(statementB);
    }));

    it('should be able to send statement for student A', () => {
        comp.isStudentA = true;
        comp.comparison = Object.assign({}, plagiarismComparisonA);
        comp.comparison.statementA = undefined;

        expect(comp.canSendStatement()).toBe(true);
    });

    it('should not be able to send statement for student A', () => {
        comp.isStudentA = true;
        comp.comparison = plagiarismComparisonA;

        expect(comp.canSendStatement()).toBe(false);
    });

    it('should be able to send statement for student B', () => {
        comp.isStudentA = false;
        comp.comparison = Object.assign({}, plagiarismComparisonB);
        comp.comparison.statementB = undefined;

        expect(comp.canSendStatement()).toBe(true);
    });

    it('should not be able to send statement for student B', () => {
        comp.isStudentA = false;
        comp.comparison = plagiarismComparisonB;

        expect(comp.canSendStatement()).toBe(false);
    });

    it('should send statement for student A', fakeAsync(() => {
        comp.isStudentA = true;
        comp.comparison = plagiarismComparisonA;
        comp.comparisonId = 1;
        comp.response = 'response';

        comp.sendStatement();
        tick();

        expect(comp.comparison.statementA).toEqual('response');
        expect(sendStatementStub).toHaveBeenCalledWith(1, 'response');
    }));

    it('should send statement for student B', fakeAsync(() => {
        comp.isStudentA = false;
        comp.comparison = plagiarismComparisonB;
        comp.comparisonId = 2;
        comp.response = 'response';

        comp.sendStatement();
        tick();

        expect(comp.comparison.statementB).toEqual('response');
        expect(sendStatementStub).toHaveBeenCalledWith(2, 'response');
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
