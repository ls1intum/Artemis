import { Course } from 'app/core/course/shared/entities/course.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { expect } from '@playwright/test';

import { admin, instructor, studentOne, tutor } from '../../../support/users';
import { test } from '../../../support/fixtures';
import { ProgrammingExerciseAssessmentType, ProgrammingLanguage, THEIA_BASE } from '../../../support/constants';
import dayjs from 'dayjs';
import dotenv from 'dotenv';
import path from 'path';

dotenv.config({ path: path.resolve(__dirname, 'theia.env') });

test.describe('Programming exercise Theia integration', { tag: '@sequential' }, () => {
    let course: Course;
    let exercise: ProgrammingExercise;
    let dueDate: dayjs.Dayjs;
    let assessmentDueDate: dayjs.Dayjs;
    let keycloakUser: string = process.env.THEIA_KEYCLOAK_USER!;
    let keycloakPassword: string = process.env.THEIA_KEYCLOAK_PASSWORD!;

    test.beforeEach('Creates a programming exercise with Theia enabled', async ({ login, courseManagementAPIRequests, exerciseAPIRequests }) => {
        await login(admin);
        course = await courseManagementAPIRequests.createCourse({ customizeGroups: true });
        await courseManagementAPIRequests.addStudentToCourse(course, studentOne);
        await courseManagementAPIRequests.addTutorToCourse(course, tutor);
        await courseManagementAPIRequests.addInstructorToCourse(course, instructor);
        dueDate = dayjs().add(60, 'seconds');
        assessmentDueDate = dueDate.add(120, 'seconds');
        exercise = await exerciseAPIRequests.createProgrammingExercise({
            course,
            releaseDate: dayjs(),
            dueDate: dueDate,
            assessmentDate: assessmentDueDate,
            assessmentType: ProgrammingExerciseAssessmentType.SEMI_AUTOMATIC,
            programmingLanguage: ProgrammingLanguage.JAVA,
            allowOnlineIDE: true,
            buildConfig: {
                theiaImage: 'java-17-latest',
            },
        });
    });

    test('Opens the programming exercise in theia and ensure repository is cloned', async ({ page, login, programmingExerciseOverview, theiaLandingPage }) => {
        console.log(exercise);
        await programmingExerciseOverview.startParticipation(course.id!, exercise.id!, studentOne);
        await login(studentOne, `/courses/${course.id!}/exercises/${exercise.id!}`);
        const [theiaPage] = await Promise.all([page.context().waitForEvent('page'), programmingExerciseOverview.openInOnlineIDE()]);
        await theiaPage.waitForURL(`**/${THEIA_BASE}/**`);
        theiaLandingPage.setPage(theiaPage);
        await theiaLandingPage.login(keycloakUser, keycloakPassword);
        await theiaPage.waitForURL(/.*#\/home\/project/);

        const repositoryName = `${course.shortName}${exercise.shortName}-${studentOne.username}`;
        await theiaPage.goto(theiaPage.url() + `/home/project/${repositoryName}`);
        const expectedUrl = theiaPage.url();
        await theiaPage.reload();
        await theiaPage.locator('.gs-header').waitFor({ state: 'visible' });
        expect(theiaPage.url()).toBe(expectedUrl);
    });

    test.afterEach('Delete course', async ({ courseManagementAPIRequests }) => {
        await courseManagementAPIRequests.deleteCourse(course, admin);
    });
});
