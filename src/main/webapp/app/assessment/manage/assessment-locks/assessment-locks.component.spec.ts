import { MockInstance, afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TranslateModule } from '@ngx-translate/core';
import { AssessmentLocksComponent } from 'app/assessment/manage/assessment-locks/assessment-locks.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe, MockProvider } from 'ng-mocks';
import { MockRouterLinkDirective } from 'test/helpers/mocks/directive/mock-router-link.directive';
import { MockHasAnyAuthorityDirective } from 'test/helpers/mocks/directive/mock-has-any-authority.directive';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ModelingSubmission } from 'app/modeling/shared/entities/modeling-submission.model';
import { FileUploadSubmission } from 'app/fileupload/shared/entities/file-upload-submission.model';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { ProgrammingSubmission } from 'app/programming/shared/entities/programming-submission.model';
import { ModelingAssessmentService } from 'app/modeling/manage/assess/modeling-assessment.service';
import { TextAssessmentService } from 'app/text/manage/assess/service/text-assessment.service';
import { ProgrammingAssessmentManualResultService } from 'app/programming/manage/assess/manual-result/programming-assessment-manual-result.service';
import { FileUploadAssessmentService } from 'app/fileupload/manage/assess/file-upload-assessment.service';
import { Submission, SubmissionExerciseType } from 'app/exercise/shared/entities/submission/submission.model';
import { of } from 'rxjs';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { ActivatedRoute } from '@angular/router';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { HttpResponse, provideHttpClient } from '@angular/common/http';

describe('AssessmentLocksComponent', () => {
    setupTestBed({ zoneless: true });
    let component: AssessmentLocksComponent;
    let fixture: ComponentFixture<AssessmentLocksComponent>;
    let courseService: CourseManagementService;
    let modelingAssessmentService: ModelingAssessmentService;
    let textAssessmentService: TextAssessmentService;
    let programmingAssessmentService: ProgrammingAssessmentManualResultService;
    let fileUploadAssessmentService: FileUploadAssessmentService;

    const modelingSubmission = { id: 21, submissionExerciseType: SubmissionExerciseType.MODELING } as ModelingSubmission;
    const fileUploadSubmission = { id: 22, submissionExerciseType: SubmissionExerciseType.FILE_UPLOAD } as FileUploadSubmission;
    const textSubmission = { id: 23, participation: { exercise: { id: 1 }, id: 2 }, submissionExerciseType: SubmissionExerciseType.TEXT } as TextSubmission;
    const programmingSubmission = { id: 24, submissionExerciseType: SubmissionExerciseType.PROGRAMMING } as ProgrammingSubmission;
    let windowConfirmStub: MockInstance;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [
                TranslateModule.forRoot(),
                AssessmentLocksComponent,
                MockPipe(ArtemisTranslatePipe),
                MockRouterLinkDirective,
                MockHasAnyAuthorityDirective,
                MockPipe(ArtemisDatePipe),
            ],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                MockProvider(TextAssessmentService),
                MockProvider(CourseManagementService),
                MockProvider(ModelingAssessmentService),
                MockProvider(ProgrammingAssessmentManualResultService),
                MockProvider(FileUploadAssessmentService),
                MockProvider(ExamManagementService),
                {
                    provide: ActivatedRoute,
                    useValue: new MockActivatedRoute({ id: 123 }),
                },
            ],
        })
            .compileComponents()
            .then(() => {
                programmingAssessmentService = TestBed.inject(ProgrammingAssessmentManualResultService);
                textAssessmentService = TestBed.inject(TextAssessmentService);
                fileUploadAssessmentService = TestBed.inject(FileUploadAssessmentService);
                modelingAssessmentService = TestBed.inject(ModelingAssessmentService);
                courseService = TestBed.inject(CourseManagementService);
            });
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(AssessmentLocksComponent);
        component = fixture.componentInstance;
        // Mock findAllLockedSubmissionsOfCourse before detectChanges to prevent undefined subscribe error
        vi.spyOn(courseService, 'findAllLockedSubmissionsOfCourse').mockReturnValue(of(new HttpResponse<Submission[]>({ body: [] })));
        fixture.detectChanges();
        windowConfirmStub = vi.spyOn(window, 'confirm').mockReturnValue(true);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should call getAllLockedSubmissions on init', () => {
        // Note: findAllLockedSubmissionsOfCourse is already called once in beforeEach during detectChanges
        // We verify it was called by checking the spy set up in beforeEach
        expect(courseService.findAllLockedSubmissionsOfCourse).toHaveBeenCalled();
    });

    it('should release lock for programming exercise', () => {
        const cancelAssessmentStub = vi.spyOn(programmingAssessmentService, 'cancelAssessment').mockReturnValue(of());
        component.cancelAssessment(programmingSubmission);
        expect(windowConfirmStub).toHaveBeenCalledTimes(1);
        expect(cancelAssessmentStub).toHaveBeenCalledTimes(1);
    });

    it('should release lock for modeling exercise', () => {
        const cancelAssessmentStub = vi.spyOn(modelingAssessmentService, 'cancelAssessment').mockReturnValue(of());
        component.cancelAssessment(modelingSubmission);
        expect(windowConfirmStub).toHaveBeenCalledTimes(1);
        expect(cancelAssessmentStub).toHaveBeenCalledTimes(1);
    });

    it('should release lock for text exercise', () => {
        const cancelAssessmentStub = vi.spyOn(textAssessmentService, 'cancelAssessment').mockReturnValue(of());
        component.cancelAssessment(textSubmission);
        expect(windowConfirmStub).toHaveBeenCalledTimes(1);
        expect(cancelAssessmentStub).toHaveBeenCalledTimes(1);
    });

    it('should release lock for the file upload exercise', () => {
        const cancelAssessmentStub = vi.spyOn(fileUploadAssessmentService, 'cancelAssessment').mockReturnValue(of());
        component.cancelAssessment(fileUploadSubmission);
        expect(windowConfirmStub).toHaveBeenCalledTimes(1);
        expect(cancelAssessmentStub).toHaveBeenCalledTimes(1);
    });
});
