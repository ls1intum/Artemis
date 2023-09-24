import { ChangeDetectorRef } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SafeHtml } from '@angular/platform-browser';
import { TranslateService } from '@ngx-translate/core';
import { Course } from 'app/entities/course.model';
import { FullscreenComponent } from 'app/shared/fullscreen/fullscreen.component';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { ResizeableContainerComponent } from 'app/shared/resizeable-container/resizeable-container.component';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { MockTranslateService, TranslatePipeMock } from '../../../../helpers/mocks/service/mock-translate.service';
import { ArtemisTestModule } from '../../../../test.module';
import { IncludedInScoreBadgeComponent } from 'app/exercises/shared/exercise-headers/included-in-score-badge.component';
import { FileUploadSubmission } from 'app/entities/file-upload-submission.model';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';
import { FileUploadExamSubmissionComponent } from 'app/exam/participate/exercises/file-upload/file-upload-exam-submission.component';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { stringifyCircular } from 'app/shared/util/utils';
import { FileUploadSubmissionService } from 'app/exercises/file-upload/participate/file-upload-submission.service';
import { HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { ExamExerciseUpdateHighlighterComponent } from 'app/exam/participate/exercises/exam-exercise-update-highlighter/exam-exercise-update-highlighter.component';
import { FileUploadStageComponent } from 'app/exercises/file-upload/stage/file-upload-stage.module';

describe('FileUploadExamSubmissionComponent', () => {
    let fixture: ComponentFixture<FileUploadExamSubmissionComponent>;
    let comp: FileUploadExamSubmissionComponent;
    let mockSubmission: FileUploadSubmission;
    let mockExercise: FileUploadExercise;
    let fileUploadSubmissionService: FileUploadSubmissionService;

    const resetComponent = () => {
        if (comp) {
            mockSubmission = { filePath: 'path/to/the/file/file1.png' } as FileUploadSubmission;
            const course = new Course();
            course.isAtLeastInstructor = true;
            const exerciseGroup = { id: 4 } as ExerciseGroup;
            mockExercise = new FileUploadExercise(undefined, exerciseGroup);
            mockExercise.filePattern = 'png,pdf';
            mockExercise.problemStatement = 'Test Problem Statement';
            comp.exercise = mockExercise;
            comp.studentSubmission = mockSubmission;
        }
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                FileUploadExamSubmissionComponent,
                FullscreenComponent,
                ResizeableContainerComponent,
                TranslatePipeMock,
                MockPipe(HtmlForMarkdownPipe, (markdown) => markdown as SafeHtml),
                MockComponent(IncludedInScoreBadgeComponent),
                MockComponent(ExamExerciseUpdateHighlighterComponent),
                MockComponent(FileUploadStageComponent),
            ],
            providers: [MockProvider(ChangeDetectorRef), { provide: TranslateService, useClass: MockTranslateService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(FileUploadExamSubmissionComponent);
                comp = fixture.componentInstance;
                fileUploadSubmissionService = fixture.debugElement.injector.get(FileUploadSubmissionService);
            });
    });

    describe('With exercise', () => {
        beforeEach(() => {
            resetComponent();
        });
        it('should initialize', () => {
            fixture.detectChanges();
            expect(FileUploadExamSubmissionComponent).not.toBeNull();
        });

        it('should show exercise group title', () => {
            comp.exercise.exerciseGroup = { title: 'Test Group' } as ExerciseGroup;
            fixture.detectChanges();
            const el = fixture.debugElement.query((de) => de.nativeElement.textContent === comp.exercise.exerciseGroup?.title);
            expect(el).not.toBeNull();
        });

        it('should show exercise max score if any', () => {
            const maxScore = 30;
            comp.exercise.maxPoints = maxScore;
            fixture.detectChanges();
            const el = fixture.debugElement.query((de) => de.nativeElement.textContent.includes(`[${maxScore} artemisApp.examParticipation.points]`));
            expect(el).not.toBeNull();
        });

        it('should show exercise bonus score if any', () => {
            const maxScore = 40;
            comp.exercise.maxPoints = maxScore;
            const bonusPoints = 55;
            comp.exercise.bonusPoints = bonusPoints;
            fixture.detectChanges();
            const el = fixture.debugElement.query((de) =>
                de.nativeElement.textContent.includes(`[${maxScore} artemisApp.examParticipation.points, ${bonusPoints} artemisApp.examParticipation.bonus]`),
            );
            expect(el).not.toBeNull();
        });
        it('should show problem statement if there is any', () => {
            fixture.detectChanges();
            const el = fixture.debugElement.query((de) => de.nativeElement.textContent === mockExercise.problemStatement);
            expect(el).not.toBeNull();
        });
    });

    describe('getSubmission', () => {
        beforeEach(() => {
            resetComponent();
        });
        it('should return student submission', () => {
            expect(comp.getSubmission()).toEqual(mockSubmission);
        });
    });

    describe('getExercise', () => {
        beforeEach(() => {
            resetComponent();
        });
        it('should return exercise', () => {
            expect(comp.getExercise()).toEqual(mockExercise);
        });
    });

    describe('updateProblemStatement', () => {
        beforeEach(() => {
            resetComponent();
        });
        it('should update problem statement', () => {
            const newProblemStatement = 'new problem statement';
            comp.updateProblemStatement(newProblemStatement);
            expect(comp.getExercise().problemStatement).toEqual(newProblemStatement);
        });
    });

    describe('updateSubmissionFromView', () => {
        beforeEach(() => {
            resetComponent();
            fixture.detectChanges();
        });
        afterEach(() => {
            jest.restoreAllMocks();
        });
        it('should do nothing', () => {
            const jsonOfComponent = stringifyCircular(comp);
            comp.updateSubmissionFromView();
            expect(stringifyCircular(comp)).toEqual(jsonOfComponent);
        });
    });

    describe('hasUnsavedChanges', () => {
        beforeEach(() => {
            resetComponent();
        });
        it('should return true if isSynced false', () => {
            comp.studentSubmission.isSynced = false;
            expect(comp.hasUnsavedChanges()).toBeTrue();
        });
        it('should return false if isSynced true', () => {
            comp.studentSubmission.isSynced = true;
            expect(comp.hasUnsavedChanges()).toBeFalse();
        });
    });

    describe('saveUploadedFile', () => {
        beforeEach(() => {
            resetComponent();
            fixture.detectChanges();
        });
        afterEach(() => {
            jest.restoreAllMocks();
        });
        it('should just return if submissionFile is undefined', () => {
            const updateStub = jest.spyOn(fileUploadSubmissionService, 'update');
            comp.saveUploadedFile();
            expect(updateStub).not.toHaveBeenCalled();
        });

        it('should save if submissionFile is defined', () => {
            const newFilePath = 'new/path/image.png';
            const updateStub = jest.spyOn(fileUploadSubmissionService, 'update').mockReturnValue(of(new HttpResponse({ body: { id: 1, filePaths: [newFilePath] } })));
            comp.stagedFiles = [new File([], 'name.png')];
            expect(comp.studentSubmission.filePaths).toBeUndefined();
            comp.saveUploadedFile();
            expect(updateStub).toHaveBeenCalledOnce();
            expect(comp.studentSubmission.filePaths![0]).toEqual(newFilePath);
        });
    });
});
