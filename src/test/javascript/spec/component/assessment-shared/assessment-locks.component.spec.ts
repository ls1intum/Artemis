import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { ArtemisTestModule } from '../../test.module';
import { AssessmentLocksComponent } from 'app/assessment/assessment-locks/assessment-locks.component';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockDirective, MockPipe } from 'ng-mocks';
import { MockRouterLinkDirective } from '../../helpers/mocks/directive/mock-router-link.directive';
import { MockHasAnyAuthorityDirective } from '../../helpers/mocks/directive/mock-has-any-authority.directive';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ModelingSubmission } from 'app/entities/modeling-submission.model';
import { FileUploadSubmission } from 'app/entities/file-upload-submission.model';
import { TextSubmission } from 'app/entities/text-submission.model';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { ModelingAssessmentService } from 'app/exercises/modeling/assess/modeling-assessment.service';
import { TextAssessmentService } from 'app/exercises/text/assess/text-assessment.service';
import { ProgrammingAssessmentManualResultService } from 'app/exercises/programming/assess/manual-result/programming-assessment-manual-result.service';
import { FileUploadAssessmentService } from 'app/exercises/file-upload/assess/file-upload-assessment.service';
import { SubmissionExerciseType } from 'app/entities/submission.model';
import { of } from 'rxjs';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';

describe('AssessmentLocksComponent', () => {
    let component: AssessmentLocksComponent;
    let fixture: ComponentFixture<AssessmentLocksComponent>;
    let modelingAssessmentService: ModelingAssessmentService;
    let textAssessmentService: TextAssessmentService;
    let programmingAssessmentService: ProgrammingAssessmentManualResultService;
    let fileUploadAssessmentService: FileUploadAssessmentService;

    const modelingSubmission = { id: 21, submissionExerciseType: SubmissionExerciseType.MODELING } as ModelingSubmission;
    const fileUploadSubmission = { id: 22, submissionExerciseType: SubmissionExerciseType.FILE_UPLOAD } as FileUploadSubmission;
    const textSubmission = { id: 23, participation: { exercise: { id: 1 }, id: 2 }, submissionExerciseType: SubmissionExerciseType.TEXT } as TextSubmission;
    const programmingSubmission = { id: 24, submissionExerciseType: SubmissionExerciseType.PROGRAMMING } as ProgrammingSubmission;

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
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
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
            });
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(AssessmentLocksComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });
    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should call getAllLockedSubmissions on init', () => {
        component.ngOnInit();
        expect(component.getAllLockedSubmissions).toHaveBeenCalled;
    });

    it('should release lock for programming exercise', () => {
        const cancelAssessmentStub = jest.spyOn(programmingAssessmentService, 'cancelAssessment').mockReturnValue(of());
        const windowConfirmStub = jest.spyOn(window, 'confirm').mockReturnValue(true);
        component.cancelAssessment(programmingSubmission);
        expect(windowConfirmStub).toBeCalled();
        expect(cancelAssessmentStub).toBeCalledTimes(1);
    });

    it('should release lock for modeling exercise', () => {
        const cancelAssessmentStub = jest.spyOn(modelingAssessmentService, 'cancelAssessment').mockReturnValue(of());
        const windowConfirmStub = jest.spyOn(window, 'confirm').mockReturnValue(true);
        component.cancelAssessment(modelingSubmission);
        expect(windowConfirmStub).toBeCalled();
        expect(cancelAssessmentStub).toBeCalledTimes(1);
    });

    it('should release lock for text exercise', () => {
        const cancelAssessmentStub = jest.spyOn(textAssessmentService, 'cancelAssessment').mockReturnValue(of());
        const windowConfirmStub = jest.spyOn(window, 'confirm').mockReturnValue(true);
        component.cancelAssessment(textSubmission);
        expect(windowConfirmStub).toBeCalled();
        expect(cancelAssessmentStub).toBeCalledTimes(1);
    });

    it('should release lock for the file upload exercise', () => {
        const cancelAssessmentStub = jest.spyOn(fileUploadAssessmentService, 'cancelAssessment').mockReturnValue(of());
        const windowConfirmStub = jest.spyOn(window, 'confirm').mockReturnValue(true);
        component.cancelAssessment(fileUploadSubmission);
        expect(windowConfirmStub).toBeCalled();
        expect(cancelAssessmentStub).toBeCalledTimes(1);
    });
});
