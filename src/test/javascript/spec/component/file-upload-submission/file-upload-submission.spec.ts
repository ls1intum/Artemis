import { ComponentFixture, fakeAsync, flush, TestBed, tick } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { AccountService, JhiLanguageHelper } from 'app/core';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ArtemisTestModule } from '../../test.module';
import { MockParticipationWebsocketService, MockSyncStorage } from '../../mocks';
import { ArtemisResultModule, Result } from 'app/entities/result';
import { MockComponent } from 'ng-mocks';
import { ArtemisSharedModule, FileUploaderService } from 'app/shared';
import { MockAlertService } from '../../helpers/mock-alert.service';
import { JhiAlertService } from 'ng-jhipster';
import { Router } from '@angular/router';
import { ResizableInstructionsComponent } from 'app/text-assessment/resizable-instructions/resizable-instructions.component';
import { ComplaintsForTutorComponent } from 'app/complaints-for-tutor';
import { DebugElement } from '@angular/core';
import { By } from '@angular/platform-browser';
import { MockAccountService } from '../../mocks/mock-account.service';
import { Location } from '@angular/common';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { ComplaintService } from 'app/entities/complaint/complaint.service';
import { MockComplaintService } from '../../mocks/mock-complaint.service';
import { NgxDatatableModule } from '@swimlane/ngx-datatable';
import { fileUploadSubmissionRoute } from 'app/file-upload-submission/file-upload-submission.route';
import { FileUploadSubmissionComponent } from 'app/file-upload-submission/file-upload-submission.component';
import { MomentModule } from 'ngx-moment';
import { ArtemisComplaintsModule } from 'app/complaints';
import { FileUploadSubmission, FileUploadSubmissionService } from 'app/entities/file-upload-submission';
import { fileUploadSubmission, MockFileUploadSubmissionService } from '../../mocks/mock-file-upload-submission.service';
import { ParticipationWebsocketService } from 'app/entities/participation';
import { fileUploadExercise } from '../../mocks/mock-file-upload-exercise.service';
import { MAX_SUBMISSION_FILE_SIZE } from 'app/shared/constants/input.constants';

chai.use(sinonChai);
const expect = chai.expect;

describe('FileUploadSubmissionComponent', () => {
    let comp: FileUploadSubmissionComponent;
    let fixture: ComponentFixture<FileUploadSubmissionComponent>;
    let debugElement: DebugElement;
    let router: Router;
    let location: Location;
    let fileUploaderService: FileUploaderService;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [
                ArtemisTestModule,
                NgxDatatableModule,
                ArtemisResultModule,
                ArtemisSharedModule,
                MomentModule,
                ArtemisComplaintsModule,
                RouterTestingModule.withRoutes([fileUploadSubmissionRoute[0]]),
            ],
            declarations: [FileUploadSubmissionComponent, MockComponent(ResizableInstructionsComponent), MockComponent(ComplaintsForTutorComponent)],
            providers: [
                JhiLanguageHelper,
                { provide: JhiAlertService, useClass: MockAlertService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: ComplaintService, useClass: MockComplaintService },
                { provide: FileUploadSubmissionService, useClass: MockFileUploadSubmissionService },
                { provide: ParticipationWebsocketService, useClass: MockParticipationWebsocketService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(FileUploadSubmissionComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;
                router = debugElement.injector.get(Router);
                location = debugElement.injector.get(Location);
                router.initialNavigation();
                comp.ngOnInit();
                fixture.detectChanges();
                fileUploaderService = TestBed.get(FileUploaderService);
            });
    });

    afterEach(fakeAsync(() => {
        fixture.destroy();
        flush();
    }));

    it('File Upload Submission is correctly initialized from service', fakeAsync(() => {
        // check if properties where assigned correctly on init
        expect(comp.acceptedFileExtensions.replace(/\./g, '')).to.be.equal(fileUploadExercise.filePattern);
        expect(comp.fileUploadExercise).to.be.equal(fileUploadExercise);
        expect(comp.isAfterAssessmentDueDate).to.be.true;
        expect(comp.numberOfAllowedComplaints).to.be.undefined;
        expect(comp.submission).to.be.undefined;

        const maxScore = debugElement.query(By.css('div p strong'));
        expect(maxScore).to.exist;
        expect(maxScore.nativeElement.textContent).to.be.equal(`Max. Score: ${fileUploadExercise.maxScore}`);

        // check if fileUploadInput is available
        const fileUploadInput = debugElement.query(By.css('#fileUploadInput'));
        expect(fileUploadInput).to.exist;
        expect(fileUploadInput.nativeElement.disabled).to.be.false;

        // check if fileUploadLabel value is not set
        const fileUploadLabel = debugElement.query(By.css('.custom-file-label.overflow-ellipsis'));
        expect(fileUploadLabel).to.exist;
        expect(fileUploadLabel.nativeElement.value).to.be.undefined;

        // check if extension elements are set
        const extension = debugElement.query(By.css('.ml-1.badge.badge-info'));
        expect(extension).to.exist;
        expect(extension.nativeElement.textContent.replace(/\s/g, '')).to.be.equal(fileUploadExercise.filePattern.split(',')[0].toUpperCase());
    }));

    it('Incorrect file type can not be submitted', fakeAsync(() => {
        // Ignore console errors and window confirm
        console.error = jest.fn();
        window.confirm = () => {
            return false;
        };

        const fileName = 'exampleSubmission';
        comp.submissionFile = new File([''], fileName, { type: 'image/jpg' });
        Object.defineProperty(comp.submissionFile, 'size', { value: MAX_SUBMISSION_FILE_SIZE + 1, writable: false });
        comp.submission = new FileUploadSubmission();
        comp.submit();
        tick();
        fixture.detectChanges();

        // check that properties are set properly
        expect(comp.erroredFile).to.be.not.null;
        expect(comp.submissionFile).to.be.null;
        expect(comp.submission.filePath).to.be.null;

        // check if fileUploadInput is available
        const fileUploadInput = debugElement.query(By.css('#fileUploadInput'));
        expect(fileUploadInput).to.exist;
        expect(fileUploadInput.nativeElement.disabled).to.be.false;
        expect(fileUploadInput.nativeElement.value).to.be.equal('');
    }));

    it('Submission and file uploaded', fakeAsync(() => {
        // Ignore window confirm
        window.confirm = () => {
            return false;
        };
        const fileName = 'exampleSubmission';
        comp.submissionFile = new File([''], fileName, { type: 'application/pdf' });
        comp.submission = fileUploadSubmission;
        fixture.detectChanges();

        // check if fileUploadLabel value is not set
        const fileUploadLabel = debugElement.query(By.css('.custom-file-label.overflow-ellipsis'));
        expect(fileUploadLabel).to.exist;
        expect(fileUploadLabel.nativeElement.textContent).to.be.equal(fileName);

        let submitFileButton = debugElement.query(By.css('.btn.btn-success'));
        spyOn(fileUploaderService, 'uploadFile').and.returnValue(Promise.resolve({ path: 'test' }));
        submitFileButton.nativeElement.click();
        comp.submission.submitted = true;
        comp.result = new Result();
        fixture.detectChanges();

        // check if fileUploadInput is available
        const fileUploadInput = debugElement.query(By.css('#fileUploadInput'));
        expect(fileUploadInput).to.not.exist;

        submitFileButton = debugElement.query(By.css('.btn.btn-success'));
        expect(submitFileButton).to.be.null;
    }));
});
