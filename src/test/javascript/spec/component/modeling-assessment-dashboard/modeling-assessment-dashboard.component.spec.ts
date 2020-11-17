import { ComponentFixture, TestBed, tick, fakeAsync, async } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { ArtemisTestModule } from '../../test.module';
import { Router } from '@angular/router';
import { of } from 'rxjs';
import { RouterTestingModule } from '@angular/router/testing';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';
import { ModelingAssessmentDashboardComponent } from 'app/exercises/modeling/assess/modeling-assessment-editor/modeling-assessment-dashboard.component';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { TranslateService } from '@ngx-translate/core';
import { ExerciseType } from 'app/entities/exercise.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { HttpResponse } from '@angular/common/http';
import { ModelingSubmissionService } from 'app/exercises/modeling/participate/modeling-submission.service';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { ModelingAssessmentService } from 'app/exercises/modeling/assess/modeling-assessment.service';

const route = { params: of({ courseId: 3, exerciseId: 22 }) };
const course = { id: 1 };
const modelingExercise = { id: 22, course_id: course.id, type: ExerciseType.MODELING, studentAssignedTeamIdComputed: true, assessmentType: AssessmentType.SEMI_AUTOMATIC };
const modelingSubmission = { id: 1, submitted: true };

describe('ModelingAssessmentDashboardComponent', () => {
    let component: ModelingAssessmentDashboardComponent;
    let fixture: ComponentFixture<ModelingAssessmentDashboardComponent>;
    let accountService: AccountService;
    let exerciseService: ExerciseService;
    let courseService: CourseManagementService;
    let modelingSubmissionService: ModelingSubmissionService;
    let modelingAssessmentService: ModelingAssessmentService;
    let router: Router;
    let accountSpy: jasmine.Spy;
    let routerSpy: jasmine.Spy;
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
                accountService = fixture.debugElement.injector.get(AccountService);
                exerciseService = fixture.debugElement.injector.get(ExerciseService);
                courseService = fixture.debugElement.injector.get(CourseManagementService);
                modelingSubmissionService = fixture.debugElement.injector.get(ModelingSubmissionService);
                modelingAssessmentService = fixture.debugElement.injector.get(ModelingAssessmentService);
                accountSpy = spyOn(accountService, 'hasAnyAuthorityDirect').and.returnValue(true);
                routerSpy = spyOn(router, 'navigateByUrl');
                exerciseFindSpy = spyOn(exerciseService, 'find').and.returnValue(of(new HttpResponse({ body: modelingExercise })));
                courseFindSpy = spyOn(courseService, 'find').and.returnValue(of(new HttpResponse({ body: course })));
                fixture.detectChanges();
            });
    }));

    afterEach(() => {
        component.ngOnDestroy();
    });

    it('should set parameters and call functions on init', fakeAsync(() => {
        //setup
        const getSubmissionsSpy = spyOn(component, 'getSubmissions');
        const registerChangeInResultsSpy = spyOn(component, 'registerChangeInResults');

        //test for init values
        expect(component).toBeTruthy();
        expect(component.submissions).toEqual([]);
        expect(component.reverse).toEqual(false);
        expect(component.predicate).toEqual('id');
        expect(component.filteredSubmissions).toEqual([]);
        expect(component.optimalSubmissions).toEqual([]);
        expect(component.otherSubmissions).toEqual([]);

        //call
        component.ngOnInit();
        tick();

        //check
        expect(getSubmissionsSpy).toHaveBeenCalledWith(true);
        expect(registerChangeInResultsSpy).toHaveBeenCalled();
        expect(courseFindSpy).toHaveBeenCalled();
        expect(exerciseFindSpy).toHaveBeenCalled();
        expect(component.course).toEqual(course);
        expect(component.modelingExercise).toEqual(modelingExercise as ModelingExercise);
    }));

    it('should get Submissions', () => {
        //test getsubmissions
        const filterSubmissionsSpy = spyOn(component, 'filterSubmissions');
        const modelingSubmissionServiceSpy = spyOn(modelingSubmissionService, 'getModelingSubmissionsForExercise').and.returnValue(
            of(new HttpResponse({ body: [modelingSubmission] })),
        );

        //call
        component.ngOnInit();

        //check
        expect(modelingSubmissionServiceSpy).toHaveBeenCalledWith(modelingExercise.id, { submittedOnly: true });
        expect(component.submissions).toEqual([modelingSubmission]);
        expect(component.filteredSubmissions).toEqual([modelingSubmission]);
        expect(filterSubmissionsSpy).toHaveBeenCalled();
    });

    it('should update filtered submissions', () => {
        //test updateFilteredSubmissions
        const applyFilter = spyOn(component, 'applyFilter');

        //setup
        component.ngOnInit();
        component.updateFilteredSubmissions([modelingSubmission]);

        //check
        expect(component.filteredSubmissions).toEqual([modelingSubmission]);
        expect(applyFilter).toHaveBeenCalled();
    });

    it('should refresh', () => {
        //test refresh
        const getSubmissionsSpy = spyOn(component, 'getSubmissions');

        component.refresh();

        expect(getSubmissionsSpy).toHaveBeenCalledWith(true);
    });
});
