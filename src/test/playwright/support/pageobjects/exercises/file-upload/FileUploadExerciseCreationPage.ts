import { UPLOAD_EXERCISE_BASE } from '../../../constants';
import { AbstractExerciseCreationPage } from '../AbstractExerciseCreationPage';

export class FileUploadExerciseCreationPage extends AbstractExerciseCreationPage {
    async typeMaxPoints(maxPoints: number) {
        await this.page.locator('#field_points').fill(maxPoints.toString());
    }

    async setFilePattern(pattern: string) {
        await this.typeText('#field_filePattern', pattern);
    }

    async typeProblemStatement(statement: string) {
        await this.typeText('#field_problemStatement', statement);
    }

    async typeExampleSolution(statement: string) {
        await this.typeText('#field_exampleSolution', statement);
    }

    async typeAssessmentInstructions(statement: string) {
        await this.typeText('#gradingInstructions', statement);
    }

    async create() {
        const responsePromise = this.page.waitForResponse(UPLOAD_EXERCISE_BASE);
        await this.page.locator('#save-entity').click();
        return await responsePromise;
    }

    private async typeText(selector: string, text: string) {
        await this.page.locator(selector).locator('.monaco-editor').pressSequentially(text);
    }
}
