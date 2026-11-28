import { beforeEach, describe, expect, it, vi } from 'vitest';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { TumAetUiDialogService } from '@tumaet/ui-angular/dialog';
import { AlertService } from 'app/foundation/service/alert.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { QuizQuestionListEditComponent } from 'app/quiz/manage/list-edit/quiz-question-list-edit.component';
import { CommonModule } from '@angular/common';
import { QuizQuestionListEditExistingComponent } from 'app/quiz/manage/list-edit-existing/quiz-question-list-edit-existing.component';
import { MultipleChoiceQuestion } from 'app/quiz/shared/entities/multiple-choice-question.model';
import { ShortAnswerQuestion } from 'app/quiz/shared/entities/short-answer-question.model';
import { provideHttpClient } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('QuizQuestionListEditComponent', () => {
    let fixture: ComponentFixture<QuizQuestionListEditComponent>;
    let component: QuizQuestionListEditComponent;

    const fileName1 = 'test1.jpg';
    const file1 = new File([], fileName1);
    const fileName2 = 'test2.jpg';
    const file2 = new File([], fileName2);
    const fileName3 = 'test3.png';
    const file3 = new File([], fileName3);

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [
                CommonModule,
                QuizQuestionListEditComponent,
                MockComponent(QuizQuestionListEditExistingComponent),
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockDirective(TranslateDirective),
            ],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }, MockProvider(TumAetUiDialogService), provideHttpClient(), provideHttpClientTesting()],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(QuizQuestionListEditComponent);
                component = fixture.componentInstance;
                // Set required input before detectChanges
                fixture.componentRef.setInput('courseId', 1);
                fixture.detectChanges();
            });
    });

    it('should add multiple choice question to quizQuestions and emit onQuestionAdded Output', () => {
        const onQuestionAddedEmit = vi.spyOn(component.onQuestionAdded, 'emit');
        component.addMultipleChoiceQuestion();
        expect(component.quizQuestions()).toHaveLength(1);
        expect(onQuestionAddedEmit).toHaveBeenCalledOnce();
    });

    it('should open the Apollon import dialog without a header and pass the course id', async () => {
        const dialogService = TestBed.inject(TumAetUiDialogService);
        const openSpy = vi.spyOn(dialogService, 'open');

        await component.importApollonDragAndDropQuestion();

        expect(openSpy).toHaveBeenCalledWith(
            expect.anything(),
            expect.objectContaining({
                size: 'large',
                contentPadding: 'none',
                showHeader: false,
                ariaLabel: 'artemisApp.quizExercise.addApollonDragAndDropQuestion',
                data: { courseId: component.courseId() },
            }),
        );
    });

    describe('loading of the Apollon import dialog', () => {
        // The dialog hosts the diagram editor and is loaded when it is opened. Until it has loaded, the button is still active.
        function holdBackImportDialog() {
            let finishDownload!: (dialog: unknown) => void;
            const download = new Promise<unknown>((resolve) => (finishDownload = resolve));
            const loadSpy = vi.spyOn(component as unknown as { loadImportDialog: () => Promise<unknown> }, 'loadImportDialog').mockReturnValue(download);
            return { loadSpy, finishDownload };
        }

        it('opens one dialog when the button is clicked twice while the dialog loads', async () => {
            const openSpy = vi.spyOn(TestBed.inject(TumAetUiDialogService), 'open').mockReturnValue(undefined as never);
            const { loadSpy, finishDownload } = holdBackImportDialog();

            const first = component.importApollonDragAndDropQuestion();
            const second = component.importApollonDragAndDropQuestion();
            finishDownload(class {});
            await Promise.all([first, second]);

            expect(loadSpy).toHaveBeenCalledOnce();
            expect(openSpy).toHaveBeenCalledOnce();
        });

        it('can be used again after the dialog was opened', async () => {
            const openSpy = vi.spyOn(TestBed.inject(TumAetUiDialogService), 'open').mockReturnValue(undefined as never);
            vi.spyOn(component as unknown as { loadImportDialog: () => Promise<unknown> }, 'loadImportDialog').mockResolvedValue(class {});

            await component.importApollonDragAndDropQuestion();
            await component.importApollonDragAndDropQuestion();

            expect(openSpy).toHaveBeenCalledTimes(2);
        });

        it('does not open the dialog when the quiz editor was destroyed while it loaded', async () => {
            const openSpy = vi.spyOn(TestBed.inject(TumAetUiDialogService), 'open').mockReturnValue(undefined as never);
            const { finishDownload } = holdBackImportDialog();

            const importing = component.importApollonDragAndDropQuestion();
            fixture.destroy();
            finishDownload(class {});
            await importing;

            expect(openSpy).not.toHaveBeenCalled();
        });

        it('shows an error and can be used again when the dialog cannot be loaded', async () => {
            const alertSpy = vi.spyOn(TestBed.inject(AlertService), 'error');
            const openSpy = vi.spyOn(TestBed.inject(TumAetUiDialogService), 'open').mockReturnValue(undefined as never);
            const loadSpy = vi.spyOn(component as unknown as { loadImportDialog: () => Promise<unknown> }, 'loadImportDialog').mockRejectedValueOnce(new Error('offline'));

            await component.importApollonDragAndDropQuestion();

            expect(alertSpy).toHaveBeenCalledExactlyOnceWith('artemisApp.modelingEditor.loadFailed');
            expect(openSpy).not.toHaveBeenCalled();

            loadSpy.mockResolvedValue(class {});
            await component.importApollonDragAndDropQuestion();
            expect(openSpy).toHaveBeenCalledOnce();
        });
    });

    it('should add drag and drop question to quizQuestions and emit onQuestionAdded Output', () => {
        const onQuestionAddedEmit = vi.spyOn(component.onQuestionAdded, 'emit');
        component.addDragAndDropQuestion();
        expect(component.quizQuestions()).toHaveLength(1);
        expect(onQuestionAddedEmit).toHaveBeenCalledOnce();
    });

    it('should add short answer question to quizQuestions and emit onQuestionAdded Output', () => {
        const onQuestionAddedEmit = vi.spyOn(component.onQuestionAdded, 'emit');
        component.addShortAnswerQuestion();
        expect(component.quizQuestions()).toHaveLength(1);
        expect(onQuestionAddedEmit).toHaveBeenCalledOnce();
    });

    it('should toggle show hide existing questions flag', () => {
        component.showHideExistingQuestions();
        expect(component.showExistingQuestions()).toBe(true);
        component.showHideExistingQuestions();
        expect(component.showExistingQuestions()).toBe(false);
    });

    it('should add existing quiz questions to quizQuestions and toggle show hide existing questions flag', () => {
        const question0 = new MultipleChoiceQuestion();
        const question1 = new ShortAnswerQuestion();
        component.showExistingQuestions.set(true);
        component.handleExistingQuestionsAdded([question0, question1]);
        expect(component.showExistingQuestions()).toBe(false);
        expect(component.quizQuestions()).toHaveLength(2);
        expect(component.quizQuestions()[0]).toEqual(question0);
        expect(component.quizQuestions()[1]).toEqual(question1);
    });

    it('should emit onQuestionUpdated Output', () => {
        const onQuestionUpdatedEmit = vi.spyOn(component.onQuestionUpdated, 'emit');
        component.handleQuestionUpdated();
        expect(onQuestionUpdatedEmit).toHaveBeenCalledOnce();
    });

    it('should delete question and emit onQuestionUpdated Output', () => {
        const question0 = new MultipleChoiceQuestion();
        const question1 = new ShortAnswerQuestion();
        fixture.componentRef.setInput('quizQuestions', [question0, question1]);
        const onQuestionDeletedEmit = vi.spyOn(component.onQuestionDeleted, 'emit');
        component.handleQuestionDeleted(question0);
        expect(onQuestionDeletedEmit).toHaveBeenCalledOnce();
        expect(component.quizQuestions()).toHaveLength(1);
        expect(component.quizQuestions()[0]).toEqual(question1);
    });

    it('should add file', () => {
        const path = 'this/is/a/path/to/a/file.png';
        component.handleFileAdded({ fileName: fileName1, file: file1 });
        component.handleFileAdded({ fileName: fileName2, file: file2, path });

        expect(component.fileMap).toEqual(
            new Map<string, { file: File; path?: string }>([
                [fileName1, { file: file1 }],
                [fileName2, { file: file2, path }],
            ]),
        );
    });

    it('should remove file', () => {
        component.fileMap = new Map<string, { file: File; path?: string }>([
            [fileName1, { file: file1 }],
            [fileName2, { file: file2 }],
            [fileName3, { file: file3 }],
        ]);
        component.handleFileRemoved(fileName2);
        expect(component.fileMap).toEqual(
            new Map<string, { file: File; path?: string }>([
                [fileName1, { file: file1 }],
                [fileName3, { file: file3 }],
            ]),
        );
    });
});
