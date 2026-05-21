import { UPLOAD_EXERCISE_BASE } from '../../../constants';
import { setMonacoEditorContent } from '../../../utils';
import { AbstractExerciseCreationPage } from '../AbstractExerciseCreationPage';

export class FileUploadExerciseCreationPage extends AbstractExerciseCreationPage {
    async typeMaxPoints(maxPoints: number) {
        await this.page.locator('#field_points').fill(maxPoints.toString());
    }

    async setFilePattern(pattern: string) {
        await setMonacoEditorContent(this.page, '#field_filePattern', pattern);
    }

    async typeProblemStatement(statement: string) {
        await setMonacoEditorContent(this.page, '#field_problemStatement', statement);
    }

    async typeExampleSolution(statement: string) {
        await setMonacoEditorContent(this.page, '#field_exampleSolution', statement);
    }

    async typeAssessmentInstructions(statement: string) {
        await setMonacoEditorContent(this.page, '#gradingInstructions', statement);
    }

    async create() {
        const responsePromise = this.page.waitForResponse(UPLOAD_EXERCISE_BASE);
        await this.page.locator('#save-entity').click();
        return await responsePromise;
    }
}
