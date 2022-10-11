import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { BehaviorSubject, of } from 'rxjs';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';
import { FileUploadAssessmentDashboardComponent } from 'app/exercises/file-upload/assess/file-upload-assessment-dashboard.component';
import { TranslatePipeMock } from '../../helpers/mocks/service/mock-translate.service';
import { ExerciseType } from 'app/entities/exercise.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { HttpHeaders, HttpResponse } from '@angular/common/http';
import { SortService } from 'app/shared/service/sort.service';
import { FileUploadSubmissionService } from 'app/exercises/file-upload/participate/file-upload-submission.service';
import { FileUploadAssessmentService } from 'app/exercises/file-upload/assess/file-upload-assessment.service';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';
import { MockExerciseService } from '../../helpers/mocks/service/mock-exercise.service';
import { SortDirective } from 'app/shared/sort/sort.directive';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { AssessmentFiltersComponent } from 'app/assessment/assessment-filters/assessment-filters.component';
import { AssessmentWarningComponent } from 'app/assessment/assessment-warning/assessment-warning.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ResultComponent } from 'app/exercises/shared/result/result.component';
import { MockQueryParamsDirective, MockRouterLinkDirective } from '../../helpers/mocks/directive/mock-router-link.directive';
import { HasAnyAuthorityDirective } from 'app/shared/auth/has-any-authority.directive';
import { TranslateDirective } from 'app/shared/language/translate.directive';

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
    results: [{ id: 10, assessor: { id: 20, guidedTourSettings: [], internal: true } }],
    participation: { id: 41, exercise: fileUploadExercise1 },
};
const fileUploadSubmission2 = {
    id: 2,
    submitted: true,
    results: [{ id: 20, assessor: { id: 30, guidedTourSettings: [], internal: true } }],
    participation: { id: 41, exercise: fileUploadExercise2 },
};

describe('FileUploadAssessmentDashboardComponent', () => {
    let component: FileUploadAssessmentDashboardComponent;
    let fixture: ComponentFixture<FileUploadAssessmentDashboardComponent>;
    let exerciseService: ExerciseService;
    let fileUploadSubmissionService: FileUploadSubmissionService;
    let fileUploadAssessmentService: FileUploadAssessmentService;
    let accountService: AccountService;
    let sortService: SortService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                FileUploadAssessmentDashboardComponent,
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
                { provide: AccountService, useClass: MockAccountService },
                { provide: ExerciseService, useClass: MockExerciseService },
                MockProvider(FileUploadSubmissionService),
                MockProvider(FileUploadAssessmentService),
                MockProvider(SortService),
                {
                    provide: ActivatedRoute,
                    useValue: {
                        snapshot: {
                            paramMap: convertToParamMap({
                                exerciseId: fileUploadExercise2.id,
                            }),
                        },
                        queryParams: new BehaviorSubject({}),
                    },
                },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(FileUploadAssessmentDashboardComponent);
                component = fixture.componentInstance;
                exerciseService = fixture.debugElement.injector.get(ExerciseService);
                fileUploadSubmissionService = fixture.debugElement.injector.get(FileUploadSubmissionService);
                fileUploadAssessmentService = fixture.debugElement.injector.get(FileUploadAssessmentService);
                accountService = fixture.debugElement.injector.get(AccountService);
                sortService = fixture.debugElement.injector.get(SortService);
            });
    });

    it('should set parameters and call functions on init', fakeAsync(() => {
        // setup
        const exerciseServiceFindMock = jest.spyOn(exerciseService, 'find');
        exerciseServiceFindMock.mockReturnValue(of(new HttpResponse({ body: fileUploadExercise1 })));
        const getFileUploadSubmissionStub = jest.spyOn(fileUploadSubmissionService, 'getSubmissions');
        getFileUploadSubmissionStub.mockReturnValue(of(new HttpResponse({ body: [fileUploadSubmission1], headers: new HttpHeaders() })));
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
        expect(getFileUploadSubmissionStub).toHaveBeenCalledWith(fileUploadExercise2.id, { submittedOnly: true });
        expect(component.exercise).toEqual(fileUploadExercise1 as FileUploadExercise);
    }));

    it('should get Submissions', fakeAsync(() => {
        // test getSubmissions
        const exerciseServiceFind = jest.spyOn(exerciseService, 'find');
        exerciseServiceFind.mockReturnValue(of(new HttpResponse({ body: fileUploadExercise1 })));
        const getFileUploadSubmissionStub = jest.spyOn(fileUploadSubmissionService, 'getSubmissions');
        getFileUploadSubmissionStub.mockReturnValue(of(new HttpResponse({ body: [fileUploadSubmission1], headers: new HttpHeaders() })));
        const isAtLeastInstructorInCourseStub = jest.spyOn(accountService, 'isAtLeastInstructorInCourse');
        isAtLeastInstructorInCourseStub.mockReturnValue(true);

        // call
        component.ngOnInit();
        tick(100);
        // check
        expect(getFileUploadSubmissionStub).toHaveBeenCalledWith(fileUploadExercise1.id, { submittedOnly: true });
        expect(component.submissions).toEqual([fileUploadSubmission1]);
        expect(component.filteredSubmissions).toEqual([fileUploadSubmission1]);
    }));

    it('should not get Submissions', () => {
        const getFileUploadSubmissionStub = jest.spyOn(fileUploadSubmissionService, 'getSubmissions');
        getFileUploadSubmissionStub.mockReturnValue(of(new HttpResponse({ body: [], headers: new HttpHeaders() })));
        const isAtLeastInstructorInCourseStub = jest.spyOn(accountService, 'isAtLeastInstructorInCourse');
        isAtLeastInstructorInCourseStub.mockReturnValue(true);
        const findExerciseStub = jest.spyOn(exerciseService, 'find');
        findExerciseStub.mockReturnValue(of(new HttpResponse({ body: fileUploadExercise2, headers: new HttpHeaders() })));
        component.exercise = fileUploadExercise2;
        // call
        component.ngOnInit();

        // check
        expect(findExerciseStub).toHaveBeenCalledOnce();
        expect(getFileUploadSubmissionStub).toHaveBeenCalledWith(fileUploadExercise2.id, { submittedOnly: true });
        expect(component.submissions).toEqual([]);
        expect(component.filteredSubmissions).toEqual([]);
    });

    it('should update filtered submissions', () => {
        // test updateFilteredSubmissions

        // setup
        component.ngOnInit();
        component.applyChartFilter([fileUploadSubmission1]);
        // check
        expect(component.filteredSubmissions).toEqual([fileUploadSubmission1]);
    });

    it('should cancelAssessment', fakeAsync(() => {
        // test cancelAssessment
        const windowSpy = jest.spyOn(window, 'confirm').mockReturnValue(true);
        const modelAssServiceCancelAssSpy = jest.spyOn(fileUploadAssessmentService, 'cancelAssessment').mockReturnValue(of());
        component.exercise = fileUploadExercise2;
        // call
        component.cancelAssessment(fileUploadSubmission2);
        tick();

        // check
        expect(modelAssServiceCancelAssSpy).toHaveBeenCalledWith(fileUploadSubmission2.id);
        expect(windowSpy).toHaveBeenCalledOnce();
    }));

    it('should sortRows', () => {
        // test cancelAssessment
        const sortServiceSpy = jest.spyOn(sortService, 'sortByProperty');
        component.predicate = 'predicate';
        component.reverse = false;
        component.submissions = [fileUploadSubmission2];
        component.sortRows();

        expect(sortServiceSpy).toHaveBeenCalledWith([fileUploadSubmission2], 'predicate', false);
    });
});
