import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisTestModule } from '../../test.module';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { BehaviorSubject, of } from 'rxjs';
import { RouterTestingModule } from '@angular/router/testing';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { ExerciseType } from 'app/entities/exercise.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { HttpHeaders, HttpResponse } from '@angular/common/http';
import { SortService } from 'app/shared/service/sort.service';
import { ProgrammingExerciseSubmissionsComponent } from 'app/exercises/programming/assess/programming-assessment-dashboard/programming-exercise-submissions.component';
import { ProgrammingSubmissionService } from 'app/exercises/programming/participate/programming-submission.service';
import { ProgrammingAssessmentManualResultService } from 'app/exercises/programming/assess/manual-result/programming-assessment-manual-result.service';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { AssessmentType } from 'app/entities/assessment-type.model';

const route = { params: of({ courseId: 3, exerciseId: 22 }) };
const programmingExercise1 = {
    id: 22,
    type: ExerciseType.PROGRAMMING,
    course: { id: 91 },
    numberOfAssessmentsOfCorrectionRounds: {},
} as ProgrammingExercise;
const programmingExercise2 = {
    id: 22,
    type: ExerciseType.PROGRAMMING,
    exerciseGroup: { id: 94, exam: { id: 777, course: { id: 92 } } },
    numberOfAssessmentsOfCorrectionRounds: {},
} as ProgrammingExercise;

const programmingSubmission1 = {
    id: 1,
    submitted: true,
    results: [{ id: 10, assessor: { id: 20, guidedTourSettings: [], internal: true } }],
    participation: { id: 41, exercise: programmingExercise1 },
};
const programmingSubmission2 = {
    id: 2,
    submitted: true,
    results: [{ id: 20, assessor: { id: 30, guidedTourSettings: [], internal: true } }],
    participation: { id: 41, exercise: programmingExercise2 },
};

describe('ProgrammingAssessmentDashboardComponent', () => {
    let component: ProgrammingExerciseSubmissionsComponent;
    let fixture: ComponentFixture<ProgrammingExerciseSubmissionsComponent>;
    let exerciseService: ExerciseService;
    let programmingSubmissionService: ProgrammingSubmissionService;
    let programmingAssessmentService: ProgrammingAssessmentManualResultService;
    let accountService: AccountService;
    let sortService: SortService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [RouterTestingModule, ArtemisTestModule],
            declarations: [ProgrammingExerciseSubmissionsComponent],
            providers: [
                JhiLanguageHelper,
                { provide: Router, useValue: route },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: Router, useClass: MockRouter },
                { provide: AccountService, useClass: MockAccountService },
                {
                    provide: ActivatedRoute,
                    useValue: {
                        snapshot: {
                            paramMap: convertToParamMap({
                                exerciseId: programmingExercise2.id,
                            }),
                        },
                        queryParams: new BehaviorSubject({}),
                    },
                },
            ],
        })
            .overrideTemplate(ProgrammingExerciseSubmissionsComponent, '')
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ProgrammingExerciseSubmissionsComponent);
                component = fixture.componentInstance;
                exerciseService = fixture.debugElement.injector.get(ExerciseService);
                programmingSubmissionService = fixture.debugElement.injector.get(ProgrammingSubmissionService);
                programmingAssessmentService = fixture.debugElement.injector.get(ProgrammingAssessmentManualResultService);
                accountService = fixture.debugElement.injector.get(AccountService);
                sortService = fixture.debugElement.injector.get(SortService);
            })
            .catch((e) => console.error(e));
    });

    it('should set parameters and call functions on init', fakeAsync(() => {
        // setup
        const exerciseServiceFindMock = jest.spyOn(exerciseService, 'find');
        exerciseServiceFindMock.mockReturnValue(of(new HttpResponse({ body: programmingExercise1 })));
        // test for init values
        expect(component).toBeTruthy();
        expect(component.submissions).toEqual([]);
        expect(component.reverse).toBeFalse();
        expect(component.predicate).toBe('id');
        expect(component.filteredSubmissions).toEqual([]);

        // call
        component.ngOnInit();
        tick(500);

        // check
        expect(exerciseServiceFindMock).toHaveBeenCalledWith(programmingExercise2.id);
        expect(component.exercise).toEqual(programmingExercise1 as ProgrammingExercise);
    }));

    it('should get Submissions', fakeAsync(() => {
        // test getSubmissions
        const exerciseServiceFindMock = jest.spyOn(exerciseService, 'find');
        const getProgrammingSubmissionsForExerciseByCorrectionRoundStub = jest.spyOn(programmingSubmissionService, 'getProgrammingSubmissionsForExerciseByCorrectionRound');
        const isAtLeastInstructorInCourseStub = jest.spyOn(accountService, 'isAtLeastInstructorInCourse');
        exerciseServiceFindMock.mockReturnValue(of(new HttpResponse({ body: programmingExercise1 })));
        getProgrammingSubmissionsForExerciseByCorrectionRoundStub.mockReturnValue(of(new HttpResponse({ body: [programmingSubmission1] })));
        isAtLeastInstructorInCourseStub.mockReturnValue(true);
        jest.spyOn<any, any>(component, 'getSubmissions');

        // call
        component.ngOnInit();
        tick(500);
        // check
        expect(component['getSubmissions']).toHaveBeenCalledOnce();
        expect(getProgrammingSubmissionsForExerciseByCorrectionRoundStub).toHaveBeenCalledOnce();
        expect(getProgrammingSubmissionsForExerciseByCorrectionRoundStub).toHaveBeenCalledWith(programmingExercise2.id, { submittedOnly: true });
        expect(exerciseServiceFindMock).toHaveBeenCalledWith(programmingExercise2.id);
        expect(component.submissions).toEqual([programmingSubmission1]);
        expect(component.filteredSubmissions).toEqual([programmingSubmission1]);
    }));

    it('should not get Submissions', fakeAsync(() => {
        const exerciseServiceFind = jest.spyOn(exerciseService, 'find');
        const getProgrammingSubmissionsForExerciseByCorrectionRoundStub = jest.spyOn(programmingSubmissionService, 'getProgrammingSubmissionsForExerciseByCorrectionRound');
        const isAtLeastInstructorInCourseStub = jest.spyOn(accountService, 'isAtLeastInstructorInCourse');

        exerciseServiceFind.mockReturnValue(of(new HttpResponse({ body: programmingExercise1 })));
        getProgrammingSubmissionsForExerciseByCorrectionRoundStub.mockReturnValue(of(new HttpResponse({ body: [] })));
        isAtLeastInstructorInCourseStub.mockReturnValue(true);
        // findExerciseStub.mockReturnValue(of(new HttpResponse({ body: fileUploadExercise, headers: new HttpHeaders() })));
        exerciseServiceFind.mockReturnValue(of(new HttpResponse({ body: programmingExercise2, headers: new HttpHeaders() })));
        jest.spyOn<any, any>(component, 'getSubmissions');
        component.exercise = programmingExercise2;

        // call
        component.ngOnInit();

        tick(100);
        // check
        expect(component['getSubmissions']).toHaveBeenCalledOnce();
        expect(exerciseServiceFind).toHaveBeenCalledWith(programmingExercise2.id);
        expect(component.submissions).toEqual([]);
        expect(component.filteredSubmissions).toEqual([]);
    }));

    it('should update filtered submissions', () => {
        // test updateFilteredSubmissions

        // setup
        component.ngOnInit();
        component.applyChartFilter([programmingSubmission1]);
        // check
        expect(component.filteredSubmissions).toEqual([programmingSubmission1]);
    });

    it('should cancelAssessment', fakeAsync(() => {
        // test cancelAssessment
        const windowSpy = jest.spyOn(window, 'confirm').mockReturnValue(true);
        const modelAssServiceCancelAssSpy = jest.spyOn(programmingAssessmentService, 'cancelAssessment').mockReturnValue(of(undefined));
        component.exercise = programmingExercise2;
        // call
        component.cancelAssessment(programmingSubmission2);
        tick();

        // check
        expect(modelAssServiceCancelAssSpy).toHaveBeenCalledWith(programmingSubmission2.id);
        expect(windowSpy).toHaveBeenCalledOnce();
    }));

    it('should sortRows', () => {
        // test cancelAssessment
        const sortServiceSpy = jest.spyOn(sortService, 'sortByProperty');
        component.predicate = 'predicate';
        component.reverse = false;
        component.submissions = [programmingSubmission2];
        component.sortRows();

        expect(sortServiceSpy).toHaveBeenCalledWith([programmingSubmission2], 'predicate', false);
    });

    it('should assessmentTypeTranslationKey', () => {
        const result = { id: 55, assessmentType: AssessmentType.SEMI_AUTOMATIC };
        expect(component.assessmentTypeTranslationKey(result)).toBe(`artemisApp.AssessmentType.${result.assessmentType}`);
        expect(component.assessmentTypeTranslationKey(undefined)).toBe(`artemisApp.AssessmentType.null`);
    });

    describe('shouldGetAssessmentLink', () => {
        it('should get assessment link for exam exercise', () => {
            const submissionId = 8;
            const participationId = 2;
            component.exercise = programmingExercise1;
            component.exerciseId = programmingExercise1.id!;
            component.courseId = programmingExercise1.course!.id!;
            expect(component.getAssessmentLink(participationId, submissionId)).toEqual([
                '/course-management',
                component.exercise.course!.id!.toString(),
                'programming-exercises',
                component.exercise.id!.toString(),
                'submissions',
                submissionId.toString(),
                'assessment',
            ]);
        });

        it('should get assessment link for normal exercise', () => {
            const submissionId = 9;
            const participationId = 2;
            component.exercise = programmingExercise2;
            component.exerciseId = programmingExercise2.id!;
            component.courseId = programmingExercise2.exerciseGroup!.exam!.course!.id!;
            component.examId = programmingExercise2.exerciseGroup!.exam!.id!;
            component.exerciseGroupId = programmingExercise2.exerciseGroup!.id!;
            expect(component.getAssessmentLink(participationId, submissionId)).toEqual([
                '/course-management',
                component.exercise.exerciseGroup!.exam!.course!.id!.toString(),
                'exams',
                component.exercise.exerciseGroup!.exam!.id!.toString(),
                'exercise-groups',
                component.exercise.exerciseGroup!.id!.toString(),
                'programming-exercises',
                component.exercise.id!.toString(),
                'submissions',
                submissionId.toString(),
                'assessment',
            ]);
        });
    });
});
