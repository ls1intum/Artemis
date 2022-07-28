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
    let fileService: FileService;

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
                fileService = TestBed.inject(FileService);
            });
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).not.toBeNull();
        expect(component.attachmentExtension(component.submission.filePath!)).toBe('N/A');
    });

    it('should correctly display the filepath', () => {
        component.submission.filePath = 'filePath.pdf';
        const downloadFileSpy = jest.spyOn(fileService, 'downloadFileWithAccessToken');
        fixture.detectChanges();
        expect(component.attachmentExtension(component.submission.filePath!)).toBe('pdf');
        const downloadFile = fixture.debugElement.query(By.css('#downloadFileButton'));
        expect(downloadFile).not.toBeNull();
        downloadFile.nativeElement.click();
        expect(downloadFileSpy).toHaveBeenCalledOnce();
    });
});
