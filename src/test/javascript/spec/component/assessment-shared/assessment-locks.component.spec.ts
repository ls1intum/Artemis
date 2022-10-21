import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { TranslateModule } from '@ngx-translate/core';
import { AssessmentLocksComponent } from 'app/assessment/assessment-locks/assessment-locks.component';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { FileUploadSubmission } from 'app/entities/file-upload-submission.model';
import { ModelingSubmission } from 'app/entities/modeling-submission.model';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { SubmissionExerciseType } from 'app/entities/submission.model';
import { TextSubmission } from 'app/entities/text-submission.model';
import { FileUploadAssessmentService } from 'app/exercises/file-upload/assess/file-upload-assessment.service';
import { ModelingAssessmentService } from 'app/exercises/modeling/assess/modeling-assessment.service';
import { ProgrammingAssessmentManualResultService } from 'app/exercises/programming/assess/manual-result/programming-assessment-manual-result.service';
import { TextAssessmentService } from 'app/exercises/text/assess/text-assessment.service';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockDirective, MockPipe } from 'ng-mocks';
import { of } from 'rxjs';
import { MockHasAnyAuthorityDirective } from '../../helpers/mocks/directive/mock-has-any-authority.directive';
import { MockRouterLinkDirective } from '../../helpers/mocks/directive/mock-router-link.directive';
import { ArtemisTestModule } from '../../test.module';

describe('AssessmentLocksComponent', () => {
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
    let windowConfirmStub: jest.SpyInstance<boolean, [message?: string | undefined]>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArtemisTestModule],
            declarations: [
                AssessmentLocksComponent,
                MockPipe(ArtemisTranslatePipe),
                MockRouterLinkDirective,
                MockHasAnyAuthorityDirective,
                MockDirective(NgbTooltip),
                MockPipe(ArtemisDatePipe),
            ],
            providers: [
                { provide: textAssessmentService, useClass: TextAssessmentService },
                { provide: programmingAssessmentService, useClass: ProgrammingAssessmentManualResultService },
                { provide: fileUploadAssessmentService, useClass: FileUploadAssessmentService },
                { provide: modelingAssessmentService, useClass: ModelingAssessmentService },
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
        fixture.detectChanges();
        windowConfirmStub = jest.spyOn(window, 'confirm').mockReturnValue(true);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should call getAllLockedSubmissions on init', () => {
        const courseServiceStub = jest.spyOn(courseService, 'findAllLockedSubmissionsOfCourse').mockReturnValue(of());
        component.ngOnInit();
        expect(courseServiceStub).toHaveBeenCalledOnce();
    });

    it('should release lock for programming exercise', () => {
        const cancelAssessmentStub = jest.spyOn(programmingAssessmentService, 'cancelAssessment').mockReturnValue(of());
        component.cancelAssessment(programmingSubmission);
        expect(windowConfirmStub).toHaveBeenCalledOnce();
        expect(cancelAssessmentStub).toHaveBeenCalledOnce();
    });

    it('should release lock for modeling exercise', () => {
        const cancelAssessmentStub = jest.spyOn(modelingAssessmentService, 'cancelAssessment').mockReturnValue(of());
        component.cancelAssessment(modelingSubmission);
        expect(windowConfirmStub).toHaveBeenCalledOnce();
        expect(cancelAssessmentStub).toHaveBeenCalledOnce();
    });

    it('should release lock for text exercise', () => {
        const cancelAssessmentStub = jest.spyOn(textAssessmentService, 'cancelAssessment').mockReturnValue(of());
        component.cancelAssessment(textSubmission);
        expect(windowConfirmStub).toHaveBeenCalledOnce();
        expect(cancelAssessmentStub).toHaveBeenCalledOnce();
    });

    it('should release lock for the file upload exercise', () => {
        const cancelAssessmentStub = jest.spyOn(fileUploadAssessmentService, 'cancelAssessment').mockReturnValue(of());
        component.cancelAssessment(fileUploadSubmission);
        expect(windowConfirmStub).toHaveBeenCalledOnce();
        expect(cancelAssessmentStub).toHaveBeenCalledOnce();
    });
});
