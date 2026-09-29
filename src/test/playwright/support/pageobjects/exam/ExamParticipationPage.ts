import { Page, expect } from '@playwright/test';
import { Course } from 'app/course/shared/entities/course.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { AdditionalData, ExerciseType } from '../../constants';
import { UserCredentials } from '../../users';
import { OnlineEditorPage, ProgrammingExerciseSubmission } from '../exercises/programming/OnlineEditorPage';
import { ExamNavigationBar } from './ExamNavigationBar';
import { ExamStartEndPage } from './ExamStartEndPage';
import { ModelingEditor } from '../exercises/modeling/ModelingEditor';
import { MultipleChoiceQuiz } from '../exercises/quiz/MultipleChoiceQuiz';
import { TextEditorPage } from '../exercises/text/TextEditorPage';
import { FileUploadEditorPage } from '../exercises/file-upload/FileUploadEditorPage';
import { Commands } from '../../commands';
import { Fixtures } from '../../../fixtures/fixtures';
import { ExamParticipationActions } from './ExamParticipationActions';
import { BUILD_RESULT_TIMEOUT } from '../../timeouts';
import { annotateRecovery, getExercise } from '../../utils';

export class ExamParticipationPage extends ExamParticipationActions {
    private readonly examNavigation: ExamNavigationBar;
    private readonly examStartEnd: ExamStartEndPage;
    private readonly modelingExerciseEditor: ModelingEditor;
    private readonly programmingExerciseEditor: OnlineEditorPage;
    private readonly quizExerciseMultipleChoice: MultipleChoiceQuiz;
    private readonly textExerciseEditor: TextEditorPage;
    private readonly fileUploadExerciseEditor: FileUploadEditorPage;

    constructor(
        examNavigation: ExamNavigationBar,
        examStartEnd: ExamStartEndPage,
        modelingExerciseEditor: ModelingEditor,
        programmingExerciseEditor: OnlineEditorPage,
        quizExerciseMultipleChoice: MultipleChoiceQuiz,
        textExerciseEditor: TextEditorPage,
        page: Page,
        fileUploadExerciseEditor: FileUploadEditorPage = new FileUploadEditorPage(page),
    ) {
        super(page);
        this.examNavigation = examNavigation;
        this.examStartEnd = examStartEnd;
        this.modelingExerciseEditor = modelingExerciseEditor;
        this.programmingExerciseEditor = programmingExerciseEditor;
        this.quizExerciseMultipleChoice = quizExerciseMultipleChoice;
        this.textExerciseEditor = textExerciseEditor;
        this.fileUploadExerciseEditor = fileUploadExerciseEditor;
    }

    async makeSubmission(exerciseID: number, exerciseType: ExerciseType, additionalData?: AdditionalData) {
        switch (exerciseType) {
            case ExerciseType.TEXT:
                await this.makeTextExerciseSubmission(exerciseID, additionalData!.textFixture!);
                break;
            case ExerciseType.MODELING:
                await this.makeModelingExerciseSubmission(exerciseID);
                break;
            case ExerciseType.QUIZ:
                await this.makeQuizExerciseSubmission(exerciseID);
                break;
            case ExerciseType.FILE_UPLOAD:
                await this.makeFileUploadExerciseSubmission(additionalData!.fileUploadFixture!);
                break;
            case ExerciseType.PROGRAMMING:
                await this.makeProgrammingExerciseSubmission(exerciseID, additionalData!.submission!, additionalData!.practiceMode, additionalData!.skipBuildResultCheck);
                break;
        }
    }

    async makeTextExerciseSubmission(exerciseID: number, textFixture: string) {
        const content = await Fixtures.get(textFixture);
        await this.textExerciseEditor.typeSubmission(exerciseID, content!);
        // The character badge is rendered from the client's copy of the answer, so once it shows the typed length the
        // text has been taken over from the textarea and is part of what the next save or hand-in sends.
        const typedLength = (await getExercise(this.page, exerciseID).locator('#text-editor').inputValue()).length;
        await expect(getExercise(this.page, exerciseID).getByTestId('character-count')).toContainText(`${typedLength}`);
    }

    private async makeProgrammingExerciseSubmission(exerciseID: number, submission: ProgrammingExerciseSubmission, practiceMode = false, skipBuildResultCheck = false) {
        await this.programmingExerciseEditor.toggleCompressFileTree(exerciseID);
        for (const deleteFile of submission.deleteFiles) {
            await this.programmingExerciseEditor.deleteFile(exerciseID, deleteFile);
        }
        await this.programmingExerciseEditor.typeSubmission(exerciseID, submission);
        if (practiceMode) {
            await this.programmingExerciseEditor.submitPractice(exerciseID);
        } else {
            // During exam setup (skipBuildResultCheck) the score-producing build runs only after the due date,
            // so don't block on a result score here — it would not appear in time and could overrun the hook.
            await this.programmingExerciseEditor.submit(exerciseID, !skipBuildResultCheck);
        }
        if (!skipBuildResultCheck) {
            await expect(this.programmingExerciseEditor.getResultScoreFromExercise(exerciseID).getByText(submission.expectedResult)).toBeVisible({
                timeout: BUILD_RESULT_TIMEOUT * 2,
            });
        }
    }

    private async makeFileUploadExerciseSubmission(fileUploadFixture: string) {
        // The exam variant attaches the file and confirms it right away; there is no separate save step, the exam's
        // own "save and continue" is what persists the submission.
        await this.fileUploadExerciseEditor.attachFileExam(Fixtures.getAbsoluteFilePath(fileUploadFixture));
    }

    private async makeModelingExerciseSubmission(exerciseID: number) {
        await this.modelingExerciseEditor.addComponentToModel(exerciseID, 2);
        await this.modelingExerciseEditor.addComponentToModel(exerciseID, 3);
        await this.modelingExerciseEditor.addComponentToModel(exerciseID, 4);
    }

    private async makeQuizExerciseSubmission(exerciseID: number) {
        // In exam mode, quiz question elements use the actual DB ID (not index),
        // so we skip the #question{id} scope and click answer options directly.
        await this.quizExerciseMultipleChoice.tickAnswerOption(exerciseID, 0);
        await this.quizExerciseMultipleChoice.tickAnswerOption(exerciseID, 2);
    }

    async openExam(student: UserCredentials, course: Course, exam: Exam) {
        const examUrl = `/courses/${course.id}/exams/${exam.id}`;
        const urlPattern = `**/exams/${exam.id}**`;
        await Commands.login(this.page, student, examUrl);
        if (await this.urlSettles(urlPattern, 30_000)) {
            return;
        }
        // Under heavy multi-node load the router occasionally stays on /courses after login because a lazy chunk failed to
        // bootstrap. One more navigation recovers that; it is recorded, and a second miss fails the test.
        annotateRecovery(`openExam: ${student.username} did not reach ${urlPattern}, landed at ${this.page.url()}; navigating again`);
        await this.page.goto(examUrl);
        if (!(await this.urlSettles(urlPattern, 30_000))) {
            throw new Error(`openExam: expected URL matching ${urlPattern} but landed at ${this.page.url()} for student ${student.username}`);
        }
    }

    private async urlSettles(urlPattern: string, timeoutMs: number): Promise<boolean> {
        return this.page
            .waitForURL(urlPattern, { timeout: timeoutMs })
            .then(() => true)
            .catch(() => false);
    }

    async startParticipation(student: UserCredentials, course: Course, exam: Exam) {
        await this.openExam(student, course, exam);
        await this.examStartEnd.startExam(true);
    }

    async startExam() {
        await this.examStartEnd.startExam(true);
    }

    async almostStartExam() {
        await this.examStartEnd.onlyClickConfirmationCheckmark();
    }

    async handInEarly() {
        await this.examNavigation.handInEarly();
        const response = await this.examStartEnd.finishExam();
        expect(response.status()).toBe(200);
    }

    async checkExerciseScore(exerciseID: number, expectedResult: string, timeout: number = BUILD_RESULT_TIMEOUT) {
        // In exam mode, page.reload() navigates away from the active exercise tab,
        // so we rely on WebSocket to push build results and use Playwright's auto-retry.
        const resultScore = this.programmingExerciseEditor.getResultScoreFromExercise(exerciseID);
        await expect(resultScore).toContainText(expectedResult, { timeout });
    }
}
