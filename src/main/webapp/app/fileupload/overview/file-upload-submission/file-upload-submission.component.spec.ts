import { ComponentFixture, TestBed, fakeAsync, flush, tick } from '@angular/core/testing';
import { AccountService } from 'app/core/auth/account.service';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { MockParticipationWebsocketService } from 'test/helpers/mocks/service/mock-participation-websocket.service';
import { MockComponent, MockPipe } from 'ng-mocks';
import { AlertService } from 'app/shared/service/alert.service';
import { DebugElement } from '@angular/core';
import { By } from '@angular/platform-browser';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { ComplaintService } from 'app/assessment/shared/services/complaint.service';
import { MockComplaintService } from 'test/helpers/mocks/service/mock-complaint.service';
import { NgxDatatableModule } from '@siemens/ngx-datatable';
import { FileUploadSubmissionComponent } from 'app/fileupload/overview/file-upload-submission/file-upload-submission.component';
import { MockFileUploadSubmissionService, createFileUploadSubmission, fileUploadParticipation } from 'test/helpers/mocks/service/mock-file-upload-submission.service';
import { ParticipationWebsocketService } from 'app/core/course/shared/services/participation-websocket.service';
import { fileUploadExercise } from 'test/helpers/mocks/service/mock-file-upload-exercise.service';
import { MAX_SUBMISSION_FILE_SIZE } from 'app/shared/constants/input.constants';
import { TranslateService } from '@ngx-translate/core';
import dayjs from 'dayjs/esm';
import { of } from 'rxjs';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { FileUploadSubmissionService } from 'app/fileupload/overview/file-upload-submission.service';
import { FileUploadSubmission } from 'app/fileupload/shared/entities/file-upload-submission.model';
import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';
import { ComplaintsForTutorComponent } from 'app/assessment/manage/complaints-for-tutor/complaints-for-tutor.component';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTimeAgoPipe } from 'app/shared/pipes/artemis-time-ago.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ResizeableContainerComponent } from 'app/shared/resizeable-container/resizeable-container.component';
import { AdditionalFeedbackComponent } from 'app/exercise/additional-feedback/additional-feedback.component';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { RatingComponent } from 'app/exercise/rating/rating.component';
import { HeaderParticipationPageComponent } from 'app/exercise/exercise-headers/participation-page/header-participation-page.component';
import { ComplaintsStudentViewComponent } from 'app/assessment/overview/complaints-for-students/complaints-student-view.component';

import { GradingInstruction } from 'app/exercise/structured-grading-criterion/grading-instruction.model';
import { Feedback, FeedbackType } from 'app/assessment/shared/entities/feedback.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { FileService } from 'app/shared/service/file.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

describe('FileUploadSubmissionComponent', () => {
    let comp: FileUploadSubmissionComponent;
    let fixture: ComponentFixture<FileUploadSubmissionComponent>;
    let debugElement: DebugElement;
    let alertService: AlertService;
    let fileUploadSubmissionService: FileUploadSubmissionService;

    const result = { id: 1 } as Result;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [
                NgxDatatableModule,
                FaIconComponent,
                FileUploadSubmissionComponent,
                MockComponent(ComplaintsForTutorComponent),
                MockComponent(ResizeableContainerComponent),
                MockComponent(AdditionalFeedbackComponent),
                MockComponent(ButtonComponent),
                MockComponent(RatingComponent),
                MockComponent(ComplaintsStudentViewComponent),
                MockComponent(HeaderParticipationPageComponent),
                MockPipe(HtmlForMarkdownPipe),
                MockPipe(ArtemisDatePipe),
                MockPipe(ArtemisTimeAgoPipe),
                MockPipe(ArtemisTranslatePipe),
            ],
            providers: [
                { provide: AccountService, useClass: MockAccountService },
                SessionStorageService,
                LocalStorageService,
                { provide: ComplaintService, useClass: MockComplaintService },
                { provide: FileUploadSubmissionService, useClass: MockFileUploadSubmissionService },
                { provide: ParticipationWebsocketService, useClass: MockParticipationWebsocketService },
                { provide: TranslateService, useClass: MockTranslateService },
                provideHttpClient(),
                provideHttpClientTesting(),
                provideRouter([]),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(FileUploadSubmissionComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;
                alertService = TestBed.inject(AlertService);
                fileUploadSubmissionService = TestBed.inject(FileUploadSubmissionService);
            });
    });

    afterEach(fakeAsync(() => {
        tick();
        fixture.destroy();
        flush();
    }));

    // Helper to trigger data load
    function activateComponent() {
        fixture.componentRef.setInput('participationId', 1);
        const accountService = TestBed.inject(AccountService);
        jest.spyOn(accountService, 'isOwnerOfParticipation').mockReturnValue(true);
    }

    it('File Upload Submission is correctly initialized from service', fakeAsync(() => {
        activateComponent();
        fixture.detectChanges();
        tick();
        // check if properties where assigned correctly on init
        expect(comp.acceptedFileExtensions().replace(/\./g, '')).toEqual(fileUploadExercise.filePattern);
        expect(comp.fileUploadExercise()).toEqual(fileUploadExercise);
        expect(comp.isAfterAssessmentDueDate()).toBeTrue();

        // check if fileUploadInput is available
        const fileUploadInput = debugElement.query(By.css('#fileUploadInput'));
        expect(fileUploadInput).not.toBeNull();
        expect(fileUploadInput.nativeElement.disabled).toBeFalse();

        // check if extension elements are set
        const extension = debugElement.query(By.css('.ms-1.badge.bg-info'));
        expect(extension).toBeDefined();
        expect(extension.nativeElement.textContent.replace(/\s/g, '')).toEqual(fileUploadExercise.filePattern!.split(',')[0].toUpperCase());
    }));

    it('Submission and file uploaded', fakeAsync(() => {
        // Ignore window confirm
        window.confirm = () => {
            return false;
        };
        const fileName = 'exampleSubmission';
        comp.submissionFile.set(new File([''], fileName, { type: 'application/pdf' }));
        const submission = createFileUploadSubmission();
        comp.submission.set(submission);
        comp.fileUploadExercise.set(submission.participation!.exercise as FileUploadExercise);
        comp.participation.set(submission.participation as StudentParticipation);
        comp.isOwnerOfParticipation.set(true); // Manually set owner
        fixture.detectChanges();

        let submitFileButton = debugElement.query(By.css('jhi-button'));
        submitFileButton.nativeElement.click();
        tick();

        comp.submission.update((s: FileUploadSubmission | undefined) => {
            if (s) s.submitted = true;
            return s;
        });
        comp.result.set(new Result());
        fixture.detectChanges();

        // After result is set, the file upload input should be hidden (as per !result() condition)
        const fileUploadInput = fixture.nativeElement.querySelector('#fileUploadInput');
        expect(fileUploadInput).toBeNull();

        submitFileButton = debugElement.query(By.css('.btn.btn-success'));
        expect(submitFileButton).toBeNull();
    }));

    it('Too big file can not be submitted', fakeAsync(() => {
        // Ignore console errors
        console.error = jest.fn();

        fixture.detectChanges();
        tick();

        const submissionFile = new File([''], 'exampleSubmission.png');
        Object.defineProperty(submissionFile, 'size', { value: MAX_SUBMISSION_FILE_SIZE + 1, writable: false });
        const submission = createFileUploadSubmission();
        comp.submission.set(submission);
        comp.fileUploadExercise.set(submission.participation!.exercise as FileUploadExercise);
        comp.participation.set(submission.participation as StudentParticipation);
        comp.isOwnerOfParticipation.set(true);
        const jhiErrorSpy = jest.spyOn(alertService, 'error');
        const event = { target: { files: [submissionFile] } };
        comp.setFileSubmissionForExercise(event);
        fixture.detectChanges();

        // check that properties are set properly
        expect(jhiErrorSpy).toHaveBeenCalledOnce();
        expect(comp.submissionFile()).toBeUndefined();
        expect(comp.submission()!.filePath).toBeUndefined();

        // check if fileUploadInput is available
        const fileUploadInput = debugElement.query(By.css('#fileUploadInput'));
        expect(fileUploadInput).toBeDefined();
        expect(fileUploadInput.nativeElement.disabled).toBeFalse();
        expect(fileUploadInput.nativeElement.value).toBe('');
    }));

    it('Incorrect file type can not be submitted', fakeAsync(() => {
        // Ignore console errors
        console.error = jest.fn();

        fixture.detectChanges();
        tick();

        // Only png and pdf types are allowed
        const submissionFile = new File([''], 'exampleSubmission.jpg');
        const submission = createFileUploadSubmission();
        comp.submission.set(submission);
        comp.fileUploadExercise.set(submission.participation!.exercise as FileUploadExercise);
        comp.participation.set(submission.participation as StudentParticipation);
        comp.isOwnerOfParticipation.set(true);
        const jhiErrorSpy = jest.spyOn(alertService, 'error');
        const event = { target: { files: [submissionFile] } };
        comp.setFileSubmissionForExercise(event);
        fixture.detectChanges();

        // check that properties are set properly
        expect(jhiErrorSpy).toHaveBeenCalledOnce();
        expect(comp.submissionFile()).toBeUndefined();
        expect(comp.submission()!.filePath).toBeUndefined();

        // check if fileUploadInput is available
        const fileUploadInput = debugElement.query(By.css('#fileUploadInput'));
        expect(fileUploadInput).toBeDefined();
        expect(fileUploadInput.nativeElement.disabled).toBeFalse();
        expect(fileUploadInput.nativeElement.value).toBe('');

        tick();
        fixture.destroy();
        flush();
    }));

    it('should not allow to submit after the due date if the initialization date is before the due date', fakeAsync(() => {
        const submission = createFileUploadSubmission();
        submission.participation!.initializationDate = dayjs().subtract(2, 'days');
        (<StudentParticipation>submission.participation).exercise!.dueDate = dayjs().subtract(1, 'days');
        jest.spyOn(fileUploadSubmissionService, 'getDataForFileUploadEditor').mockReturnValue(of(submission));
        comp.submissionFile.set(new File([''], 'exampleSubmission.png'));
        activateComponent();

        fixture.detectChanges();
        tick();

        const submitButton = debugElement.query(By.css('jhi-button'));
        expect(submitButton.componentInstance.disabled).toBeTrue();

        tick();
        fixture.destroy();
        flush();
    }));

    it('should allow to submit after the due date if the initialization date is after the due date', fakeAsync(() => {
        const submission = createFileUploadSubmission();
        submission.participation!.initializationDate = dayjs().add(1, 'days');
        (<StudentParticipation>submission.participation).exercise!.dueDate = dayjs();
        jest.spyOn(fileUploadSubmissionService, 'getDataForFileUploadEditor').mockReturnValue(of(submission));
        comp.submissionFile.set(new File([''], 'exampleSubmission.png'));
        activateComponent();

        fixture.detectChanges();
        tick();

        expect(comp.isLate()).toBeTrue();
        const submitButton = debugElement.query(By.css('jhi-button'));
        expect(submitButton.componentInstance.disabled).toBeFalse();

        tick();
        fixture.destroy();
        flush();
    }));

    it('should not allow to submit if there is a result and no due date', fakeAsync(() => {
        jest.spyOn(fileUploadSubmissionService, 'getDataForFileUploadEditor').mockReturnValue(of(createFileUploadSubmission()));
        comp.submissionFile.set(new File([''], 'exampleSubmission.png'));
        activateComponent();

        fixture.detectChanges();
        tick();

        comp.result.set(result);
        fixture.detectChanges();

        expect(comp.isLate()).toBeTrue(); // This expectation seems to depend on logic inside component that defaults/calculates isLate
        expect((!comp.isActive() && !comp.isLate()) || !comp.submission() || !comp.submissionFile() || !!comp.result()).toBeTrue();

        tick();
        fixture.destroy();
        flush();
    }));

    it('should get inactive as soon as the due date passes the current date', fakeAsync(() => {
        const submission = createFileUploadSubmission();
        const exercise = (<StudentParticipation>submission.participation).exercise as any;
        exercise.dueDate = dayjs().add(1, 'days');
        jest.spyOn(fileUploadSubmissionService, 'getDataForFileUploadEditor').mockReturnValue(of(submission));
        comp.submissionFile.set(new File([''], 'exampleSubmission.png'));
        activateComponent();

        fixture.detectChanges();
        tick();

        // comp.participation is now a signal, but we can mutate the object it holds if it's the same ref,
        // or update the signal.
        const participation = comp.participation()!;
        participation.initializationDate = dayjs();
        comp.participation.set(participation); // trigger signal update

        expect(comp.isActive()).toBeTrue();

        const exerciseToUpdate = comp.fileUploadExercise()!;
        // Signal object equality check: we must create a NEW object or spread to trigger change
        const newExercise = { ...exerciseToUpdate, dueDate: dayjs().subtract(1, 'days') } as FileUploadExercise;
        comp.fileUploadExercise.set(newExercise); // trigger signal update

        fixture.detectChanges();
        tick();

        expect(comp.isActive()).toBeFalse();

        tick();
        fixture.destroy();
        flush();
    }));

    it('should mark the subsequent feedback', () => {
        const gradingInstruction = {
            id: 1,
            credits: 1,
            gradingScale: 'scale',
            instructionDescription: 'description',
            feedback: 'instruction feedback',
            usageCount: 1,
        } as GradingInstruction;

        const feedbacks = [
            {
                id: 1,
                detailText: 'feedback1',
                credits: 1,
                gradingInstruction,
                type: FeedbackType.MANUAL_UNREFERENCED,
            } as Feedback,
            {
                id: 2,
                detailText: 'feedback2',
                credits: 1,
                gradingInstruction,
                type: FeedbackType.MANUAL_UNREFERENCED,
            } as Feedback,
        ];

        comp.result.set(new Result());
        const res = comp.result()!;
        res.feedbacks = feedbacks;
        comp.result.set(res);

        const unreferencedFeedback = comp.unreferencedFeedback();

        expect(unreferencedFeedback).toBeDefined();
        expect(unreferencedFeedback).toHaveLength(2);
        expect(unreferencedFeedback![0].isSubsequent).toBeUndefined();
        expect(unreferencedFeedback![1].isSubsequent).toBeTrue();
    });

    it('should download file', () => {
        const fileService = TestBed.inject(FileService);
        const fileServiceStub = jest.spyOn(fileService, 'downloadFile').mockImplementation();

        comp.downloadFile('');

        expect(fileServiceStub).toHaveBeenCalledOnce();
    });

    it('should decide over deactivation correctly', () => {
        const submission = createFileUploadSubmission();

        expect(comp.canDeactivate()).toBeTrue();

        submission.submitted = true;
        comp.submission.set(submission);

        expect(comp.canDeactivate()).toBeTrue();

        comp.submissionFile.set(new File([''], 'exampleSubmission.png'));

        expect(comp.canDeactivate()).toBeTrue();

        submission.submitted = false;
        comp.submission.set(submission);

        expect(comp.canDeactivate()).toBeFalse();
    });

    it('should set alert correctly', async () => {
        // Ignore window confirm
        window.confirm = () => {
            return false;
        };
        const fileName = 'exampleSubmission';
        comp.submissionFile.set(new File([''], fileName, { type: 'application/pdf' }));
        const submission = createFileUploadSubmission();
        submission.filePath = 'test/exampleSubmission.pdf';
        submission.participation = new StudentParticipation();
        submission.participation.initializationDate = dayjs().subtract(2, 'days');
        submission.participation.exercise = fileUploadExercise;
        submission.participation.exercise.dueDate = dayjs().subtract(1, 'days');

        const jhiWarningSpy = jest.spyOn(alertService, 'warning');
        jest.spyOn(fileUploadSubmissionService, 'getDataForFileUploadEditor').mockReturnValue(of(submission));
        jest.spyOn(fileUploadSubmissionService, 'update').mockReturnValue(of(new HttpResponse({ body: submission })));

        // Activate
        activateComponent();
        fixture.detectChanges();
        await fixture.whenStable();

        await comp.submitExercise();

        expect(comp.isActive()).toBeFalse();
        expect(jhiWarningSpy).toHaveBeenCalledWith('artemisApp.fileUploadExercise.submitDueDateMissed');
    });

    it('should set file name and type correctly', fakeAsync(() => {
        const fileName = 'exampleSubmission';
        comp.submissionFile.set(new File([''], fileName, { type: 'application/pdf' }));
        const submission = createFileUploadSubmission();
        submission.filePath = 'test/exampleSubmission.pdf';
        comp.submission.set(submission);
        jest.spyOn(fileUploadSubmissionService, 'getDataForFileUploadEditor').mockReturnValue(of(submission));
        fixture.detectChanges();

        comp.submitExercise();
        tick();

        expect(comp.submittedFileName()).toBe(fileName + '.pdf');
        expect(comp.submittedFileExtension()).toBe('pdf');
    }));

    it('should be set up with input values if present instead of loading new values from server', () => {
        // @ts-ignore method is private
        const setUpComponentWithInputValuesSpy = jest.spyOn(comp, 'setupComponentWithInputValues');
        const getDataForFileUploadEditorSpy = jest.spyOn(fileUploadSubmissionService, 'getDataForFileUploadEditor');
        const fileUploadSubmission = createFileUploadSubmission();
        fileUploadSubmission.submitted = true;
        fixture.componentRef.setInput('inputExercise', fileUploadExercise);
        fixture.componentRef.setInput('inputSubmission', fileUploadSubmission);
        fixture.componentRef.setInput('inputParticipation', fileUploadParticipation);

        fixture.detectChanges();

        expect(setUpComponentWithInputValuesSpy).toHaveBeenCalledOnce();
        expect(comp.fileUploadExercise()).toEqual(fileUploadExercise);
        expect(comp.submission()).toEqual(fileUploadSubmission);
        expect(comp.participation()).toEqual(fileUploadParticipation);

        // should not fetch additional information from server, reason for input values!
        expect(getDataForFileUploadEditorSpy).not.toHaveBeenCalled();
    });
});
