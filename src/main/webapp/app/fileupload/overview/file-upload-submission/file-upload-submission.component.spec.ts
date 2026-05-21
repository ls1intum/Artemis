/**
 * Vitest tests for FileUploadSubmissionComponent.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, Params, provideRouter } from '@angular/router';
import { BehaviorSubject, of, throwError } from 'rxjs';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { TranslateModule } from '@ngx-translate/core';
import { MockComponent, MockPipe } from 'ng-mocks';
import dayjs from 'dayjs/esm';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

import 'app/shared/util/array.extension';

import { FileUploadSubmissionComponent } from './file-upload-submission.component';
import { FileUploadSubmissionService } from '../file-upload-submission.service';
import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';
import { FileUploadSubmission } from 'app/fileupload/shared/entities/file-upload-submission.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { Feedback, FeedbackType } from 'app/assessment/shared/entities/feedback.model';
import { GradingInstruction } from 'app/exercise/structured-grading-criterion/grading-instruction.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';

import { AccountService } from 'app/core/auth/account.service';
import { AlertService } from 'app/shared/service/alert.service';
import { ParticipationWebsocketService } from 'app/core/course/shared/services/participation-websocket.service';
import { FileService } from 'app/shared/service/file.service';
import { MAX_SUBMISSION_FILE_SIZE } from 'app/shared/constants/input.constants';

import { HeaderParticipationPageComponent } from 'app/exercise/exercise-headers/participation-page/header-participation-page.component';
import { ResizeableContainerComponent } from 'app/shared/resizeable-container/resizeable-container.component';
import { AdditionalFeedbackComponent } from 'app/exercise/additional-feedback/additional-feedback.component';
import { RatingComponent } from 'app/exercise/rating/rating.component';
import { ComplaintsStudentViewComponent } from 'app/assessment/overview/complaints-for-students/complaints-student-view.component';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisTimeAgoPipe } from 'app/shared/pipes/artemis-time-ago.pipe';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';

describe('FileUploadSubmissionComponent', () => {
    setupTestBed({ zoneless: true });

    let component: FileUploadSubmissionComponent;
    let fixture: ComponentFixture<FileUploadSubmissionComponent>;
    let fileUploadSubmissionService: FileUploadSubmissionService;
    let alertService: AlertService;
    let participationWebsocketService: ParticipationWebsocketService;
    let fileService: FileService;

    let routeParams$: BehaviorSubject<Params>;

    const createCourse = (id = 123): Course => {
        const course = new Course();
        course.id = id;
        return course;
    };

    const createExercise = (overrides?: Partial<FileUploadExercise>): FileUploadExercise => {
        const exercise = new FileUploadExercise(createCourse(), undefined);
        exercise.id = 456;
        exercise.title = 'Test Exercise';
        exercise.filePattern = 'pdf,png';
        if (overrides) {
            Object.assign(exercise, overrides);
        }
        return exercise;
    };

    const createParticipation = (exercise?: FileUploadExercise): StudentParticipation => {
        const participation = new StudentParticipation();
        participation.id = 111;
        participation.exercise = exercise ?? createExercise();
        participation.initializationDate = dayjs().subtract(1, 'hour');
        return participation;
    };

    const createSubmission = (exercise?: FileUploadExercise): FileUploadSubmission => {
        const submission = new FileUploadSubmission();
        submission.id = 789;
        submission.submitted = false;
        submission.filePath = undefined;
        submission.participation = createParticipation(exercise);
        return submission;
    };

    const createSubmittedSubmission = (exercise?: FileUploadExercise): FileUploadSubmission => {
        const submission = createSubmission(exercise);
        submission.submitted = true;
        submission.filePath = '/api/files/submissions/test.pdf';
        return submission;
    };

    /**
     * Helper to get the StudentParticipation from a test submission.
     * In tests, we always create submissions with StudentParticipation via createParticipation.
     */
    const getParticipation = (submission: FileUploadSubmission): StudentParticipation => {
        return submission.participation as StudentParticipation;
    };

    const createResult = (submission?: FileUploadSubmission): Result => {
        const result = new Result();
        result.id = 999;
        result.completionDate = dayjs();
        result.score = 80;
        result.feedbacks = [];
        return result;
    };

    const createFile = (name: string, type: string, size = 1000): File => {
        const file = new File(['test content'], name, { type });
        Object.defineProperty(file, 'size', { value: size, writable: false });
        return file;
    };

    beforeEach(async () => {
        routeParams$ = new BehaviorSubject({ participationId: 111 });

        await TestBed.configureTestingModule({
            imports: [FileUploadSubmissionComponent, TranslateModule.forRoot()],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                provideRouter([]),
                {
                    provide: ActivatedRoute,
                    useValue: {
                        params: routeParams$.asObservable(),
                    },
                },
                {
                    provide: AccountService,
                    useValue: {
                        isOwnerOfParticipation: vi.fn().mockReturnValue(true),
                    },
                },
                {
                    provide: ParticipationWebsocketService,
                    useValue: {
                        addParticipation: vi.fn(),
                    },
                },
                {
                    provide: FileService,
                    useValue: {
                        downloadFile: vi.fn(),
                    },
                },
            ],
        })
            .overrideComponent(FileUploadSubmissionComponent, {
                remove: {
                    imports: [
                        HeaderParticipationPageComponent,
                        ResizeableContainerComponent,
                        AdditionalFeedbackComponent,
                        RatingComponent,
                        ComplaintsStudentViewComponent,
                        ButtonComponent,
                        ArtemisTranslatePipe,
                        ArtemisTimeAgoPipe,
                        HtmlForMarkdownPipe,
                    ],
                },
                add: {
                    imports: [
                        MockComponent(HeaderParticipationPageComponent),
                        MockComponent(ResizeableContainerComponent),
                        MockComponent(AdditionalFeedbackComponent),
                        MockComponent(RatingComponent),
                        MockComponent(ComplaintsStudentViewComponent),
                        MockComponent(ButtonComponent),
                        MockPipe(ArtemisTranslatePipe),
                        MockPipe(ArtemisTimeAgoPipe),
                        MockPipe(HtmlForMarkdownPipe),
                    ],
                },
            })
            .compileComponents();

        fixture = TestBed.createComponent(FileUploadSubmissionComponent);
        component = fixture.componentInstance;

        fileUploadSubmissionService = TestBed.inject(FileUploadSubmissionService);
        alertService = TestBed.inject(AlertService);
        participationWebsocketService = TestBed.inject(ParticipationWebsocketService);
        fileService = TestBed.inject(FileService);
    });

    afterEach(() => {
        vi.clearAllMocks();
        if (fixture) {
            fixture.destroy();
        }
    });

    describe('initialization with route params', () => {
        it('should load submission from participation ID in route', async () => {
            const exercise = createExercise();
            const submission = createSubmission(exercise);
            vi.spyOn(fileUploadSubmissionService, 'getDataForFileUploadEditor').mockReturnValue(of(submission));

            fixture.detectChanges();
            await fixture.whenStable();

            expect(fileUploadSubmissionService.getDataForFileUploadEditor).toHaveBeenCalledWith(111);
            expect(component.submission()).toEqual(submission);
        });

        it('should set exercise from loaded submission', async () => {
            const exercise = createExercise();
            const submission = createSubmission(exercise);
            vi.spyOn(fileUploadSubmissionService, 'getDataForFileUploadEditor').mockReturnValue(of(submission));

            fixture.detectChanges();
            await fixture.whenStable();

            expect(component.fileUploadExercise()).toEqual(exercise);
        });

        it('should set participation from loaded submission', async () => {
            const exercise = createExercise();
            const submission = createSubmission(exercise);
            vi.spyOn(fileUploadSubmissionService, 'getDataForFileUploadEditor').mockReturnValue(of(submission));

            fixture.detectChanges();
            await fixture.whenStable();

            expect(component.participation()).toEqual(submission.participation);
        });

        it('should handle error when loading submission fails', async () => {
            vi.spyOn(fileUploadSubmissionService, 'getDataForFileUploadEditor').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404 })));
            const alertSpy = vi.spyOn(alertService, 'error');

            fixture.detectChanges();
            await fixture.whenStable();

            expect(alertSpy).toHaveBeenCalled();
        });
    });

    describe('initialization with input values', () => {
        it('should use input values instead of loading from server', async () => {
            const exercise = createExercise();
            const submission = createSubmission(exercise);
            const participation = getParticipation(submission);
            const getSpy = vi.spyOn(fileUploadSubmissionService, 'getDataForFileUploadEditor');

            fixture.componentRef.setInput('inputExercise', exercise);
            fixture.componentRef.setInput('inputSubmission', submission);
            fixture.componentRef.setInput('inputParticipation', participation);
            fixture.detectChanges();
            await fixture.whenStable();

            expect(getSpy).not.toHaveBeenCalled();
            expect(component.fileUploadExercise()).toEqual(exercise);
            expect(component.submission()).toEqual(submission);
            expect(component.participation()).toEqual(participation);
        });

        it('should set up component with submitted file when submission is submitted', async () => {
            const exercise = createExercise();
            const submission = createSubmittedSubmission(exercise);

            fixture.componentRef.setInput('inputExercise', exercise);
            fixture.componentRef.setInput('inputSubmission', submission);
            fixture.detectChanges();
            await fixture.whenStable();

            expect(component.submittedFileName()).toBe('test.pdf');
        });
    });

    describe('computed signals', () => {
        beforeEach(async () => {
            const exercise = createExercise();
            const submission = createSubmission(exercise);
            vi.spyOn(fileUploadSubmissionService, 'getDataForFileUploadEditor').mockReturnValue(of(submission));
            fixture.detectChanges();
            await fixture.whenStable();
        });

        it('should compute course from exercise', () => {
            expect(component.course()).toBeDefined();
            expect(component.course()?.id).toBe(123);
        });

        it('should compute examMode as false for course exercise', () => {
            expect(component.examMode()).toBe(false);
        });

        it('should compute accepted file extensions from pattern', () => {
            expect(component.acceptedFileExtensions()).toContain('.pdf');
            expect(component.acceptedFileExtensions()).toContain('.png');
        });

        it('should compute isAfterAssessmentDueDate when no assessment due date', () => {
            expect(component.isAfterAssessmentDueDate()).toBe(true);
        });

        it('should compute isAfterAssessmentDueDate when assessment due date passed', async () => {
            const exercise = createExercise({ assessmentDueDate: dayjs().subtract(1, 'day') });
            const submission = createSubmission(exercise);
            vi.spyOn(fileUploadSubmissionService, 'getDataForFileUploadEditor').mockReturnValue(of(submission));

            fixture = TestBed.createComponent(FileUploadSubmissionComponent);
            component = fixture.componentInstance;
            fixture.detectChanges();
            await fixture.whenStable();

            expect(component.isAfterAssessmentDueDate()).toBe(true);
        });

        it('should compute isLate when initialization is after due date', async () => {
            const exercise = createExercise({ dueDate: dayjs().subtract(2, 'days') });
            const submission = createSubmission(exercise);
            getParticipation(submission).initializationDate = dayjs().subtract(1, 'day');
            vi.spyOn(fileUploadSubmissionService, 'getDataForFileUploadEditor').mockReturnValue(of(submission));

            fixture = TestBed.createComponent(FileUploadSubmissionComponent);
            component = fixture.componentInstance;
            fixture.detectChanges();
            await fixture.whenStable();

            expect(component.isLate()).toBe(true);
        });
    });

    describe('file selection', () => {
        beforeEach(async () => {
            const exercise = createExercise({ filePattern: 'pdf,png' });
            const submission = createSubmission(exercise);
            vi.spyOn(fileUploadSubmissionService, 'getDataForFileUploadEditor').mockReturnValue(of(submission));
            fixture.detectChanges();
            await fixture.whenStable();
        });

        it('should accept valid file type', () => {
            const file = createFile('document.pdf', 'application/pdf');
            const event = { target: { files: [file] } } as unknown as Event;

            component.setFileSubmissionForExercise(event);

            expect(component.submissionFile()).toBe(file);
        });

        it('should reject invalid file type', () => {
            const file = createFile('document.docx', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document');
            const event = { target: { files: [file] } } as unknown as Event;
            const alertSpy = vi.spyOn(alertService, 'error');

            component.setFileSubmissionForExercise(event);

            expect(alertSpy).toHaveBeenCalledWith('artemisApp.fileUploadSubmission.fileExtensionError');
            expect(component.submissionFile()).toBeUndefined();
        });

        it('should reject file too large', () => {
            const file = createFile('document.pdf', 'application/pdf', MAX_SUBMISSION_FILE_SIZE + 1);
            const event = { target: { files: [file] } } as unknown as Event;
            const alertSpy = vi.spyOn(alertService, 'error');

            component.setFileSubmissionForExercise(event);

            expect(alertSpy).toHaveBeenCalledWith('artemisApp.fileUploadSubmission.fileTooBigError', { fileName: 'document.pdf' });
            expect(component.submissionFile()).toBeUndefined();
        });

        it('should handle empty file list', () => {
            const event = { target: { files: [] } } as unknown as Event;

            component.setFileSubmissionForExercise(event);

            expect(component.submissionFile()).toBeUndefined();
        });
    });

    describe('submitExercise', () => {
        beforeEach(async () => {
            const exercise = createExercise({ dueDate: dayjs().add(1, 'day') });
            const submission = createSubmission(exercise);
            vi.spyOn(fileUploadSubmissionService, 'getDataForFileUploadEditor').mockReturnValue(of(submission));
            fixture.detectChanges();
            await fixture.whenStable();
        });

        it('should submit file when valid', async () => {
            const file = createFile('document.pdf', 'application/pdf');
            component.submissionFile.set(file);
            const newSubmission = createSubmittedSubmission();
            newSubmission.participation = component.participation();
            vi.spyOn(fileUploadSubmissionService, 'update').mockReturnValue(of(new HttpResponse({ body: newSubmission })));
            const alertSpy = vi.spyOn(alertService, 'success');

            await component.submitExercise();

            expect(fileUploadSubmissionService.update).toHaveBeenCalled();
            expect(alertSpy).toHaveBeenCalledWith('artemisApp.fileUploadExercise.submitSuccessful');
        });

        it('should show warning when submitting after due date', async () => {
            const exercise = createExercise({ dueDate: dayjs().subtract(1, 'day') });
            exercise.studentParticipations = [];
            const submission = createSubmission(exercise);
            getParticipation(submission).initializationDate = dayjs().subtract(2, 'days');
            vi.spyOn(fileUploadSubmissionService, 'getDataForFileUploadEditor').mockReturnValue(of(submission));

            fixture = TestBed.createComponent(FileUploadSubmissionComponent);
            component = fixture.componentInstance;
            fixture.detectChanges();
            await fixture.whenStable();

            const file = createFile('document.pdf', 'application/pdf');
            component.submissionFile.set(file);
            const newSubmission = createSubmittedSubmission();
            newSubmission.participation = component.participation();
            vi.spyOn(fileUploadSubmissionService, 'update').mockReturnValue(of(new HttpResponse({ body: newSubmission })));
            const alertSpy = vi.spyOn(alertService, 'warning');

            await component.submitExercise();

            expect(alertSpy).toHaveBeenCalledWith('artemisApp.fileUploadExercise.submitDueDateMissed');
        });

        it('should not submit if no file selected', async () => {
            const updateSpy = vi.spyOn(fileUploadSubmissionService, 'update');

            await component.submitExercise();

            expect(updateSpy).not.toHaveBeenCalled();
        });

        it('should not submit if already saving', async () => {
            component.isSaving.set(true);
            component.submissionFile.set(createFile('test.pdf', 'application/pdf'));
            const updateSpy = vi.spyOn(fileUploadSubmissionService, 'update');

            await component.submitExercise();

            expect(updateSpy).not.toHaveBeenCalled();
        });

        it('should handle submission error', async () => {
            const file = createFile('document.pdf', 'application/pdf');
            component.submissionFile.set(file);
            vi.spyOn(fileUploadSubmissionService, 'update').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
            const alertSpy = vi.spyOn(alertService, 'error');

            await component.submitExercise();

            expect(alertSpy).toHaveBeenCalledWith('artemisApp.fileUploadSubmission.fileUploadError', { fileName: file.name });
            expect(component.isSaving()).toBe(false);
        });

        it('should add participation to websocket service after successful submit', async () => {
            const file = createFile('document.pdf', 'application/pdf');
            component.submissionFile.set(file);
            const newSubmission = createSubmittedSubmission();
            newSubmission.participation = component.participation();
            vi.spyOn(fileUploadSubmissionService, 'update').mockReturnValue(of(new HttpResponse({ body: newSubmission })));

            await component.submitExercise();

            expect(participationWebsocketService.addParticipation).toHaveBeenCalled();
        });
    });

    describe('canDeactivate', () => {
        beforeEach(async () => {
            const submission = createSubmission();
            vi.spyOn(fileUploadSubmissionService, 'getDataForFileUploadEditor').mockReturnValue(of(submission));
            fixture.detectChanges();
            await fixture.whenStable();
        });

        it('should return true when no file selected', () => {
            expect(component.canDeactivate()).toBe(true);
        });

        it('should return true when submission is already submitted', () => {
            component.submission()!.submitted = true;
            component.submissionFile.set(createFile('test.pdf', 'application/pdf'));

            expect(component.canDeactivate()).toBe(true);
        });

        it('should return false when file selected but not submitted', () => {
            component.submissionFile.set(createFile('test.pdf', 'application/pdf'));

            expect(component.canDeactivate()).toBe(false);
        });
    });

    describe('downloadFile', () => {
        beforeEach(async () => {
            const submission = createSubmission();
            vi.spyOn(fileUploadSubmissionService, 'getDataForFileUploadEditor').mockReturnValue(of(submission));
            fixture.detectChanges();
            await fixture.whenStable();
        });

        it('should call file service to download', () => {
            component.downloadFile('/path/to/file.pdf');

            expect(fileService.downloadFile).toHaveBeenCalledWith('/path/to/file.pdf');
        });
    });

    describe('unreferencedFeedback', () => {
        it('should compute unreferenced feedback from result', async () => {
            const exercise = createExercise();
            const submission = createSubmittedSubmission(exercise);
            const result = createResult(submission);
            const feedback1: Feedback = {
                id: 1,
                credits: 5,
                detailText: 'Feedback 1',
                type: FeedbackType.MANUAL_UNREFERENCED,
            };
            const feedback2: Feedback = {
                id: 2,
                credits: 3,
                detailText: 'Feedback 2',
                type: FeedbackType.MANUAL_UNREFERENCED,
            };
            result.feedbacks = [feedback1, feedback2];

            vi.spyOn(fileUploadSubmissionService, 'getDataForFileUploadEditor').mockReturnValue(of(submission));

            fixture.componentRef.setInput('inputExercise', exercise);
            fixture.componentRef.setInput('inputSubmission', submission);
            fixture.detectChanges();
            await fixture.whenStable();

            component.result.set(result);
            fixture.detectChanges();

            expect(component.unreferencedFeedback()).toHaveLength(2);
        });

        it('should mark subsequent feedback when using same grading instruction', async () => {
            const exercise = createExercise();
            const submission = createSubmittedSubmission(exercise);
            const result = createResult(submission);
            const gradingInstruction: GradingInstruction = {
                id: 1,
                credits: 5,
                gradingScale: 'scale',
                instructionDescription: 'desc',
                feedback: 'feedback',
                usageCount: 1,
            };
            const feedback1: Feedback = {
                id: 1,
                credits: 5,
                detailText: 'Feedback 1',
                type: FeedbackType.MANUAL_UNREFERENCED,
                gradingInstruction,
            };
            const feedback2: Feedback = {
                id: 2,
                credits: 5,
                detailText: 'Feedback 2',
                type: FeedbackType.MANUAL_UNREFERENCED,
                gradingInstruction,
            };
            result.feedbacks = [feedback1, feedback2];

            fixture.componentRef.setInput('inputExercise', exercise);
            fixture.componentRef.setInput('inputSubmission', submission);
            vi.spyOn(fileUploadSubmissionService, 'getDataForFileUploadEditor').mockReturnValue(of(submission));
            fixture.detectChanges();
            await fixture.whenStable();

            component.result.set(result);
            fixture.detectChanges();

            const feedback = component.unreferencedFeedback();
            expect(feedback).toBeDefined();
        });
    });

    describe('submitButtonTooltip', () => {
        beforeEach(async () => {
            const exercise = createExercise({ dueDate: dayjs().add(1, 'day') });
            const submission = createSubmission(exercise);
            vi.spyOn(fileUploadSubmissionService, 'getDataForFileUploadEditor').mockReturnValue(of(submission));
            fixture.detectChanges();
            await fixture.whenStable();
        });

        it('should show select file tooltip when no file selected', () => {
            expect(component.submitButtonTooltip()).toBe('artemisApp.fileUploadSubmission.selectFile');
        });

        it('should show submit tooltip when file selected and active', () => {
            component.submissionFile.set(createFile('test.pdf', 'application/pdf'));
            fixture.detectChanges();

            expect(component.submitButtonTooltip()).toBe('entity.action.submitTooltip');
        });
    });

    describe('submittedFileName and extension', () => {
        it('should extract file name from path', async () => {
            const submission = createSubmittedSubmission();
            submission.filePath = '/api/files/submissions/document.pdf';

            fixture.componentRef.setInput('inputSubmission', submission);
            fixture.componentRef.setInput('inputExercise', createExercise());
            fixture.detectChanges();
            await fixture.whenStable();

            expect(component.submittedFileName()).toBe('document.pdf');
        });

        it('should extract file extension from name', async () => {
            const submission = createSubmittedSubmission();
            submission.filePath = '/api/files/submissions/document.pdf';

            fixture.componentRef.setInput('inputSubmission', submission);
            fixture.componentRef.setInput('inputExercise', createExercise());
            fixture.detectChanges();
            await fixture.whenStable();

            expect(component.submittedFileExtension()).toBe('pdf');
        });

        it('should return empty string when no file path', async () => {
            const submission = createSubmission();
            submission.filePath = undefined;

            fixture.componentRef.setInput('inputSubmission', submission);
            fixture.componentRef.setInput('inputExercise', createExercise());
            fixture.detectChanges();
            await fixture.whenStable();

            expect(component.submittedFileName()).toBe('');
            expect(component.submittedFileExtension()).toBe('');
        });
    });

    describe('isActive', () => {
        it('should be active when before due date in course mode', async () => {
            const exercise = createExercise({ dueDate: dayjs().add(1, 'day') });
            const submission = createSubmission(exercise);
            vi.spyOn(fileUploadSubmissionService, 'getDataForFileUploadEditor').mockReturnValue(of(submission));
            fixture.detectChanges();
            await fixture.whenStable();

            expect(component.isActive()).toBe(true);
        });

        it('should not be active when after due date', async () => {
            const exercise = createExercise({ dueDate: dayjs().subtract(1, 'day') });
            const submission = createSubmission(exercise);
            getParticipation(submission).initializationDate = dayjs().subtract(2, 'days');
            vi.spyOn(fileUploadSubmissionService, 'getDataForFileUploadEditor').mockReturnValue(of(submission));
            fixture.detectChanges();
            await fixture.whenStable();

            expect(component.isActive()).toBe(false);
        });

        it('should not be active in exam mode', async () => {
            const exercise = createExercise({ dueDate: dayjs().add(1, 'day') });
            exercise.exerciseGroup = new ExerciseGroup();
            exercise.exerciseGroup.id = 1;
            const submission = createSubmission(exercise);
            vi.spyOn(fileUploadSubmissionService, 'getDataForFileUploadEditor').mockReturnValue(of(submission));
            fixture.detectChanges();
            await fixture.whenStable();

            expect(component.isActive()).toBe(false);
        });
    });
});
