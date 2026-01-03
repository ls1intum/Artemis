import { Page, expect } from '@playwright/test';

/**
 * A class which encapsulates UI selectors and actions for the exam details page.
 */
export class ExamDetailsPage {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    async openExerciseGroups() {
        await this.page.locator(`#exercises-button-groups`).click();
    }

    async checkItemChecked(checklistItem: ExamChecklistItem) {
        await expect(
            this.getChecklistItemLocator(checklistItem).getByTestId('check-icon-checked'),
            `Checklist item for \"${checklistItem}\" is not checked or not found`,
        ).toBeVisible();
    }

    async checkItemUnchecked(checklistItem: ExamChecklistItem) {
        await expect(
            this.getChecklistItemLocator(checklistItem).getByTestId('check-icon-unchecked'),
            `Checklist item for \"${checklistItem}\" is not unchecked or not found`,
        ).toBeVisible();
    }

    private getChecklistItemLocator(checklistItem: ExamChecklistItem) {
        return this.page.getByTestId(checklistItem);
    }

    async clickStudentsToRegister() {
        await this.page.getByTestId('students-button-register').click();
    }

    async clickStudentExamsToGenerate() {
        await this.page.getByTestId('student-exams-button-generate').click();
    }

    async clickStudentExamsToPrepareStart() {
        await this.page.getByTestId('student-exams-button-prepare-start').click();
    }

    async clickEditExamForPublishDate() {
        await this.page.locator('#editButton_publish').click();
    }

    async clickEditExamForReviewDate() {
        await this.page.locator('#editButton_review').click();
    }

    async clickEvaluateQuizExercises() {
        await this.page.locator('#evaluateQuizExercisesButton').click();
    }

    async clickAssessUnsubmittedParticipations() {
        await this.page.locator('#assessUnsubmittedExamModelingAndTextParticipationsButton').click();
    }

    /**
     * Deletes this exam.
     * @param examTitle the exam title to confirm the deletion
     */
    async deleteExam(examTitle: string) {
        await this.page.locator('#exam-delete').click();
        const deleteButton = this.page.getByTestId('delete-dialog-confirm-button');
        await expect(deleteButton).toBeDisabled();
        await this.page.locator('#confirm-entity-name').fill(examTitle);
        await expect(deleteButton).not.toBeDisabled();
        await deleteButton.click();
    }
}

export enum ExamChecklistItem {
    LEAST_ONE_EXERCISE_GROUP = 'check-least-one-exercise-group',
    NUMBER_OF_EXERCISE_GROUPS = 'check-number-of-exercise-groups',
    EACH_EXERCISE_GROUP_HAS_EXERCISES = 'check-each-exercise-group-has-exercises',
    POINTS_IN_EXERCISE_GROUPS_EQUAL = 'check-points-in-exercise-groups-equal',
    TOTAL_POINTS_POSSIBLE = 'check-total-points-possible',
    LEAST_ONE_STUDENT = 'check-least-one-student',
    ALL_EXAMS_GENERATED = 'check-all-exams-generated',
    ALL_EXERCISES_PREPARED = 'check-all-exercises-prepared',
    PUBLISHING_DATE_SET = 'check-publishing-date-set',
    START_DATE_REVIEW_SET = 'check-start-date-review-set',
    END_DATE_REVIEW_SET = 'check-end-date-review-set',
    UNFINISHED_ASSESSMENTS = 'check-unfinished-assessments',
    UNASSESSED_QUIZZES = 'check-unassessed-quizzes',
    UNSUBMITTED_EXERCISES = 'check-unsubmitted-exercises',
}
