import { async, ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { ArtemisTestModule } from '../../test.module';
import { Router } from '@angular/router';
import { of, Subscription } from 'rxjs';
import { RouterTestingModule } from '@angular/router/testing';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';
import { ModelingAssessmentDashboardComponent } from 'app/exercises/modeling/assess/modeling-assessment-editor/modeling-assessment-dashboard.component';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { ExerciseType } from 'app/entities/exercise.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { HttpResponse } from '@angular/common/http';
import { ModelingSubmissionService } from 'app/exercises/modeling/participate/modeling-submission.service';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { ModelingAssessmentService } from 'app/exercises/modeling/assess/modeling-assessment.service';
import { SortService } from 'app/shared/service/sort.service';

const route = { params: of({ courseId: 3, exerciseId: 22 }) };
const course = { id: 1 };
const modelingExercise = {
    id: 22,
    course,
    type: ExerciseType.MODELING,
    studentAssignedTeamIdComputed: true,
    assessmentType: AssessmentType.SEMI_AUTOMATIC,
    numberOfAssessmentsOfCorrectionRounds: [],
};
const modelingExerciseOfExam = {
    id: 23,
    exerciseGroup: { id: 111, exam: { id: 112, course: course } },
    type: ExerciseType.MODELING,
    studentAssignedTeamIdComputed: true,
    assessmentType: AssessmentType.SEMI_AUTOMATIC,
    numberOfAssessmentsOfCorrectionRounds: [],
};
const modelingSubmission = { id: 1, submitted: true, results: [{ id: 10, assessor: { id: 20, guidedTourSettings: [] } }] };
const modelingSubmission2 = { id: 2, submitted: true, results: [{ id: 20, assessor: { id: 30, guidedTourSettings: [] } }] };
const userId = 30;

describe('ModelingAssessmentDashboardComponent', () => {
    let component: ModelingAssessmentDashboardComponent;
    let fixture: ComponentFixture<ModelingAssessmentDashboardComponent>;
    let exerciseService: ExerciseService;
    let courseService: CourseManagementService;
    let modelingSubmissionService: ModelingSubmissionService;
    let modelingAssessmentService: ModelingAssessmentService;
    let sortService: SortService;
    let router: Router;
    let exerciseFindSpy: jasmine.Spy;
    let courseFindSpy: jasmine.Spy;

    beforeEach(async(() => {
        TestBed.configureTestingModule({
            imports: [RouterTestingModule, TranslateModule.forRoot(), ArtemisTestModule],
            declarations: [ModelingAssessmentDashboardComponent],
            providers: [
                JhiLanguageHelper,
                { provide: Router, useClass: route },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: Router, useClass: MockRouter },
                { provide: AccountService, useClass: MockAccountService },
            ],
        })
            .overrideTemplate(ModelingAssessmentDashboardComponent, '')
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ModelingAssessmentDashboardComponent);
                component = fixture.componentInstance;
                router = fixture.debugElement.injector.get(Router);
                exerciseService = fixture.debugElement.injector.get(ExerciseService);
                courseService = fixture.debugElement.injector.get(CourseManagementService);
                modelingSubmissionService = fixture.debugElement.injector.get(ModelingSubmissionService);
                modelingAssessmentService = fixture.debugElement.injector.get(ModelingAssessmentService);
                sortService = fixture.debugElement.injector.get(SortService);
                exerciseFindSpy = spyOn(exerciseService, 'find').and.returnValue(of(new HttpResponse({ body: modelingExercise })));
                courseFindSpy = spyOn(courseService, 'find').and.returnValue(of(new HttpResponse({ body: course })));
                fixture.detectChanges();
            });
    }));

    afterEach(() => {
        component.ngOnDestroy();
    });

    it('should set parameters and call functions on init', () => {
        // setup
        const getSubmissionsSpy = spyOn(component, 'getSubmissions');
        const registerChangeInResultsSpy = spyOn(component, 'registerChangeInResults');

        // test for init values
        expect(component).toBeTruthy();
        expect(component.submissions).toEqual([]);
        expect(component.reverse).toEqual(false);
        expect(component.predicate).toEqual('id');
        expect(component.filteredSubmissions).toEqual([]);
        expect(component.optimalSubmissions).toEqual([]);
        expect(component.otherSubmissions).toEqual([]);

        // call
        component.ngOnInit();

        // check
        expect(getSubmissionsSpy).toHaveBeenCalledWith(true);
        expect(registerChangeInResultsSpy).toHaveBeenCalled();
        expect(courseFindSpy).toHaveBeenCalled();
        expect(exerciseFindSpy).toHaveBeenCalled();
        expect(component.course).toEqual(course);
        expect(component.modelingExercise).toEqual(modelingExercise as ModelingExercise);
    });

    it('should get Submissions', () => {
        // test getSubmissions
        const filterSubmissionsSpy = spyOn(component, 'filterSubmissions');
        const modelingSubmissionServiceSpy = spyOn(modelingSubmissionService, 'getModelingSubmissionsForExerciseByCorrectionRound').and.returnValue(
            of(new HttpResponse({ body: [modelingSubmission] })),
        );

        // call
        component.ngOnInit();

        // check
        expect(modelingSubmissionServiceSpy).toHaveBeenCalledWith(modelingExercise.id, { submittedOnly: true });
        expect(component.submissions).toEqual([modelingSubmission]);
        expect(component.filteredSubmissions).toEqual([modelingSubmission]);
        expect(filterSubmissionsSpy).toHaveBeenCalled();
    });

    it('should update filtered submissions', () => {
        // test updateFilteredSubmissions
        const applyFilter = spyOn(component, 'applyFilter');

        // setup
        component.ngOnInit();
        component.updateFilteredSubmissions([modelingSubmission]);

        // check
        expect(component.filteredSubmissions).toEqual([modelingSubmission]);
        expect(applyFilter).toHaveBeenCalled();
    });

    it('should refresh', () => {
        // test refresh
        const getSubmissionsSpy = spyOn(component, 'getSubmissions');

        component.refresh();

        expect(getSubmissionsSpy).toHaveBeenCalledWith(true);
    });

    describe('filter Submissions', () => {
        it('should filter Submissions', () => {
            // test filterSubmissions
            // setup
            const applyFilter = spyOn(component, 'applyFilter');
            const getOptimalSubmissionsSpy = spyOn(modelingAssessmentService, 'getOptimalSubmissions').and.returnValue(of([1]));
            component.modelingExercise = modelingExercise;
            component.nextOptimalSubmissionIds = [];

            // call
            component.filterSubmissions(true);

            // check
            expect(getOptimalSubmissionsSpy).toHaveBeenCalled();
            expect(component.nextOptimalSubmissionIds).toEqual([1]);
            expect(applyFilter).toHaveBeenCalled();
        });
        it('should not filter Submissions', () => {
            // test filterSubmissions
            // setup
            const applyFilter = spyOn(component, 'applyFilter');
            const getOptimalSubmissionsSpy = spyOn(modelingAssessmentService, 'getOptimalSubmissions').and.returnValue(of([1]));

            component.modelingExercise = modelingExercise;
            component.modelingExercise.assessmentType = AssessmentType.AUTOMATIC;
            component.nextOptimalSubmissionIds = [];

            // call
            component.filterSubmissions(true);

            // check
            expect(getOptimalSubmissionsSpy).not.toHaveBeenCalled();
            expect(component.nextOptimalSubmissionIds).toEqual([]);
            expect(applyFilter).toHaveBeenCalled();
        });
    });

    it('should apply filters ', () => {
        // test applyFilters
        // setup
        component.submissions = [modelingSubmission, modelingSubmission2];
        component.userId = userId;
        component.nextOptimalSubmissionIds = [1, 2];
        component.filteredSubmissions = component.submissions;
        // call
        component.applyFilter();

        // check
        expect(component.otherSubmissions.length).toBe(1);
        expect(component.optimalSubmissions.length).toBe(1);
        expect(component.optimalSubmissions[0].id).toEqual(modelingSubmission2.id);
        expect(component.otherSubmissions[0].id).toEqual(modelingSubmission.id);
    });
    describe('reset optimality', () => {
        it('should reset optimality', fakeAsync(() => {
            component.modelingExercise.assessmentType = AssessmentType.SEMI_AUTOMATIC;
            component.modelingExercise = modelingExercise;
            const serviceResetOptSpy = spyOn(modelingAssessmentService, 'resetOptimality').and.returnValue(of(1));
            const filterSubmissionsSpy = spyOn(component, 'filterSubmissions');

            // call
            component.resetOptimality();

            tick();

            expect(serviceResetOptSpy).toHaveBeenCalledWith(modelingExercise.id);
            expect(filterSubmissionsSpy).toHaveBeenCalledWith(true);
        }));

        it('should not reset optimality', () => {
            // setup
            component.modelingExercise.assessmentType = AssessmentType.AUTOMATIC;
            const filterSubmissionsSpy = spyOn(component, 'filterSubmissions');

            // call
            component.resetOptimality();

            // check
            expect(filterSubmissionsSpy).not.toHaveBeenCalled();
        });
    });

    describe('makeAllSubmissionsVisible', () => {
        it('should make all submissions visible ', () => {
            // setup
            component.busy = false;
            component.allSubmissionsVisible = false;
            // call
            component.makeAllSubmissionsVisible();
            // check
            expect(component.allSubmissionsVisible).toBe(true);
        });

        it('should not make all submissions visible ', () => {
            // setup
            component.busy = true;
            component.allSubmissionsVisible = false;
            // call
            component.makeAllSubmissionsVisible();
            // check
            expect(component.allSubmissionsVisible).toBe(false);
        });
    });

    describe('assessNextOptimal', () => {
        it('should assess next optimal submission', () => {
            // setup
            component.nextOptimalSubmissionIds = [];
            component.busy = true;
            const routerNavigateSpy = spyOn(router, 'navigate');
            const serviceResetOptSpy = spyOn(modelingAssessmentService, 'getOptimalSubmissions').and.returnValue(of([1]));
            const navigateToNextSpy = spyOn<any>(component, 'navigateToNextRandomOptimalSubmission').and.callThrough(); // <any> bc of private method
            component.modelingExercise = modelingExercise;

            // call
            component.assessNextOptimal();

            // check
            expect(serviceResetOptSpy).toHaveBeenCalledWith(modelingExercise.id);
            expect(component.busy).toBe(false);
            expect(navigateToNextSpy).toHaveBeenCalled();
            expect(routerNavigateSpy).toHaveBeenCalled();
        });

        it('should navigate to next random optimal submission', () => {
            // setup
            component.nextOptimalSubmissionIds = [1];
            component.busy = false;
            const routerNavigateSpy = spyOn(router, 'navigate');
            const navigateToNextSpy = spyOn<any>(component, 'navigateToNextRandomOptimalSubmission').and.callThrough(); // <any> bc of private method

            // call
            component.assessNextOptimal();

            // check
            expect(component.busy).toBe(true);
            expect(navigateToNextSpy).toHaveBeenCalled();
            expect(routerNavigateSpy).toHaveBeenCalled();
        });
    });

    it('should cancelAssessment', fakeAsync(() => {
        // test cancelAssessment
        const windowSpy = spyOn(window, 'confirm').and.returnValue(true);
        const refreshSpy = spyOn(component, 'refresh');

        const modelAssServiceCancelAssSpy = spyOn(modelingAssessmentService, 'cancelAssessment').and.returnValue(of(1));

        // call
        component.cancelAssessment(modelingSubmission);
        tick();

        // check
        expect(modelAssServiceCancelAssSpy).toHaveBeenCalledWith(modelingSubmission.id);
        expect(windowSpy).toHaveBeenCalled();
        expect(refreshSpy).toHaveBeenCalled();
    }));

    it('should sortRows', () => {
        // test cancelAssessment
        const sortServiceSpy = spyOn(sortService, 'sortByProperty');
        component.otherSubmissions = [modelingSubmission];
        component.predicate = 'predicate';
        component.reverse = false;

        component.sortRows();

        expect(sortServiceSpy).toHaveBeenCalledWith([modelingSubmission], 'predicate', false);
    });

    it('ngOnDestroy', () => {
        // setup
        component.paramSub = new Subscription();
        const paramSubSpy = spyOn(component.paramSub, 'unsubscribe');

        // call
        component.ngOnDestroy();

        // check
        expect(paramSubSpy).toHaveBeenCalled();
    });

    describe('shouldGetAssessmentLink', () => {
        it('should get assessment link for exam exercise', () => {
            const submissionId = 7;
            component.modelingExercise = modelingExercise;
            expect(component.getAssessmentLink(submissionId)).toEqual([
                '/course-management',
                component.modelingExercise.course?.id,
                'modeling-exercises',
                component.modelingExercise.id,
                'submissions',
                submissionId,
                'assessment',
            ]);
        });

        it('should get assessment link for normal exercise', () => {
            const submissionId = 8;
            component.modelingExercise = modelingExerciseOfExam;
            expect(component.getAssessmentLink(submissionId)).toEqual([
                '/course-management',
                component.modelingExercise.exerciseGroup?.exam?.course?.id,
                'modeling-exercises',
                component.modelingExercise.id,
                'submissions',
                submissionId,
                'assessment',
            ]);
        });
    });
});
