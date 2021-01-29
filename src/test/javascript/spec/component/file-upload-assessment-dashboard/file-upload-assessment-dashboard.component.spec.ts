import { async, ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { ArtemisTestModule } from '../../test.module';
import { ActivatedRoute, convertToParamMap, Params, Router } from '@angular/router';
import { of, Subscription } from 'rxjs';
import { RouterTestingModule } from '@angular/router/testing';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';
import { FileUploadAssessmentDashboardComponent } from 'app/exercises/file-upload/assess/file-upload-assessment-dashboard.component';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { ExerciseType } from 'app/entities/exercise.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { HttpResponse } from '@angular/common/http';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { SortService } from 'app/shared/service/sort.service';
import { FileUploadSubmissionService } from 'app/exercises/file-upload/participate/file-upload-submission.service';
import { FileUploadAssessmentsService } from 'app/exercises/file-upload/assess/file-upload-assessment.service';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';

const route = { params: of({ courseId: 3, exerciseId: 22 }) };
const course = { id: 1 };
let fileUploadExercise: FileUploadExercise;
fileUploadExercise = {
    id: 22,
    type: ExerciseType.FILE_UPLOAD,
} as FileUploadExercise;

const fileUploadSubmission1 = { id: 1, submitted: true, results: [{ id: 10, assessor: { id: 20, guidedTourSettings: [] } }] };
const fileUploadSubmission2 = { id: 2, submitted: true, results: [{ id: 20, assessor: { id: 30, guidedTourSettings: [] } }] };
const userId = 30;

describe('ModelingAssessmentDashboardComponent', () => {
    let component: FileUploadAssessmentDashboardComponent;
    let fixture: ComponentFixture<FileUploadAssessmentDashboardComponent>;
    let exerciseService: ExerciseService;
    let courseService: CourseManagementService;
    let fileUploadSubmissionService: FileUploadSubmissionService;
    let fileUploadAssessmentsService: FileUploadAssessmentsService;
    let sortService: SortService;
    let router: Router;
    let exerciseFindSpy: jasmine.Spy;
    let courseFindSpy: jasmine.Spy;

    beforeEach(async(() => {
        TestBed.configureTestingModule({
            imports: [RouterTestingModule, TranslateModule.forRoot(), ArtemisTestModule],
            declarations: [FileUploadAssessmentDashboardComponent],
            providers: [
                JhiLanguageHelper,
                { provide: Router, useClass: route },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: Router, useClass: MockRouter },
                { provide: AccountService, useClass: MockAccountService },
                {
                    provide: ActivatedRoute,
                    useValue: {
                        params: {
                            subscribe: (fn: (value: Params) => void) =>
                                fn({
                                    courseId: 1,
                                }),
                        },
                        snapshot: {
                            paramMap: convertToParamMap({
                                courseId: '1',
                                exerciseId: fileUploadExercise.id,
                            }),
                        },
                    },
                },
            ],
        })
            .overrideTemplate(FileUploadAssessmentDashboardComponent, '')
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(FileUploadAssessmentDashboardComponent);
                component = fixture.componentInstance;
                router = fixture.debugElement.injector.get(Router);
                exerciseService = fixture.debugElement.injector.get(ExerciseService);
                courseService = fixture.debugElement.injector.get(CourseManagementService);
                fileUploadSubmissionService = fixture.debugElement.injector.get(FileUploadSubmissionService);
                fileUploadAssessmentsService = fixture.debugElement.injector.get(FileUploadAssessmentsService);
                sortService = fixture.debugElement.injector.get(SortService);
                exerciseFindSpy = spyOn(exerciseService, 'find').and.returnValue(of(new HttpResponse({ body: fileUploadExercise })));
                courseFindSpy = spyOn(courseService, 'find').and.returnValue(of(new HttpResponse({ body: course })));
                // fixture.detectChanges();
            });
    }));

    // afterEach(() => {
    //     component.ngOnDestroy();
    // });

    it('should set parameters and call functions on init', fakeAsync(() => {
        // setup
        spyOn<any>(component, 'getSubmissions');
        spyOn<any>(component, 'setPermissions');
        // test for init values
        expect(component).toBeTruthy();
        expect(component.submissions).toEqual([]);
        expect(component.reverse).toEqual(false);
        expect(component.predicate).toEqual('id');
        expect(component.filteredSubmissions).toEqual([]);

        // call
        component.ngOnInit();
        tick();

        // check
        expect(component['getSubmissions']).toHaveBeenCalledWith(fileUploadExercise.id);
        expect(component['setPermissions']).toHaveBeenCalled();
        expect(courseFindSpy).toHaveBeenCalled();
        expect(exerciseFindSpy).toHaveBeenCalled();
        expect(component.exercise).toEqual(fileUploadExercise as FileUploadExercise);
    }));

    it('should get Submissions', () => {
        // test getSubmissions
        const fileUploadSubmissionServiceSpy = spyOn(fileUploadSubmissionService, 'getFileUploadSubmissionsForExerciseByCorrectionRound').and.returnValue(
            of(new HttpResponse({ body: [fileUploadSubmission1] })),
        );

        // call
        component.ngOnInit();

        // check
        expect(fileUploadSubmissionServiceSpy).toHaveBeenCalledWith(fileUploadExercise.id, { submittedOnly: true });
        expect(component.submissions).toEqual([fileUploadSubmission1]);
        expect(component.filteredSubmissions).toEqual([fileUploadSubmission1]);
    });

    it('should update filtered submissions', () => {
        // test updateFilteredSubmissions

        // setup
        component.ngOnInit();
        component.updateFilteredSubmissions([fileUploadSubmission1]);

        // check
        expect(component.filteredSubmissions).toEqual([fileUploadSubmission1]);
    });

    it('should cancelAssessment', fakeAsync(() => {
        // test cancelAssessment
        const windowSpy = spyOn(window, 'confirm').and.returnValue(true);
        spyOn<any>(component, 'getSubmissions');
        const modelAssServiceCancelAssSpy = spyOn(fileUploadAssessmentsService, 'cancelAssessment').and.returnValue(of(1));

        // call
        component.cancelAssessment(fileUploadSubmission1);
        tick();

        // check
        expect(modelAssServiceCancelAssSpy).toHaveBeenCalledWith(fileUploadSubmission1.id);
        expect(windowSpy).toHaveBeenCalled();
        expect(component['getSubmissions']).toHaveBeenCalled();
    }));

    it('should sortRows', () => {
        // test cancelAssessment
        const sortServiceSpy = spyOn(sortService, 'sortByProperty');
        component.predicate = 'predicate';
        component.reverse = false;

        component.sortRows();

        expect(sortServiceSpy).toHaveBeenCalledWith([fileUploadSubmission1], 'predicate', false);
    });
});
