import { Page, expect } from '@playwright/test';
import { Course } from 'app/core/course/shared/entities/course.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { AdditionalData, ExerciseType } from '../../constants';
import { UserCredentials } from '../../users';
import { OnlineEditorPage, ProgrammingExerciseSubmission } from '../exercises/programming/OnlineEditorPage';
import { ExamNavigationBar } from './ExamNavigationBar';
import { ExamStartEndPage } from './ExamStartEndPage';
import { ModelingEditor } from '../exercises/modeling/ModelingEditor';
import { MultipleChoiceQuiz } from '../exercises/quiz/MultipleChoiceQuiz';
import { TextEditorPage } from '../exercises/text/TextEditorPage';
import { Commands } from '../../commands';
import { Fixtures } from '../../../fixtures/fixtures';
import { ExamParticipationActions } from './ExamParticipationActions';
import { BUILD_RESULT_TIMEOUT } from '../../timeouts';

export class ExamParticipationPage extends ExamParticipationActions {
    private readonly examNavigation: ExamNavigationBar;
    private readonly examStartEnd: ExamStartEndPage;
    private readonly modelingExerciseEditor: ModelingEditor;
    private readonly programmingExerciseEditor: OnlineEditorPage;
    private readonly quizExerciseMultipleChoice: MultipleChoiceQuiz;
    private readonly textExerciseEditor: TextEditorPage;

    constructor(
        examNavigation: ExamNavigationBar,
        examStartEnd: ExamStartEndPage,
        modelingExerciseEditor: ModelingEditor,
        programmingExerciseEditor: OnlineEditorPage,
        quizExerciseMultipleChoice: MultipleChoiceQuiz,
        textExerciseEditor: TextEditorPage,
        page: Page,
    ) {
        super(page);
        this.examNavigation = examNavigation;
        this.examStartEnd = examStartEnd;
        this.modelingExerciseEditor = modelingExerciseEditor;
        this.programmingExerciseEditor = programmingExerciseEditor;
        this.quizExerciseMultipleChoice = quizExerciseMultipleChoice;
        this.textExerciseEditor = textExerciseEditor;
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
            case ExerciseType.PROGRAMMING:
                await this.makeProgrammingExerciseSubmission(exerciseID, additionalData!.submission!, additionalData!.practiceMode, additionalData!.skipBuildResultCheck);
                break;
        }
    }

    async makeTextExerciseSubmission(exerciseID: number, textFixture: string) {
        const content = await Fixtures.get(textFixture);
        await this.textExerciseEditor.typeSubmission(exerciseID, content!);
        // Wait for the text to be processed by Angular change detection
        await this.page.waitForTimeout(1000);
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
            await this.programmingExerciseEditor.submit(exerciseID);
        }
        if (!skipBuildResultCheck) {
            await expect(this.programmingExerciseEditor.getResultScoreFromExercise(exerciseID).getByText(submission.expectedResult)).toBeVisible({
                timeout: BUILD_RESULT_TIMEOUT * 2,
            });
        }
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
        await Commands.login(this.page, student, `/courses/${course.id}/exams/${exam.id}`);
        // Use a permissive glob so Angular sub-path routing (e.g. /exams/{id}/start)
        // does not cause waitForURL to time out.
        await this.page.waitForURL(`**/exams/${exam.id}**`);
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
