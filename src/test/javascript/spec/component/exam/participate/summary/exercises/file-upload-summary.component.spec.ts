import { ComponentFixture, TestBed } from '@angular/core/testing';
import * as chai from 'chai';
import sinonChai from 'sinon-chai';
import { FileUploadExamSummaryComponent } from 'app/exam/participate/summary/exercises/file-upload-exam-summary/file-upload-exam-summary.component';
import { MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { FileService } from 'app/shared/http/file.service';
import { FileUploadSubmission } from 'app/entities/file-upload-submission.model';
import { By } from '@angular/platform-browser';
import * as sinon from 'sinon';

chai.use(sinonChai);
const expect = chai.expect;

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
        expect(component).to.be.ok;
        expect(component.attachmentExtension(component.submission.filePath!)).to.equal('N/A');
    });

    it('should correctly display the filepath', () => {
        component.submission.filePath = 'filePath.pdf';
        const downloadFileSpy = sinon.spy(fileService, 'downloadFileWithAccessToken');
        fixture.detectChanges();
        expect(component.attachmentExtension(component.submission.filePath!)).to.equal('pdf');
        const downloadFile = fixture.debugElement.query(By.css('#downloadFileButton'));
        expect(downloadFile).to.exist;
        downloadFile.nativeElement.click();
        expect(downloadFileSpy).to.have.been.calledOnce;
    });
});
