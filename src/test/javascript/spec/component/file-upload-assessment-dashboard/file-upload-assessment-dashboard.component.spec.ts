import { async, ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { ArtemisTestModule } from '../../test.module';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { of } from 'rxjs';
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
import { HttpHeaders, HttpResponse } from '@angular/common/http';
import { SortService } from 'app/shared/service/sort.service';
import { FileUploadSubmissionService } from 'app/exercises/file-upload/participate/file-upload-submission.service';
import { FileUploadAssessmentsService } from 'app/exercises/file-upload/assess/file-upload-assessment.service';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';
import { stub } from 'sinon';

const route = { params: of({ courseId: 3, exerciseId: 22 }) };
const fileUploadExercise1 = {
    id: 22,
    type: ExerciseType.FILE_UPLOAD,
    course: { id: 91 },
} as FileUploadExercise;
const fileUploadExercise2 = {
    id: 22,
    type: ExerciseType.FILE_UPLOAD,
    exerciseGroup: { id: 94, exam: { id: 777, course: { id: 92 } } },
} as FileUploadExercise;

const fileUploadSubmission1 = {
    id: 1,
    submitted: true,
    results: [{ id: 10, assessor: { id: 20, guidedTourSettings: [] } }],
    participation: { id: 41, exercise: fileUploadExercise1 },
};
const fileUploadSubmission2 = {
    id: 2,
    submitted: true,
    results: [{ id: 20, assessor: { id: 30, guidedTourSettings: [] } }],
    participation: { id: 41, exercise: fileUploadExercise2 },
};

describe('FileUploadAssessmentDashboardComponent', () => {
    let component: FileUploadAssessmentDashboardComponent;
    let fixture: ComponentFixture<FileUploadAssessmentDashboardComponent>;
    let exerciseService: ExerciseService;
    let fileUploadSubmissionService: FileUploadSubmissionService;
    let fileUploadAssessmentsService: FileUploadAssessmentsService;
    let accountService: AccountService;
    let sortService: SortService;

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
                        snapshot: {
                            paramMap: convertToParamMap({
                                exerciseId: fileUploadExercise2.id,
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
                exerciseService = fixture.debugElement.injector.get(ExerciseService);
                fileUploadSubmissionService = fixture.debugElement.injector.get(FileUploadSubmissionService);
                fileUploadAssessmentsService = fixture.debugElement.injector.get(FileUploadAssessmentsService);
                accountService = fixture.debugElement.injector.get(AccountService);
                sortService = fixture.debugElement.injector.get(SortService);
            });
    }));

    it('should set parameters and call functions on init', fakeAsync(() => {
        // setup
        const exerciseServiceFind = stub(exerciseService, 'find');
        exerciseServiceFind.returns(of(new HttpResponse({ body: fileUploadExercise1 })));
        const getFileUploadSubmissionStub = stub(fileUploadSubmissionService, 'getFileUploadSubmissionsForExerciseByCorrectionRound');
        getFileUploadSubmissionStub.returns(of(new HttpResponse({ body: [fileUploadSubmission1], headers: new HttpHeaders() })));
        spyOn<any>(component, 'setPermissions');
        // test for init values
        expect(component).toBeTruthy();
        expect(component.submissions).toEqual([]);
        expect(component.reverse).toEqual(false);
        expect(component.predicate).toEqual('id');
        expect(component.filteredSubmissions).toEqual([]);

        // call
        component.ngOnInit();
        tick(500);

        // check
        expect(getFileUploadSubmissionStub).toHaveBeenCalledWith(fileUploadExercise1.id);
        expect(component['setPermissions']).toHaveBeenCalled();
        expect(component.exercise).toEqual(fileUploadExercise1 as FileUploadExercise);
    }));

    it('should get Submissions', fakeAsync(() => {
        // test getSubmissions
        const exerciseServiceFind = stub(exerciseService, 'find');
        exerciseServiceFind.returns(of(new HttpResponse({ body: fileUploadExercise1 })));
        const getFileUploadSubmissionStub = stub(fileUploadSubmissionService, 'getFileUploadSubmissionsForExerciseByCorrectionRound');
        getFileUploadSubmissionStub.returns(of(new HttpResponse({ body: [fileUploadSubmission1], headers: new HttpHeaders() })));
        const isAtLeastInstructorInCourseStub = stub(accountService, 'isAtLeastInstructorInCourse');
        isAtLeastInstructorInCourseStub.returns(true);
        spyOn<any>(component, 'setPermissions');

        // call
        component.ngOnInit();
        tick(100);
        // check
        expect(component['setPermissions']).toHaveBeenCalled();
        expect(getFileUploadSubmissionStub).toHaveBeenCalledWith(fileUploadExercise1.id, { submittedOnly: true });
        expect(component.submissions).toEqual([fileUploadSubmission1]);
        expect(component.filteredSubmissions).toEqual([fileUploadSubmission1]);
    }));

    it('should not get Submissions', () => {
        const getFileUploadSubmissionStub = stub(fileUploadSubmissionService, 'getFileUploadSubmissionsForExerciseByCorrectionRound');
        getFileUploadSubmissionStub.returns(of(new HttpResponse({ body: [], headers: new HttpHeaders() })));
        const isAtLeastInstructorInCourseStub = stub(accountService, 'isAtLeastInstructorInCourse');
        isAtLeastInstructorInCourseStub.returns(true);
        const findExerciseStub = stub(exerciseService, 'find');
        findExerciseStub.returns(of(new HttpResponse({ body: fileUploadExercise2, headers: new HttpHeaders() })));
        const spy = spyOn<any>(component, 'getExercise');
        spy.and.callThrough();
        component.exercise = fileUploadExercise2;
        // call
        component.ngOnInit();

        // check
        expect(findExerciseStub).toHaveBeenCalled();
        expect(getFileUploadSubmissionStub).toHaveBeenCalledWith(fileUploadExercise2.id, { submittedOnly: true });
        expect(component.submissions).toEqual([]);
        expect(component.filteredSubmissions).toEqual([]);
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
        const modelAssServiceCancelAssSpy = spyOn(fileUploadAssessmentsService, 'cancelAssessment').and.returnValue(of(1));
        component.exercise = fileUploadExercise2;
        // call
        component.cancelAssessment(fileUploadSubmission2);
        tick();

        // check
        expect(modelAssServiceCancelAssSpy).toHaveBeenCalledWith(fileUploadSubmission2.id);
        expect(windowSpy).toHaveBeenCalled();
    }));

    it('should sortRows', () => {
        // test cancelAssessment
        const sortServiceSpy = spyOn(sortService, 'sortByProperty');
        component.predicate = 'predicate';
        component.reverse = false;
        component.submissions = [fileUploadSubmission2];
        component.sortRows();

        expect(sortServiceSpy).toHaveBeenCalledWith([fileUploadSubmission2], 'predicate', false);
    });
});
