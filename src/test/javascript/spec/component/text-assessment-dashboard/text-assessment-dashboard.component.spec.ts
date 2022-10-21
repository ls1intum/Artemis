import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { ExerciseType } from 'app/entities/exercise.model';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { SortService } from 'app/shared/service/sort.service';
import { TextAssessmentDashboardComponent } from 'app/exercises/text/assess/text-assessment-dashboard/text-assessment-dashboard.component';
import { TextSubmissionService } from 'app/exercises/text/participate/text-submission.service';
import { TextAssessmentService } from 'app/exercises/text/assess/text-assessment.service';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { TranslatePipeMock } from '../../helpers/mocks/service/mock-translate.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';
import { HttpHeaders, HttpResponse } from '@angular/common/http';
import { BehaviorSubject, of } from 'rxjs';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { MockExerciseService } from '../../helpers/mocks/service/mock-exercise.service';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { AssessmentFiltersComponent } from 'app/assessment/assessment-filters/assessment-filters.component';
import { AssessmentWarningComponent } from 'app/assessment/assessment-warning/assessment-warning.component';
import { ResultComponent } from 'app/exercises/shared/result/result.component';
import { HasAnyAuthorityDirective } from 'app/shared/auth/has-any-authority.directive';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { SortDirective } from 'app/shared/sort/sort.directive';
import { MockQueryParamsDirective, MockRouterLinkDirective } from '../../helpers/mocks/directive/mock-router-link.directive';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import dayjs from 'dayjs/esm';
import { TextExercise } from 'app/entities/text-exercise.model';

const textExercise: TextExercise = {
    allowManualFeedbackRequests: false,
    id: 22,
    course: { id: 91 },
    type: ExerciseType.TEXT,
    studentAssignedTeamIdComputed: true,
    assessmentType: AssessmentType.SEMI_AUTOMATIC,
    numberOfAssessmentsOfCorrectionRounds: [],
    secondCorrectionEnabled: true,
};
const textExerciseOfExam: TextExercise = {
    allowManualFeedbackRequests: false,
    id: 23,
    exerciseGroup: { id: 111, exam: { id: 112, course: { id: 91 } } },
    type: ExerciseType.TEXT,
    studentAssignedTeamIdComputed: true,
    assessmentType: AssessmentType.SEMI_AUTOMATIC,
    numberOfAssessmentsOfCorrectionRounds: [],
    secondCorrectionEnabled: true,
};
const textSubmission = {
    id: 1,
    submitted: true,
    results: [{ id: 10, assessor: { id: 20, guidedTourSettings: [], internal: true }, assessmentType: AssessmentType.MANUAL, completionDate: dayjs(), rated: true }],
    participation: { id: 1 },
};

const unassessedSubmission = {
    id: 2,
    submitted: true,
    participation: { id: 2 },
};

const semiautomaticAssessedSubmission = {
    id: 3,
    submitted: true,
    results: [{ id: 30, assessor: { id: 20, guidedTourSettings: [], internal: true }, assessmentType: AssessmentType.SEMI_AUTOMATIC, completionDate: dayjs(), rated: true }],
    participation: { id: 1 },
};

describe('TextAssessmentDashboardComponent', () => {
    let component: TextAssessmentDashboardComponent;
    let fixture: ComponentFixture<TextAssessmentDashboardComponent>;
    let exerciseService: ExerciseService;
    let textSubmissionService: TextSubmissionService;
    let textAssessmentService: TextAssessmentService;
    let accountService: AccountService;
    let sortService: SortService;
    let route: ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                TextAssessmentDashboardComponent,
                MockComponent(AssessmentFiltersComponent),
                MockComponent(AssessmentWarningComponent),
                MockComponent(ResultComponent),
                MockDirective(HasAnyAuthorityDirective),
                MockDirective(TranslateDirective),
                MockDirective(SortDirective),
                MockRouterLinkDirective,
                MockQueryParamsDirective,
                TranslatePipeMock,
                MockPipe(ArtemisDatePipe),
            ],
            providers: [
                JhiLanguageHelper,
                { provide: AccountService, useClass: MockAccountService },
                { provide: ExerciseService, useClass: MockExerciseService },
                MockProvider(TextSubmissionService),
                MockProvider(TextAssessmentService),
                MockProvider(SortService),
                {
                    provide: ActivatedRoute,
                    useValue: {
                        snapshot: {
                            paramMap: convertToParamMap({
                                exerciseId: textExercise.id,
                                examId: 2,
                            }),
                        },
                        queryParams: new BehaviorSubject({ submissionFilter: 1 }),
                    },
                },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TextAssessmentDashboardComponent);
                component = fixture.componentInstance;
                exerciseService = TestBed.inject(ExerciseService);
                textSubmissionService = TestBed.inject(TextSubmissionService);
                textAssessmentService = TestBed.inject(TextAssessmentService);
                accountService = TestBed.inject(AccountService);
                sortService = TestBed.inject(SortService);
                route = TestBed.inject(ActivatedRoute);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should set parameters and call functions on init', () => {
        // setup
        jest.spyOn(exerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: textExercise })));
        const getTextSubmissionStub = jest
            .spyOn(textSubmissionService, 'getSubmissions')
            .mockReturnValue(of(new HttpResponse({ body: [textSubmission], headers: new HttpHeaders() })));

        // test for init values
        expect(component).toBeTruthy();
        expect(component.submissions).toEqual([]);
        expect(component.reverse).toBeFalse();
        expect(component.predicate).toBe('id');
        expect(component.filteredSubmissions).toEqual([]);

        // call
        component.ngOnInit();

        // check
        expect(getTextSubmissionStub).toHaveBeenCalledOnce();
        expect(getTextSubmissionStub).toHaveBeenCalledWith(textExercise.id, { submittedOnly: true });
        expect(component.exercise).toEqual(textExercise);
        expect(component.examId).toBe(2);
    });

    it('should get submissions', fakeAsync(() => {
        // test getSubmissions
        jest.spyOn(exerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: textExercise })));
        const getTextSubmissionStub = jest
            .spyOn(textSubmissionService, 'getSubmissions')
            .mockReturnValue(of(new HttpResponse({ body: [textSubmission], headers: new HttpHeaders() })));
        jest.spyOn(accountService, 'isAtLeastInstructorInCourse').mockReturnValue(true);

        // call
        component.ngOnInit();
        tick(100);
        // check
        expect(getTextSubmissionStub).toHaveBeenCalledOnce();
        expect(getTextSubmissionStub).toHaveBeenCalledWith(textExercise.id, { submittedOnly: true });
        expect(component.submissions).toEqual([textSubmission]);
        expect(component.filteredSubmissions).toEqual([textSubmission]);
    }));

    it('should not get Submissions', () => {
        const getTextSubmissionStub = jest.spyOn(textSubmissionService, 'getSubmissions').mockReturnValue(of(new HttpResponse({ body: [], headers: new HttpHeaders() })));
        jest.spyOn(accountService, 'isAtLeastInstructorInCourse').mockReturnValue(true);
        const findExerciseStub = jest.spyOn(exerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: textExercise, headers: new HttpHeaders() })));
        component.exercise = textExercise;
        // call
        component.ngOnInit();

        // check
        expect(findExerciseStub).toHaveBeenCalledOnce();
        expect(getTextSubmissionStub).toHaveBeenCalledWith(textExercise.id, { submittedOnly: true });
        expect(component.submissions).toEqual([]);
        expect(component.filteredSubmissions).toEqual([]);
    });

    it('should update filtered submissions', () => {
        // setup
        component.ngOnInit();
        component.applyChartFilter([textSubmission]);
        // check
        expect(component.filteredSubmissions).toEqual([textSubmission]);
    });

    it('should cancel assessment', fakeAsync(() => {
        // test cancelAssessment
        const windowSpy = jest.spyOn(window, 'confirm').mockReturnValue(true);
        const cancelAssessmentStub = jest.spyOn(textAssessmentService, 'cancelAssessment').mockReturnValue(of());
        component.exercise = textExercise;

        // call
        component.cancelAssessment(textSubmission);
        tick();

        // check
        expect(cancelAssessmentStub).toHaveBeenCalledOnce();
        expect(cancelAssessmentStub).toHaveBeenCalledWith(textSubmission.participation.id, textSubmission.id);
        expect(windowSpy).toHaveBeenCalledOnce();
    }));

    it('should sort rows', () => {
        // test cancelAssessment
        const sortServiceSpy = jest.spyOn(sortService, 'sortByProperty');
        component.predicate = 'predicate';
        component.reverse = false;
        component.submissions = [textSubmission];
        component.sortRows();

        expect(sortServiceSpy).toHaveBeenCalledOnce();
        expect(sortServiceSpy).toHaveBeenCalledWith([textSubmission], 'predicate', false);
    });

    it('should get the assessment type of a result', () => {
        const result = { id: 55, assessmentType: AssessmentType.SEMI_AUTOMATIC };
        expect(component.assessmentTypeTranslationKey(result)).toBe(`artemisApp.AssessmentType.${result.assessmentType}`);
        expect(component.assessmentTypeTranslationKey(undefined)).toBe(`artemisApp.AssessmentType.null`);
    });

    it('should get assessment link for normal exercise', () => {
        const submissionId = 7;
        const participationId = 2;
        component.exercise = textExercise;
        component.exerciseId = textExercise.id!;
        component.courseId = textExercise.course!.id!;
        expect(component.getAssessmentLink(participationId, submissionId)).toEqual([
            '/course-management',
            component.exercise.course!.id!.toString(),
            'text-exercises',
            component.exercise.id!.toString(),
            'participations',
            participationId.toString(),
            'submissions',
            submissionId.toString(),
            'assessment',
        ]);
    });

    it('should get assessment link for exam exercise', () => {
        const submissionId = 8;
        const participationId = 3;
        component.exercise = textExerciseOfExam;
        component.exerciseId = textExerciseOfExam.id!;
        component.courseId = textExerciseOfExam.exerciseGroup!.exam!.course!.id!;
        component.examId = textExerciseOfExam.exerciseGroup!.exam!.id!;
        component.exerciseGroupId = textExerciseOfExam.exerciseGroup!.id!;
        expect(component.getAssessmentLink(participationId, submissionId)).toEqual([
            '/course-management',
            component.exercise.exerciseGroup!.exam!.course!.id!.toString(),
            'exams',
            component.exercise.exerciseGroup!.exam!.id!.toString(),
            'exercise-groups',
            component.exercise.exerciseGroup!.id!.toString(),
            'text-exercises',
            component.exercise.id!.toString(),
            'participations',
            participationId.toString(),
            'submissions',
            submissionId.toString(),
            'assessment',
        ]);
    });

    it.each(['0', '1', '2'])(
        'should reset the chart filter',
        fakeAsync((filterOption: string) => {
            // test getSubmissions
            const body = [unassessedSubmission, textSubmission, semiautomaticAssessedSubmission];
            jest.spyOn(exerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: textExercise })));
            const getTextSubmissionStub = jest.spyOn(textSubmissionService, 'getSubmissions').mockReturnValue(of(new HttpResponse({ body, headers: new HttpHeaders() })));
            jest.spyOn(accountService, 'isAtLeastInstructorInCourse').mockReturnValue(true);
            route.queryParams = new BehaviorSubject({ filterOption });

            // call
            component.ngOnInit();
            tick(100);
            // check
            expect(component.filterOption).toBe(Number(filterOption));
            expect(getTextSubmissionStub).toHaveBeenCalledOnce();
            expect(getTextSubmissionStub).toHaveBeenCalledWith(textExercise.id, { submittedOnly: true });
            expect(component.submissions).toEqual(body);
            expect(component.filteredSubmissions).toEqual([body[Number(filterOption)]]);

            component.resetFilterOptions();

            expect(component.filteredSubmissions).toEqual(body);
        }),
    );
});
