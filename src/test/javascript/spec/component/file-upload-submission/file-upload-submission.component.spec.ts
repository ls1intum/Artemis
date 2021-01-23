import { ComponentFixture, fakeAsync, flush, TestBed, tick } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { AccountService } from 'app/core/auth/account.service';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ArtemisTestModule } from '../../test.module';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockParticipationWebsocketService } from '../../helpers/mocks/service/mock-participation-websocket.service';
import { MockComponent } from 'ng-mocks';

import { JhiAlertService } from 'ng-jhipster';
import { Router } from '@angular/router';
import { DebugElement } from '@angular/core';
import { By } from '@angular/platform-browser';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { ComplaintService } from 'app/complaints/complaint.service';
import { MockComplaintService } from '../../helpers/mocks/service/mock-complaint.service';
import { NgxDatatableModule } from '@swimlane/ngx-datatable';
import { routes } from 'app/exercises/file-upload/participate/file-upload-participation.route';
import { FileUploadSubmissionComponent } from 'app/exercises/file-upload/participate/file-upload-submission.component';
import { MomentModule } from 'ngx-moment';
import { createFileUploadSubmission, MockFileUploadSubmissionService } from '../../helpers/mocks/service/mock-file-upload-submission.service';
import { ParticipationWebsocketService } from 'app/overview/participation-websocket.service';
import { fileUploadExercise } from '../../helpers/mocks/service/mock-file-upload-exercise.service';
import { MAX_SUBMISSION_FILE_SIZE } from 'app/shared/constants/input.constants';
import { TranslateModule } from '@ngx-translate/core';
import * as sinon from 'sinon';
import { stub } from 'sinon';
import { FileUploadResultComponent } from 'app/exercises/file-upload/participate/file-upload-result.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import * as moment from 'moment';
import { of } from 'rxjs';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { FileUploaderService } from 'app/shared/http/file-uploader.service';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { Result } from 'app/entities/result.model';
import { FileUploadSubmissionService } from 'app/exercises/file-upload/participate/file-upload-submission.service';
import { ComplaintsForTutorComponent } from 'app/complaints/complaints-for-tutor/complaints-for-tutor.component';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { ArtemisComplaintsModule } from 'app/complaints/complaints.module';
import { ArtemisHeaderExercisePageWithDetailsModule } from 'app/exercises/shared/exercise-headers/exercise-headers.module';
import { RatingModule } from 'app/exercises/shared/rating/rating.module';

chai.use(sinonChai);
const expect = chai.expect;

describe('FileUploadSubmissionComponent', () => {
    let comp: FileUploadSubmissionComponent;
    let fixture: ComponentFixture<FileUploadSubmissionComponent>;
    let debugElement: DebugElement;
    let router: Router;
    let fileUploaderService: FileUploaderService;
    let jhiAlertService: JhiAlertService;
    let fileUploadSubmissionService: FileUploadSubmissionService;

    const result = { id: 1 } as Result;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [
                ArtemisTestModule,
                NgxDatatableModule,
                ArtemisResultModule,
                ArtemisSharedModule,
                MomentModule,
                ArtemisComplaintsModule,
                TranslateModule.forRoot(),
                RouterTestingModule.withRoutes([routes[0]]),
                ArtemisSharedComponentModule,
                ArtemisHeaderExercisePageWithDetailsModule,
                RatingModule,
            ],
            declarations: [FileUploadSubmissionComponent, MockComponent(ComplaintsForTutorComponent), MockComponent(FileUploadResultComponent)],
            providers: [
                { provide: AccountService, useClass: MockAccountService },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: ComplaintService, useClass: MockComplaintService },
                { provide: FileUploadSubmissionService, useClass: MockFileUploadSubmissionService },
                { provide: ParticipationWebsocketService, useClass: MockParticipationWebsocketService },
            ],
        })
            .overrideModule(ArtemisTestModule, { set: { declarations: [], exports: [] } })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(FileUploadSubmissionComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;
                router = debugElement.injector.get(Router);
                fixture.ngZone!.run(() => {
                    router.initialNavigation();
                });
                fileUploaderService = TestBed.inject(FileUploaderService);
                jhiAlertService = TestBed.inject(JhiAlertService);
                fileUploadSubmissionService = debugElement.injector.get(FileUploadSubmissionService);
            });
    });

    afterEach(fakeAsync(() => {
        tick();
        fixture.destroy();
        flush();
    }));

    it('File Upload Submission is correctly initialized from service', fakeAsync(() => {
        fixture.detectChanges();
        tick();
        // check if properties where assigned correctly on init
        expect(comp.acceptedFileExtensions.replace(/\./g, '')).to.be.equal(fileUploadExercise.filePattern);
        expect(comp.fileUploadExercise).to.be.equal(fileUploadExercise);
        expect(comp.isAfterAssessmentDueDate).to.be.true;

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
        expect(extension.nativeElement.textContent.replace(/\s/g, '')).to.be.equal(fileUploadExercise.filePattern!.split(',')[0].toUpperCase());
    }));

    it('Submission and file uploaded', fakeAsync(() => {
        // Ignore window confirm
        window.confirm = () => {
            return false;
        };
        const fileName = 'exampleSubmission';
        comp.submissionFile = new File([''], fileName, { type: 'application/pdf' });
        comp.submission = createFileUploadSubmission();
        fixture.detectChanges();

        // check if fileUploadLabel value is not set
        const fileUploadLabel = debugElement.query(By.css('.custom-file-label.overflow-ellipsis'));
        expect(fileUploadLabel).to.exist;
        expect(fileUploadLabel.nativeElement.textContent).to.be.equal(fileName);

        let submitFileButton = debugElement.query(By.css('jhi-button'));
        spyOn(fileUploaderService, 'uploadFile').and.returnValue(Promise.resolve({ path: 'test' }));
        submitFileButton.nativeElement.click();
        comp.submission!.submitted = true;
        comp.result = new Result();
        fixture.detectChanges();

        // check if fileUploadInput is available
        const fileUploadInput = debugElement.query(By.css('#fileUploadInput'));
        expect(fileUploadInput).to.not.exist;

        submitFileButton = debugElement.query(By.css('.btn.btn-success'));
        expect(submitFileButton).to.be.null;
    }));

    it('Too big file can not be submitted', fakeAsync(() => {
        // Ignore console errors
        console.error = jest.fn();

        fixture.detectChanges();
        tick();

        const submissionFile = new File([''], 'exampleSubmission.png');
        Object.defineProperty(submissionFile, 'size', { value: MAX_SUBMISSION_FILE_SIZE + 1, writable: false });
        comp.submission = createFileUploadSubmission();
        const jhiErrorSpy = sinon.spy(jhiAlertService, 'error');
        const event = { target: { files: [submissionFile] } };
        comp.setFileSubmissionForExercise(event);
        fixture.detectChanges();

        // check that properties are set properly
        expect(jhiErrorSpy.callCount).to.be.equal(1);
        expect(comp.submissionFile).to.be.undefined;
        expect(comp.submission!.filePath).to.be.undefined;

        // check if fileUploadInput is available
        const fileUploadInput = debugElement.query(By.css('#fileUploadInput'));
        expect(fileUploadInput).to.exist;
        expect(fileUploadInput.nativeElement.disabled).to.be.false;
        expect(fileUploadInput.nativeElement.value).to.be.equal('');
    }));

    it('Incorrect file type can not be submitted', fakeAsync(() => {
        // Ignore console errors
        console.error = jest.fn();

        fixture.detectChanges();
        tick();

        // Only png and pdf types are allowed
        const submissionFile = new File([''], 'exampleSubmission.jpg');
        comp.submission = createFileUploadSubmission();
        const jhiErrorSpy = sinon.spy(jhiAlertService, 'error');
        const event = { target: { files: [submissionFile] } };
        comp.setFileSubmissionForExercise(event);
        fixture.detectChanges();

        // check that properties are set properly
        expect(jhiErrorSpy.callCount).to.be.equal(1);
        expect(comp.submissionFile).to.be.undefined;
        expect(comp.submission!.filePath).to.be.undefined;

        // check if fileUploadInput is available
        const fileUploadInput = debugElement.query(By.css('#fileUploadInput'));
        expect(fileUploadInput).to.exist;
        expect(fileUploadInput.nativeElement.disabled).to.be.false;
        expect(fileUploadInput.nativeElement.value).to.be.equal('');

        tick();
        fixture.destroy();
        flush();
    }));

    it('should not allow to submit after the deadline if the initialization date is before the due date', fakeAsync(() => {
        const submission = createFileUploadSubmission();
        submission.participation!.initializationDate = moment().subtract(2, 'days');
        (<StudentParticipation>submission.participation).exercise!.dueDate = moment().subtract(1, 'days');
        stub(fileUploadSubmissionService, 'getDataForFileUploadEditor').returns(of(submission));
        comp.submissionFile = new File([''], 'exampleSubmission.png');

        fixture.detectChanges();
        tick();

        const submitButton = debugElement.query(By.css('jhi-button'));
        expect(submitButton).to.exist;
        expect(submitButton.attributes['ng-reflect-disabled']).to.be.equal('true');

        tick();
        fixture.destroy();
        flush();
    }));

    it('should allow to submit after the deadline if the initialization date is after the due date', fakeAsync(() => {
        const submission = createFileUploadSubmission();
        submission.participation!.initializationDate = moment().add(1, 'days');
        (<StudentParticipation>submission.participation).exercise!.dueDate = moment();
        stub(fileUploadSubmissionService, 'getDataForFileUploadEditor').returns(of(submission));
        comp.submissionFile = new File([''], 'exampleSubmission.png');

        fixture.detectChanges();
        tick();

        expect(comp.isLate).to.be.true;
        const submitButton = debugElement.query(By.css('jhi-button'));
        expect(submitButton).to.exist;
        expect(submitButton.attributes['ng-reflect-disabled']).to.be.equal('false');

        tick();
        fixture.destroy();
        flush();
    }));

    it('should not allow to submit if there is a result and no due date', fakeAsync(() => {
        stub(fileUploadSubmissionService, 'getDataForFileUploadEditor').returns(of(createFileUploadSubmission()));
        comp.submissionFile = new File([''], 'exampleSubmission.png');

        fixture.detectChanges();
        tick();

        comp.result = result;
        fixture.detectChanges();

        const submitButton = debugElement.query(By.css('jhi-button'));
        expect(submitButton).to.exist;
        expect(submitButton.attributes['ng-reflect-disabled']).to.be.equal('true');

        tick();
        fixture.destroy();
        flush();
    }));

    it('should get inactive as soon as the due date passes the current date', fakeAsync(() => {
        const submission = createFileUploadSubmission();
        (<StudentParticipation>submission.participation).exercise!.dueDate = moment().add(1, 'days');
        stub(fileUploadSubmissionService, 'getDataForFileUploadEditor').returns(of(submission));
        comp.submissionFile = new File([''], 'exampleSubmission.png');

        fixture.detectChanges();
        tick();
        comp.participation.initializationDate = moment();

        expect(comp.isActive).to.be.true;

        comp.fileUploadExercise.dueDate = moment().subtract(1, 'days');

        fixture.detectChanges();
        tick();

        expect(comp.isActive).to.be.false;

        tick();
        fixture.destroy();
        flush();
    }));
});
