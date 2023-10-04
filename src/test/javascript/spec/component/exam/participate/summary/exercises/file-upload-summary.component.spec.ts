import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FileUploadExamSummaryComponent } from 'app/exam/participate/summary/exercises/file-upload-exam-summary/file-upload-exam-summary.component';
import { MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { FileService } from 'app/shared/http/file.service';
import { FileUploadSubmission } from 'app/entities/file-upload-submission.model';
import { By } from '@angular/platform-browser';

describe('FileUploadExamSummaryComponent', () => {
    let fixture: ComponentFixture<FileUploadExamSummaryComponent>;
    let component: FileUploadExamSummaryComponent;

    const fileUploadSubmission = { id: 1 } as FileUploadSubmission;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            declarations: [FileUploadExamSummaryComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [MockProvider(FileService)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(FileUploadExamSummaryComponent);
                component = fixture.componentInstance;
                component.submission = fileUploadSubmission;
            });
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).not.toBeNull();
        expect(component.attachmentExtension(component.submission.filePath!)).toBe('N/A');
    });

    it('should render submission when exercise and submisssion is set', () => {
        const exercise = { id: 1234, studentParticipations: [{ id: 1 }] } as FileUploadExercise;
        const submission = { submitted: true, filePath: 'filePath.pdf' } as FileUploadSubmission;
        component.submission = submission;
        component.exercise = exercise;

        fixture.detectChanges();

        const fileUploadSubmissionComponent = fixture.debugElement.query(By.directive(FileUploadSubmissionComponent)).componentInstance;
        expect(fileUploadSubmissionComponent).toBeTruthy();
    });

    it('should not render submission if exercise and submission are not set', () => {
        fixture.detectChanges();

        const fileUploadSubmissionComponent = fixture.debugElement.query(By.directive(FileUploadSubmissionComponent))?.componentInstance;
        expect(fileUploadSubmissionComponent).not.toBeTruthy();
    });
});
