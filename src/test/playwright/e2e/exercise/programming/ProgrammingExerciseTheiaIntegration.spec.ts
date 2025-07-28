import { Course } from 'app/core/course/shared/entities/course.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';

import { admin, instructor, studentOne, tutor } from '../../../support/users';
import { test } from '../../../support/fixtures';
import { ProgrammingExerciseAssessmentType, ProgrammingLanguage, THEIA_BASE } from '../../../support/constants';
import dayjs from 'dayjs';

test.describe('Programming exercise Theia integration', { tag: '@sequential' }, () => {
    let course: Course;
    let exercise: ProgrammingExercise;
    let dueDate: dayjs.Dayjs;
    let assessmentDueDate: dayjs.Dayjs;

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

    test('Opens the programming exercise in theia and ensure repository is cloned', async ({ page, login, programmingExerciseOverview, landingPage }) => {
        console.log(exercise);
        await programmingExerciseOverview.startParticipation(course.id!, exercise.id!, studentOne);
        await login(studentOne, `/courses/${course.id!}/exercises/${exercise.id!}`);
        const [theiaPage] = await Promise.all([page.context().waitForEvent('page'), programmingExerciseOverview.openInOnlineIDE()]);
        await theiaPage.waitForURL(`**/${THEIA_BASE}/**`);
        landingPage.setPage(theiaPage);
        await landingPage.login(studentOne.username, studentOne.password);
        await theiaPage.waitForURL(/.*#\/home\/project/); //signalizes that theia session is loading
    });

    test.afterEach('Delete course', async ({ courseManagementAPIRequests }) => {
        await courseManagementAPIRequests.deleteCourse(course, admin);
    });
});
