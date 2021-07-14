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
import { routes } from 'app/exercises/modeling/assess/modeling-assessment-editor/modeling-assessment-editor.route';

const route = { params: of({ courseId: 3, exerciseId: 22 }) };
const course = { id: 1 };
const modelingExercise = {
    id: 22,
    course,
    type: ExerciseType.MODELING,
    studentAssignedTeamIdComputed: true,
    assessmentType: AssessmentType.SEMI_AUTOMATIC,
    numberOfAssessmentsOfCorrectionRounds: [],
    secondCorrectionEnabled: true,
};
const modelingExerciseOfExam = {
    id: 23,
    exerciseGroup: { id: 111, exam: { id: 112, course } },
    type: ExerciseType.MODELING,
    studentAssignedTeamIdComputed: true,
    assessmentType: AssessmentType.SEMI_AUTOMATIC,
    numberOfAssessmentsOfCorrectionRounds: [],
    secondCorrectionEnabled: true,
};
const modelingSubmission = { id: 1, submitted: true, results: [{ id: 10, assessor: { id: 20, guidedTourSettings: [] } }] };

describe('ModelingAssessmentDashboardComponent', () => {
    let component: ModelingAssessmentDashboardComponent;
    let fixture: ComponentFixture<ModelingAssessmentDashboardComponent>;
    let exerciseService: ExerciseService;
    let courseService: CourseManagementService;
    let modelingSubmissionService: ModelingSubmissionService;
    let modelingAssessmentService: ModelingAssessmentService;
    let sortService: SortService;
    let exerciseFindSpy: jasmine.Spy;
    let courseFindSpy: jasmine.Spy;

    beforeEach(async(() => {
        TestBed.configureTestingModule({
            imports: [RouterTestingModule.withRoutes([routes[2]]), TranslateModule.forRoot(), ArtemisTestModule],
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

        // call
        component.ngOnInit();

        // check
        expect(getSubmissionsSpy).toHaveBeenCalled();
        expect(registerChangeInResultsSpy).toHaveBeenCalled();
        expect(courseFindSpy).toHaveBeenCalled();
        expect(exerciseFindSpy).toHaveBeenCalled();
        expect(component.course).toEqual(course);
        expect(component.exercise).toEqual(modelingExercise as ModelingExercise);
    });

    it('should get Submissions', () => {
        // test getSubmissions
        const modelingSubmissionServiceSpy = spyOn(modelingSubmissionService, 'getModelingSubmissionsForExerciseByCorrectionRound').and.returnValue(
            of(new HttpResponse({ body: [modelingSubmission] })),
        );

        // call
        component.ngOnInit();

        // check
        expect(modelingSubmissionServiceSpy).toHaveBeenCalledWith(modelingExercise.id, { submittedOnly: true });
        expect(component.submissions).toEqual([modelingSubmission]);
        expect(component.filteredSubmissions).toEqual([modelingSubmission]);
    });

    it('should update filtered submissions', () => {
        // setup
        component.ngOnInit();
        component.updateFilteredSubmissions([modelingSubmission]);

        // check
        expect(component.filteredSubmissions).toEqual([modelingSubmission]);
    });

    it('should cancelAssessment', fakeAsync(() => {
        // test cancelAssessment
        const windowSpy = spyOn(window, 'confirm').and.returnValue(true);
        const getSubmissionsSpy = spyOn(component, 'getSubmissions');

        const modelAssServiceCancelAssSpy = spyOn(modelingAssessmentService, 'cancelAssessment').and.returnValue(of(1));

        // call
        component.cancelAssessment(modelingSubmission);
        tick();

        // check
        expect(modelAssServiceCancelAssSpy).toHaveBeenCalledWith(modelingSubmission.id);
        expect(windowSpy).toHaveBeenCalled();
        expect(getSubmissionsSpy).toHaveBeenCalled();
    }));

    it('should sortRows', () => {
        // test cancelAssessment
        const sortServiceSpy = spyOn(sortService, 'sortByProperty');
        component.filteredSubmissions = [modelingSubmission];
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
            const participationId = 2;
            component.exercise = modelingExercise;
            component.exerciseId = modelingExercise.id!;
            component.courseId = modelingExercise.course!.id!;
            expect(component.getAssessmentLink(participationId, submissionId)).toEqual([
                '/course-management',
                component.exercise.course!.id!.toString(),
                'modeling-exercises',
                component.exercise.id!.toString(),
                'submissions',
                submissionId.toString(),
                'assessment',
            ]);
        });

        it('should get assessment link for normal exercise', () => {
            const submissionId = 8;
            const participationId = 3;
            component.exercise = modelingExerciseOfExam;
            component.exerciseId = modelingExerciseOfExam.id!;
            component.courseId = modelingExerciseOfExam.exerciseGroup!.exam!.course!.id!;
            component.examId = modelingExerciseOfExam.exerciseGroup!.exam!.id!;
            component.exerciseGroupId = modelingExerciseOfExam.exerciseGroup!.id!;
            expect(component.getAssessmentLink(participationId, submissionId)).toEqual([
                '/course-management',
                component.exercise.exerciseGroup!.exam!.course!.id!.toString(),
                'exams',
                component.exercise.exerciseGroup!.exam!.id!.toString(),
                'exercise-groups',
                component.exercise.exerciseGroup!.id!.toString(),
                'modeling-exercises',
                component.exercise.id!.toString(),
                'submissions',
                submissionId.toString(),
                'assessment',
            ]);
        });
    });
});
