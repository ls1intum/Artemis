import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisTestModule } from '../../test.module';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { ExerciseType } from 'app/entities/exercise.model';
import { SortService } from 'app/shared/service/sort.service';
import { FileUploadAssessmentDashboardComponent } from 'app/exercises/file-upload/assess/file-upload-assessment-dashboard.component';
import { FileUploadSubmissionService } from 'app/exercises/file-upload/participate/file-upload-submission.service';
import { FileUploadAssessmentsService } from 'app/exercises/file-upload/assess/file-upload-assessment.service';

const course = { id: 1 };
const testRoute = ({ snapshot: { paramMap: convertToParamMap({ courseId: course.id, exerciseId: 22 }) } } as any) as ActivatedRoute;
const fileuploadExercise = { id: 22, course, type: ExerciseType.FILE_UPLOAD, studentAssignedTeamIdComputed: true };
const fileUploadSubmission = { id: 25, submitted: true, result: { id: 10, assessor: { id: 20, guidedTourSettings: [] } } };

describe('FileUploadAssessmentDashboardComponent', () => {
    let component: FileUploadAssessmentDashboardComponent;
    let fixture: ComponentFixture<FileUploadAssessmentDashboardComponent>;
    let fileUploadSubmissionService: FileUploadSubmissionService;
    let fileUploadAssessmentsService: FileUploadAssessmentsService;
    let sortService: SortService;
    let mockAuth: MockAccountService;

    beforeEach(fakeAsync(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [FileUploadAssessmentDashboardComponent],
            providers: [
                JhiLanguageHelper,
                { provide: ActivatedRoute, useValue: testRoute },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .overrideTemplate(FileUploadAssessmentDashboardComponent, '')
            .compileComponents()

            .then(() => {
                fixture = TestBed.createComponent(FileUploadAssessmentDashboardComponent);
                component = fixture.componentInstance;
                fileUploadAssessmentsService = TestBed.inject(FileUploadAssessmentsService);
                fileUploadSubmissionService = TestBed.inject(FileUploadSubmissionService);
                sortService = TestBed.inject(SortService);
                mockAuth = (fixture.debugElement.injector.get(AccountService) as any) as MockAccountService;
                mockAuth.hasAnyAuthorityDirect([]);
                mockAuth.identity();
                fixture.detectChanges();
            });
    }));

    describe('ngOnInit', () => {
        it('test ngOnInit', fakeAsync(() => {
            // setup
            const fileUpServicespy = spyOn(fileUploadSubmissionService, 'getFileUploadSubmissionsForExercise').and.returnValue({ body: { submission: [fileUploadSubmission] } });
            const getSubSpy = spyOn<any>(component, 'getSubmissions').and.callThrough();

            // call
            component.ngOnInit();
            tick();

            // check
            expect(component.submissions).toEqual([]);
            expect(getSubSpy).toHaveBeenCalled();
            expect(fileUpServicespy).toHaveBeenCalledWith(fileuploadExercise.id, { submittedOnly: true });
        }));
    });

    describe('updateFilteredSubmissions', () => {
        it('update filteSubmissions', () => {
            // setup
            component.filteredSubmissions = [];

            // call
            component.updateFilteredSubmissions([fileUploadSubmission]);

            // check
            expect(component.filteredSubmissions).toEqual([fileUploadSubmission]);
        });
    });

    describe('cancelAssessment', () => {
        it('test cancelAssessment', fakeAsync(() => {
            // setup
            const windowSpy = spyOn(window, 'confirm').and.returnValue(true);
            const refreshSpy = spyOn<any>(component, 'getSubmissions');
            const fileUploadServiceCancelAssSpy = spyOn(fileUploadAssessmentsService, 'cancelAssessment').and.returnValue(of(1));
            component.exercise = fileuploadExercise;

            // call
            component.cancelAssessment(fileUploadSubmission);
            tick();

            // check
            expect(fileUploadServiceCancelAssSpy).toHaveBeenCalledWith(fileUploadSubmission.id);
            expect(windowSpy).toHaveBeenCalled();
            expect(refreshSpy).toHaveBeenCalled();
        }));
    });

    it('should do sortRows', () => {
        // setup
        const sortBySpy = spyOn(sortService, 'sortByProperty');
        component.submissions = [];
        component.predicate = 'predicate';
        component.reverse = false;
        // call
        component.sortRows();
        // check
        expect(sortBySpy).toHaveBeenCalledWith([], 'predicate', false);
    });
});
