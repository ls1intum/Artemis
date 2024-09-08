import { Page, expect } from '@playwright/test';
import { Course } from 'app/entities/course.model';
import { Exam } from 'app/entities/exam/exam.model';
import { AdditionalData, ExerciseType } from '../../constants';
import { UserCredentials } from '../../users';
import { OnlineEditorPage, ProgrammingExerciseSubmission } from '../exercises/programming/OnlineEditorPage';
import { CoursesPage } from '../course/CoursesPage';
import { CourseOverviewPage } from '../course/CourseOverviewPage';
import { ExamNavigationBar } from './ExamNavigationBar';
import { ExamStartEndPage } from './ExamStartEndPage';
import { ModelingEditor } from '../exercises/modeling/ModelingEditor';
import { MultipleChoiceQuiz } from '../exercises/quiz/MultipleChoiceQuiz';
import { TextEditorPage } from '../exercises/text/TextEditorPage';
import { Commands } from '../../commands';
import { Fixtures } from '../../../fixtures/fixtures';
import { ExamParticipationActions } from './ExamParticipationActions';

export class ExamParticipationPage extends ExamParticipationActions {
    private readonly courseList: CoursesPage;
    private readonly courseOverview: CourseOverviewPage;
    private readonly examNavigation: ExamNavigationBar;
    private readonly examStartEnd: ExamStartEndPage;
    private readonly modelingExerciseEditor: ModelingEditor;
    private readonly programmingExerciseEditor: OnlineEditorPage;
    private readonly quizExerciseMultipleChoice: MultipleChoiceQuiz;
    private readonly textExerciseEditor: TextEditorPage;

    constructor(
        courseList: CoursesPage,
        courseOverview: CourseOverviewPage,
        examNavigation: ExamNavigationBar,
        examStartEnd: ExamStartEndPage,
        modelingExerciseEditor: ModelingEditor,
        programmingExerciseEditor: OnlineEditorPage,
        quizExerciseMultipleChoice: MultipleChoiceQuiz,
        textExerciseEditor: TextEditorPage,
        page: Page,
    ) {
        super(page);
        this.courseList = courseList;
        this.courseOverview = courseOverview;
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
                await this.makeQuizExerciseSubmission(exerciseID, additionalData!.quizExerciseID!);
                break;
            case ExerciseType.PROGRAMMING:
                await this.makeProgrammingExerciseSubmission(exerciseID, additionalData!.submission!, additionalData!.practiceMode);
                break;
        }
    }

    async makeTextExerciseSubmission(exerciseID: number, textFixture: string) {
        const content = await Fixtures.get(textFixture);
        await this.textExerciseEditor.typeSubmission(exerciseID, content!);
        await this.page.waitForTimeout(1000);
    }

    private async makeProgrammingExerciseSubmission(exerciseID: number, submission: ProgrammingExerciseSubmission, practiceMode = false) {
        await this.programmingExerciseEditor.toggleCompressFileTree(exerciseID);
        await this.programmingExerciseEditor.deleteFile(exerciseID, 'Client.java');
        await this.programmingExerciseEditor.deleteFile(exerciseID, 'BubbleSort.java');
        await this.programmingExerciseEditor.deleteFile(exerciseID, 'MergeSort.java');
        await this.programmingExerciseEditor.typeSubmission(exerciseID, submission);
        if (practiceMode) {
            await this.programmingExerciseEditor.submitPractice(exerciseID);
        } else {
            await this.programmingExerciseEditor.submit(exerciseID);
        }
        await expect(this.programmingExerciseEditor.getResultScoreFromExercise(exerciseID).getByText(submission.expectedResult)).toBeVisible();
    }

    private async makeModelingExerciseSubmission(exerciseID: number) {
        await this.modelingExerciseEditor.addComponentToModel(exerciseID, 1);
        await this.modelingExerciseEditor.addComponentToModel(exerciseID, 2);
        await this.modelingExerciseEditor.addComponentToModel(exerciseID, 3);
    }

    private async makeQuizExerciseSubmission(exerciseID: number, quizExerciseID: number) {
        await this.quizExerciseMultipleChoice.tickAnswerOption(exerciseID, 0, quizExerciseID);
        await this.quizExerciseMultipleChoice.tickAnswerOption(exerciseID, 2, quizExerciseID);
    }

    async openExam(student: UserCredentials, course: Course, exam: Exam) {
        await Commands.login(this.page, student, '/');
        await this.page.goto(`/courses`);
        await this.page.waitForURL('/courses');
        await this.courseList.openCourse(course.id!);
        await this.courseOverview.openExamsTab();
        await this.courseOverview.openExam(exam.title!);
        await this.page.goto(`/courses/${course.id}/exams/${exam.id}`);
        await this.page.waitForURL(`**/exams/${exam.id}`);
    }

    async startParticipation(student: UserCredentials, course: Course, exam: Exam) {
        await this.openExam(student, course, exam);
        await this.examStartEnd.startExam(true);
    }

    async startExam() {
        await this.examStartEnd.startExam(true);
    }

    async handInEarly() {
        await this.examNavigation.handInEarly();
        const response = await this.examStartEnd.finishExam();
        expect(response.status()).toBe(200);
    }
}
