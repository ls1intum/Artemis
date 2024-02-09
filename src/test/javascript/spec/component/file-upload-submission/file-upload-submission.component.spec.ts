import { ComponentFixture, TestBed, fakeAsync, flush, tick } from '@angular/core/testing';
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
import { NgxDatatableModule } from '@flaviosantoro92/ngx-datatable';
import { routes } from 'app/exercises/file-upload/participate/file-upload-participation.route';
import { FileUploadSubmissionComponent } from 'app/exercises/file-upload/participate/file-upload-submission.component';
import { MockFileUploadSubmissionService, createFileUploadSubmission, fileUploadParticipation } from '../../helpers/mocks/service/mock-file-upload-submission.service';
import { ParticipationWebsocketService } from 'app/overview/participation-websocket.service';
import { fileUploadExercise } from '../../helpers/mocks/service/mock-file-upload-exercise.service';
import { MAX_SUBMISSION_FILE_SIZE } from 'app/shared/constants/input.constants';
import { TranslateModule } from '@ngx-translate/core';
import dayjs from 'dayjs/esm';
import { of } from 'rxjs';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { Result } from 'app/entities/result.model';
import { FileUploadSubmissionService } from 'app/exercises/file-upload/participate/file-upload-submission.service';
import { ComplaintsForTutorComponent } from 'app/complaints/complaints-for-tutor/complaints-for-tutor.component';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTimeAgoPipe } from 'app/shared/pipes/artemis-time-ago.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ResizeableContainerComponent } from 'app/shared/resizeable-container/resizeable-container.component';
import { AdditionalFeedbackComponent } from 'app/shared/additional-feedback/additional-feedback.component';
import { ButtonComponent } from 'app/shared/components/button.component';
import { RatingComponent } from 'app/exercises/shared/rating/rating.component';
import { HeaderParticipationPageComponent } from 'app/exercises/shared/exercise-headers/header-participation-page.component';
import { ComplaintsStudentViewComponent } from 'app/complaints/complaints-for-students/complaints-student-view.component';
import { FileService } from 'app/shared/http/file.service';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { GradingInstruction } from 'app/exercises/shared/structured-grading-criterion/grading-instruction.model';
import { Feedback, FeedbackType } from 'app/entities/feedback.model';

describe('FileUploadSubmissionComponent', () => {
    let comp: FileUploadSubmissionComponent;
    let fixture: ComponentFixture<FileUploadSubmissionComponent>;
    let debugElement: DebugElement;
    let router: Router;
    let alertService: AlertService;
    let fileUploadSubmissionService: FileUploadSubmissionService;

    const result = { id: 1 } as Result;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, NgxDatatableModule, TranslateModule.forRoot(), RouterTestingModule.withRoutes([routes[0]])],
            declarations: [
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
                fixture.ngZone!.run(() => {
                    router.initialNavigation();
                });
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
        expect(comp.isAfterAssessmentDueDate).toBeTrue();

        // check if fileUploadInput is available
        const fileUploadInput = debugElement.query(By.css('#fileUploadInput'));
        expect(fileUploadInput).not.toBeNull();
        expect(fileUploadInput.nativeElement.disabled).toBeFalse();

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
        submitFileButton.nativeElement.click();
        comp.submission!.submitted = true;
        comp.result = new Result();
        fixture.detectChanges();

        // check if fileUploadInput is available
        const fileUploadInput = debugElement.query(By.css('#fileUploadInput'));
        expect(fileUploadInput).toBeNull();

        submitFileButton = debugElement.query(By.css('.btn.btn-success'));
        expect(submitFileButton).toBeNull();
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
        expect(jhiErrorSpy).toHaveBeenCalledOnce();
        expect(comp.submissionFile).toBeUndefined();
        expect(comp.submission!.filePath).toBeUndefined();

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
        comp.submission = createFileUploadSubmission();
        const jhiErrorSpy = jest.spyOn(alertService, 'error');
        const event = { target: { files: [submissionFile] } };
        comp.setFileSubmissionForExercise(event);
        fixture.detectChanges();

        // check that properties are set properly
        expect(jhiErrorSpy).toHaveBeenCalledOnce();
        expect(comp.submissionFile).toBeUndefined();
        expect(comp.submission!.filePath).toBeUndefined();

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
        comp.submissionFile = new File([''], 'exampleSubmission.png');

        fixture.detectChanges();
        tick();

        const submitButton = debugElement.query(By.css('jhi-button'));
        expect(submitButton).toBeDefined();
        expect(submitButton.attributes['ng-reflect-disabled']).toBe('true');

        tick();
        fixture.destroy();
        flush();
    }));

    it('should allow to submit after the due date if the initialization date is after the due date', fakeAsync(() => {
        const submission = createFileUploadSubmission();
        submission.participation!.initializationDate = dayjs().add(1, 'days');
        (<StudentParticipation>submission.participation).exercise!.dueDate = dayjs();
        jest.spyOn(fileUploadSubmissionService, 'getDataForFileUploadEditor').mockReturnValue(of(submission));
        comp.submissionFile = new File([''], 'exampleSubmission.png');

        fixture.detectChanges();
        tick();

        expect(comp.isLate).toBeTrue();
        const submitButton = debugElement.query(By.css('jhi-button'));
        expect(submitButton).toBeDefined();
        expect(submitButton.attributes['ng-reflect-disabled']).toBe('false');

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
        expect(submitButton.attributes['ng-reflect-disabled']).toBe('true');

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

        expect(comp.isActive).toBeTrue();

        comp.fileUploadExercise.dueDate = dayjs().subtract(1, 'days');

        fixture.detectChanges();
        tick();

        expect(comp.isActive).toBeFalse();

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

        comp.result = new Result();
        comp.result.feedbacks = feedbacks;

        const unreferencedFeedback = comp.unreferencedFeedback;

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
        comp.submission = submission;

        expect(comp.canDeactivate()).toBeTrue();

        comp.submissionFile = new File([''], 'exampleSubmission.png');

        expect(comp.canDeactivate()).toBeTrue();

        submission.submitted = false;
        comp.submission = submission;

        expect(comp.canDeactivate()).toBeFalse();
    });

    it('should set alert correctly', () => {
        // Ignore window confirm
        window.confirm = () => {
            return false;
        };
        const fileName = 'exampleSubmission';
        comp.submissionFile = new File([''], fileName, { type: 'application/pdf' });
        const submission = createFileUploadSubmission();
        submission.filePath = 'test/exampleSubmission.pdf';
        submission.participation = new StudentParticipation();
        submission.participation.exercise = fileUploadExercise;
        submission.participation.exercise.exerciseGroup = new ExerciseGroup();
        const jhiWarningSpy = jest.spyOn(alertService, 'warning');
        jest.spyOn(fileUploadSubmissionService, 'getDataForFileUploadEditor').mockReturnValue(of(submission));
        fixture.detectChanges();

        comp.submitExercise();

        expect(comp.isActive).toBeFalse();
        expect(jhiWarningSpy).toHaveBeenCalledWith('artemisApp.fileUploadExercise.submitDueDateMissed');

        submission.participation.exercise = undefined;
        comp.ngOnInit();
        fixture.detectChanges();
        comp.submitExercise();

        expect(comp.isActive).toBeFalse();
        expect(jhiWarningSpy).toHaveBeenCalledWith('artemisApp.fileUploadExercise.submitDueDateMissed');
    });

    it('should set file name and type correctly', () => {
        const fileName = 'exampleSubmission';
        comp.submissionFile = new File([''], fileName, { type: 'application/pdf' });
        const submission = createFileUploadSubmission();
        submission.filePath = 'test/exampleSubmission.pdf';
        comp.submission = submission;
        jest.spyOn(fileUploadSubmissionService, 'getDataForFileUploadEditor').mockReturnValue(of(submission));
        fixture.detectChanges();

        comp.submitExercise();

        expect(comp.submittedFileName).toBe(fileName + '.pdf');
        expect(comp.submittedFileExtension).toBe('pdf');
    });

    it('should be set up with input values if present instead of loading new values from server', () => {
        // @ts-ignore method is private
        const setUpComponentWithInputValuesSpy = jest.spyOn(comp, 'setupComponentWithInputValues');
        const getDataForFileUploadEditorSpy = jest.spyOn(fileUploadSubmissionService, 'getDataForFileUploadEditor');
        const fileUploadSubmission = createFileUploadSubmission();
        fileUploadSubmission.submitted = true;
        comp.inputExercise = fileUploadExercise;
        comp.inputSubmission = fileUploadSubmission;
        comp.inputParticipation = fileUploadParticipation;

        fixture.detectChanges();

        expect(setUpComponentWithInputValuesSpy).toHaveBeenCalledOnce();
        expect(comp.fileUploadExercise).toEqual(fileUploadExercise);
        expect(comp.submission).toEqual(fileUploadSubmission);
        expect(comp.participation).toEqual(fileUploadParticipation);

        // should not fetch additional information from server, reason for input values!
        expect(getDataForFileUploadEditorSpy).not.toHaveBeenCalled();
    });
});
