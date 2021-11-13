import { ComponentFixture, fakeAsync, flush, TestBed, tick } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { AccountService } from 'app/core/auth/account.service';
import { ArtemisTestModule } from '../../test.module';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockParticipationWebsocketService } from '../../helpers/mocks/service/mock-participation-websocket.service';
import { MockComponent, MockPipe } from 'ng-mocks';
import { AlertService } from 'app/core/util/alert.service';
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
import { createFileUploadSubmission, MockFileUploadSubmissionService } from '../../helpers/mocks/service/mock-file-upload-submission.service';
import { ParticipationWebsocketService } from 'app/overview/participation-websocket.service';
import { fileUploadExercise } from '../../helpers/mocks/service/mock-file-upload-exercise.service';
import { MAX_SUBMISSION_FILE_SIZE } from 'app/shared/constants/input.constants';
import { TranslateModule } from '@ngx-translate/core';
import dayjs from 'dayjs';
import { of } from 'rxjs';
import { FileUploaderService } from 'app/shared/http/file-uploader.service';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { Result } from 'app/entities/result.model';
import { FileUploadSubmissionService } from 'app/exercises/file-upload/participate/file-upload-submission.service';
import { ComplaintsForTutorComponent } from 'app/complaints/complaints-for-tutor/complaints-for-tutor.component';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTimeAgoPipe } from 'app/shared/pipes/artemis-time-ago.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { AlertErrorComponent } from 'app/shared/alert/alert-error.component';
import { AlertComponent } from 'app/shared/alert/alert.component';
import { ResizeableContainerComponent } from 'app/shared/resizeable-container/resizeable-container.component';
import { AdditionalFeedbackComponent } from 'app/shared/additional-feedback/additional-feedback.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ButtonComponent } from 'app/shared/components/button.component';
import { RatingComponent } from 'app/exercises/shared/rating/rating.component';
import { HeaderParticipationPageComponent } from 'app/exercises/shared/exercise-headers/header-participation-page.component';
import { ComplaintsStudentViewComponent } from 'app/complaints/complaints-for-students/complaints-student-view.component';

describe('FileUploadSubmissionComponent', () => {
    let comp: FileUploadSubmissionComponent;
    let fixture: ComponentFixture<FileUploadSubmissionComponent>;
    let debugElement: DebugElement;
    let router: Router;
    let fileUploaderService: FileUploaderService;
    let alertService: AlertService;
    let fileUploadSubmissionService: FileUploadSubmissionService;

    const result = { id: 1 } as Result;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule, NgxDatatableModule, TranslateModule.forRoot(), RouterTestingModule.withRoutes([routes[0]])],
            declarations: [
                FileUploadSubmissionComponent,
                MockComponent(ComplaintsForTutorComponent),
                MockComponent(AlertErrorComponent),
                MockComponent(AlertComponent),
                MockComponent(ResizeableContainerComponent),
                MockComponent(AdditionalFeedbackComponent),
                MockComponent(ButtonComponent),
                MockComponent(RatingComponent),
                MockComponent(ComplaintsStudentViewComponent),
                MockComponent(HeaderParticipationPageComponent),
                MockComponent(FaIconComponent),
                MockPipe(HtmlForMarkdownPipe),
                MockPipe(ArtemisDatePipe),
                MockPipe(ArtemisTimeAgoPipe),
                MockPipe(ArtemisTranslatePipe),
            ],
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
                alertService = TestBed.inject(AlertService);
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
        expect(comp.acceptedFileExtensions.replace(/\./g, '')).toEqual(fileUploadExercise.filePattern);
        expect(comp.fileUploadExercise).toEqual(fileUploadExercise);
        expect(comp.isAfterAssessmentDueDate).toBe(true);

        // check if fileUploadInput is available
        const fileUploadInput = debugElement.query(By.css('#fileUploadInput'));
        expect(fileUploadInput).not.toBe(null);
        expect(fileUploadInput.nativeElement.disabled).toBe(false);

        // check if extension elements are set
        const extension = debugElement.query(By.css('.ms-1.badge.bg-info'));
        expect(extension).toBeDefined();
        expect(extension.nativeElement.textContent.replace(/\s/g, '')).toEqual(fileUploadExercise.filePattern!.split(',')[0].toUpperCase());
    }));

    it('Submission and file uploaded', () => {
        // Ignore window confirm
        window.confirm = () => {
            return false;
        };
        const fileName = 'exampleSubmission';
        comp.submissionFile = new File([''], fileName, { type: 'application/pdf' });
        comp.submission = createFileUploadSubmission();
        fixture.detectChanges();

        let submitFileButton = debugElement.query(By.css('jhi-button'));
        jest.spyOn(fileUploaderService, 'uploadFile').mockReturnValue(Promise.resolve({ path: 'test' }));
        submitFileButton.nativeElement.click();
        comp.submission!.submitted = true;
        comp.result = new Result();
        fixture.detectChanges();

        // check if fileUploadInput is available
        const fileUploadInput = debugElement.query(By.css('#fileUploadInput'));
        expect(fileUploadInput).toBe(null);

        submitFileButton = debugElement.query(By.css('.btn.btn-success'));
        expect(submitFileButton).toBe(null);
    });

    it('Too big file can not be submitted', fakeAsync(() => {
        // Ignore console errors
        console.error = jest.fn();

        fixture.detectChanges();
        tick();

        const submissionFile = new File([''], 'exampleSubmission.png');
        Object.defineProperty(submissionFile, 'size', { value: MAX_SUBMISSION_FILE_SIZE + 1, writable: false });
        comp.submission = createFileUploadSubmission();
        const jhiErrorSpy = jest.spyOn(alertService, 'error');
        const event = { target: { files: [submissionFile] } };
        comp.setFileSubmissionForExercise(event);
        fixture.detectChanges();

        // check that properties are set properly
        expect(jhiErrorSpy).toBeCalledTimes(1);
        expect(comp.submissionFile).toBe(undefined);
        expect(comp.submission!.filePath).toBe(undefined);

        // check if fileUploadInput is available
        const fileUploadInput = debugElement.query(By.css('#fileUploadInput'));
        expect(fileUploadInput).toBeDefined();
        expect(fileUploadInput.nativeElement.disabled).toBe(false);
        expect(fileUploadInput.nativeElement.value).toEqual('');
    }));

    it('Incorrect file type can not be submitted', fakeAsync(() => {
        // Ignore console errors
        console.error = jest.fn();

        fixture.detectChanges();
        tick();

        // Only png and pdf types are allowed
        const submissionFile = new File([''], 'exampleSubmission.jpg');
        comp.submission = createFileUploadSubmission();
        const jhiErrorSpy = jest.spyOn(alertService, 'error');
        const event = { target: { files: [submissionFile] } };
        comp.setFileSubmissionForExercise(event);
        fixture.detectChanges();

        // check that properties are set properly
        expect(jhiErrorSpy).toBeCalledTimes(1);
        expect(comp.submissionFile).toBe(undefined);
        expect(comp.submission!.filePath).toBe(undefined);

        // check if fileUploadInput is available
        const fileUploadInput = debugElement.query(By.css('#fileUploadInput'));
        expect(fileUploadInput).toBeDefined();
        expect(fileUploadInput.nativeElement.disabled).toBe(false);
        expect(fileUploadInput.nativeElement.value).toEqual('');

        tick();
        fixture.destroy();
        flush();
    }));

    it('should not allow to submit after the deadline if the initialization date is before the due date', fakeAsync(() => {
        const submission = createFileUploadSubmission();
        submission.participation!.initializationDate = dayjs().subtract(2, 'days');
        (<StudentParticipation>submission.participation).exercise!.dueDate = dayjs().subtract(1, 'days');
        jest.spyOn(fileUploadSubmissionService, 'getDataForFileUploadEditor').mockReturnValue(of(submission));
        comp.submissionFile = new File([''], 'exampleSubmission.png');

        fixture.detectChanges();
        tick();

        const submitButton = debugElement.query(By.css('jhi-button'));
        expect(submitButton).toBeDefined();
        expect(submitButton.attributes['ng-reflect-disabled']).toEqual('true');

        tick();
        fixture.destroy();
        flush();
    }));

    it('should allow to submit after the deadline if the initialization date is after the due date', fakeAsync(() => {
        const submission = createFileUploadSubmission();
        submission.participation!.initializationDate = dayjs().add(1, 'days');
        (<StudentParticipation>submission.participation).exercise!.dueDate = dayjs();
        jest.spyOn(fileUploadSubmissionService, 'getDataForFileUploadEditor').mockReturnValue(of(submission));
        comp.submissionFile = new File([''], 'exampleSubmission.png');

        fixture.detectChanges();
        tick();

        expect(comp.isLate).toBe(true);
        const submitButton = debugElement.query(By.css('jhi-button'));
        expect(submitButton).toBeDefined();
        expect(submitButton.attributes['ng-reflect-disabled']).toEqual('false');

        tick();
        fixture.destroy();
        flush();
    }));

    it('should not allow to submit if there is a result and no due date', fakeAsync(() => {
        jest.spyOn(fileUploadSubmissionService, 'getDataForFileUploadEditor').mockReturnValue(of(createFileUploadSubmission()));
        comp.submissionFile = new File([''], 'exampleSubmission.png');

        fixture.detectChanges();
        tick();

        comp.result = result;
        fixture.detectChanges();

        const submitButton = debugElement.query(By.css('jhi-button'));
        expect(submitButton).toBeDefined();
        expect(submitButton.attributes['ng-reflect-disabled']).toEqual('true');

        tick();
        fixture.destroy();
        flush();
    }));

    it('should get inactive as soon as the due date passes the current date', fakeAsync(() => {
        const submission = createFileUploadSubmission();
        (<StudentParticipation>submission.participation).exercise!.dueDate = dayjs().add(1, 'days');
        jest.spyOn(fileUploadSubmissionService, 'getDataForFileUploadEditor').mockReturnValue(of(submission));
        comp.submissionFile = new File([''], 'exampleSubmission.png');

        fixture.detectChanges();
        tick();
        comp.participation.initializationDate = dayjs();

        expect(comp.isActive).toBe(true);

        comp.fileUploadExercise.dueDate = dayjs().subtract(1, 'days');

        fixture.detectChanges();
        tick();

        expect(comp.isActive).toBe(false);

        tick();
        fixture.destroy();
        flush();
    }));
});
