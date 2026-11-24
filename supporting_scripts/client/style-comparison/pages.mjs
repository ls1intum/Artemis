// The pages and overlays that a capture visits. See README.md.

/**
 * Looks up the content of the course through the REST API: one exercise of each type, a lecture and an exam. A page that
 * needs an entity the course does not have is left out, and the capture names what is missing.
 */
export async function resolveCourseContent(page, base, courseId) {
    const json = async (path) => {
        const response = await page.request.get(`${base}/${path}`);
        return response.ok() ? response.json() : undefined;
    };
    const course = await json(`api/course/courses/${courseId}/with-exercises`);
    if (!course) {
        throw new Error(`Course ${courseId} was not found on ${base}. Pass a course with content with --course.`);
    }
    const exercises = course.exercises ?? [];
    const first = (type) => exercises.find((exercise) => exercise.type === type)?.id;
    const lectures = (await json(`api/lecture/courses/${courseId}/lectures`)) ?? [];
    const exams = (await json(`api/exam/courses/${courseId}/exams`)) ?? [];
    return {
        course: courseId,
        text: first('text'),
        modeling: first('modeling'),
        fileUpload: first('file-upload'),
        programming: first('programming'),
        quiz: first('quiz'),
        lecture: lectures[0]?.id,
        exam: exams[0]?.id,
    };
}

/** A page is `[name, path, ...ids it needs]`. The name starts with a letter for the area and a number that keeps the order. */
export function pagesOf(ids) {
    const management = `/course-management/${ids.course}`;
    const overview = `/courses/${ids.course}`;
    const programming = `${management}/programming-exercises/${ids.programming}`;
    const text = `${management}/text-exercises/${ids.text}`;
    const exam = `${management}/exams/${ids.exam}`;
    const lecture = `${management}/lectures/${ids.lecture}`;
    const admin = [
        ['a01-admin-users', '/admin/user-management'],
        ['a02-admin-user-new', '/admin/user-management/new'],
        ['a03-admin-health', '/admin/health'],
        ['a04-admin-configuration', '/admin/configuration'],
        ['a05-admin-features', '/admin/features'],
        ['a06-admin-logs', '/admin/logs'],
        ['a07-admin-metrics', '/admin/metrics'],
        ['a08-admin-audits', '/admin/audits'],
        ['a09-admin-build-overview', '/admin/build-overview'],
        ['a10-admin-build-agents', '/admin/build-agents'],
        ['a11-admin-cleanup', '/admin/cleanup-service'],
        ['a12-admin-system-notifications', '/admin/system-notification-management'],
        ['a13-admin-organizations', '/admin/organization-management'],
        ['a14-admin-lti', '/admin/lti-configuration'],
        ['a15-admin-standardized-competencies', '/admin/standardized-competencies'],
        ['a16-admin-user-statistics', '/admin/user-statistics'],
        ['a17-admin-upcoming', '/admin/upcoming-exams-and-exercises'],
        ['a18-admin-course-requests', '/admin/course-requests'],
        ['a19-admin-imprint', '/admin/imprint'],
        ['a20-admin-passkeys', '/admin/passkey-management'],
        ['a21-admin-websocket', '/admin/websocket'],
        ['a22-admin-feature-usage', '/admin/feature-usage'],
        ['a23-admin-exam-rooms', '/admin/exams/rooms'],
        ['a24-admin-error-analysis', '/admin/error-analysis'],
        ['m01-course-management', '/course-management'],
        ['m02-course-detail', management],
        ['m03-course-edit', `${management}/settings`],
        ['m04-exercises', `${management}/exercises`],
        ['m05-lectures', `${management}/lectures`],
        ['m06-lecture-detail', lecture, 'lecture'],
        ['m07-lecture-edit', `${lecture}/edit`, 'lecture'],
        ['m08-lecture-new', `${management}/lectures/new`],
        ['m09-exams', `${management}/exams`],
        ['m10-exam-detail', exam, 'exam'],
        ['m11-exam-edit', `${exam}/edit`, 'exam'],
        ['m12-exam-groups', `${exam}/exercise-groups`, 'exam'],
        ['m13-exam-students', `${exam}/students`, 'exam'],
        ['m14-exam-student-exams', `${exam}/student-exams`, 'exam'],
        ['m15-exam-scores', `${exam}/scores`, 'exam'],
        ['m16-exam-new', `${management}/exams/new`],
        ['m17-competencies', `${management}/competency-management`],
        ['m18-competency-new', `${management}/competency-management/create`],
        ['m19-faqs', `${management}/faqs`],
        ['m20-faq-new', `${management}/faqs/new`],
        ['m21-assessment-dashboard', `${management}/assessment-dashboard`],
        ['m22-scores', `${management}/scores`],
        ['m23-statistics', `${management}/course-statistics`],
        ['m24-grading', `${management}/grading`],
        ['m25-plagiarism-cases', `${management}/plagiarism-cases`],
        ['m26-tutorial-groups', `${management}/tutorial-groups`],
        ['m27-iris-settings', `${management}/iris-settings`],
        ['m28-learning-path', `${management}/learning-path-management`],
        ['m29-build-queue', `${management}/build-overview`],
        ['m30-communication', `${management}/communication`],
        ['m31-groups-students', `${management}/groups/students`],
        ['m32-onboarding', `${management}/onboarding`],
        ['x01-text-detail', text, 'text'],
        ['x02-text-edit', `${text}/edit`, 'text'],
        ['x03-text-new', `${management}/text-exercises/new`],
        ['x04-text-scores', `${text}/scores`, 'text'],
        ['x05-text-participations', `${text}/participations`, 'text'],
        ['x06-text-example-submissions', `${text}/example-submissions`, 'text'],
        ['x07-text-assessment', `${management}/assessment-dashboard/${ids.text}`, 'text'],
        ['x08-text-plagiarism', `${text}/plagiarism`, 'text'],
        ['x10-modeling-detail', `${management}/modeling-exercises/${ids.modeling}`, 'modeling'],
        ['x11-modeling-edit', `${management}/modeling-exercises/${ids.modeling}/edit`, 'modeling'],
        ['x12-modeling-new', `${management}/modeling-exercises/new`],
        ['x13-fileupload-detail', `${management}/file-upload-exercises/${ids.fileUpload}`, 'fileUpload'],
        ['x14-fileupload-edit', `${management}/file-upload-exercises/${ids.fileUpload}/edit`, 'fileUpload'],
        ['x15-quiz-new', `${management}/quiz-exercises/new`],
        ['x16-quiz-import', `${management}/quiz-exercises/import`],
        ['x17-quiz-detail', `${management}/quiz-exercises/${ids.quiz}`, 'quiz'],
        ['x18-quiz-edit', `${management}/quiz-exercises/${ids.quiz}/edit`, 'quiz'],
        ['p01-prog-detail', programming, 'programming'],
        ['p02-prog-edit', `${programming}/edit`, 'programming'],
        ['p03-prog-new', `${management}/programming-exercises/new`],
        ['p04-prog-grading-tests', `${programming}/grading/test-cases`, 'programming'],
        ['p05-prog-grading-categories', `${programming}/grading/code-analysis`, 'programming'],
        ['p06-prog-grading-policy', `${programming}/grading/submission-policy`, 'programming'],
        ['p07-prog-grading-feedback', `${programming}/grading/feedback-analysis`, 'programming'],
        ['p08-prog-code-editor-template', `${programming}/code-editor/TEMPLATE`, 'programming'],
        ['p09-prog-code-editor-solution', `${programming}/code-editor/SOLUTION`, 'programming'],
        ['p10-prog-scores', `${programming}/scores`, 'programming'],
        ['p11-prog-participations', `${programming}/participations`, 'programming'],
        ['p12-prog-repository-template', `${programming}/repository/TEMPLATE`, 'programming'],
        ['p13-prog-commit-history', `${programming}/repository/TEMPLATE/commit-history`, 'programming'],
        ['p14-prog-vcs-log', `${programming}/repository/TEMPLATE/vcs-access-log`, 'programming'],
        ['p15-prog-build-plan', `${programming}/edit-build-plan`, 'programming'],
        ['p16-prog-plagiarism', `${programming}/plagiarism`, 'programming'],
        ['p17-prog-import', `${management}/programming-exercises/import/${ids.programming}`, 'programming'],
        ['p18-prog-statistics', `${programming}/exercise-statistics`, 'programming'],
        ['p19-prog-version-history', `${programming}/version-history`, 'programming'],
        ['p20-prog-iris', `${programming}/iris-settings`, 'programming'],
        ['p21-prog-submissions', `${programming}/submissions`, 'programming'],
        ['p22-prog-teams', `${management}/exercises/${ids.programming}/teams`, 'programming'],
    ];
    const student = [
        ['s01-courses', '/courses'],
        ['s02-exercises', `${overview}/exercises`],
        ['s03-text-exercise', `${overview}/exercises/${ids.text}`, 'text'],
        ['s04-programming-exercise', `${overview}/exercises/${ids.programming}`, 'programming'],
        ['s05-modeling-exercise', `${overview}/exercises/${ids.modeling}`, 'modeling'],
        ['s06-fileupload-exercise', `${overview}/exercises/${ids.fileUpload}`, 'fileUpload'],
        ['s07-lectures', `${overview}/lectures/${ids.lecture}`, 'lecture'],
        ['s08-statistics', `${overview}/statistics`],
        ['s09-calendar', `${overview}/calendar`],
        ['s10-communication', `${overview}/communication`],
        ['s11-exams', `${overview}/exams`],
        ['s12-exam-start', `${overview}/exams/${ids.exam}`, 'exam'],
        ['s13-competencies', `${overview}/competencies`],
        ['s14-faq', `${overview}/faq`],
        ['s15-settings', `${overview}/settings`],
        ['s16-user-settings', '/user-settings/profile'],
        ['s17-user-settings-credentials', '/user-settings/credentials'],
        ['s18-user-settings-passkeys', '/user-settings/passkeys'],
        ['s19-user-settings-ssh-add', '/user-settings/credentials/ssh/add'],
        ['s20-text-editor', `${overview}/exercises/text-exercises/${ids.text}/participate/new`, 'text'],
        ['s21-archive', '/courses/archive'],
        ['s22-enroll', '/courses/enroll'],
        ['s23-about', '/about'],
        ['s24-privacy', '/privacy'],
        ['s25-tutorial-groups', `${overview}/tutorial-groups`],
        ['s26-learning-path', `${overview}/learning-path`],
    ];
    const available = ([, , ...needed]) => needed.every((key) => ids[key] !== undefined);
    return { admin: admin.filter(available), student: student.filter(available), skipped: [...admin, ...student].filter((entry) => !available(entry)).map(([name]) => name) };
}

/**
 * An overlay is a dialog, menu or popover that only exists after an interaction: `[name, user, path, open, ...ids]`.
 * `open` gets the page and opens the overlay; the capture records the overlay container of the Angular CDK.
 */
export function overlaysOf(ids) {
    const management = `/course-management/${ids.course}`;
    const overview = `/courses/${ids.course}`;
    const programming = `${management}/programming-exercises/${ids.programming}`;
    const button = (name) => (page) => page.getByRole('button', { name }).first().click({ timeout: 4000 });
    const locator = (selector) => (page) => page.locator(selector).first().click({ timeout: 4000 });
    const overlays = [
        ['o01-account-menu', 'admin', '/course-management', locator('#account-menu, [data-testid="account-menu"]')],
        ['o02-prog-reset', 'admin', programming, button(/^\s*Reset\s*$/i), 'programming'],
        ['o03-prog-export', 'admin', `${programming}/participations`, button(/^\s*Export/i), 'programming'],
        ['o04-prog-delete', 'admin', programming, button(/^\s*Delete\s*$/i), 'programming'],
        ['o05-lecture-delete', 'admin', `${management}/lectures`, button(/^\s*Delete\s*$/i), 'lecture'],
        ['o06-exam-delete', 'admin', `${management}/exams/${ids.exam}`, button(/^\s*Delete\s*$/i), 'exam'],
        ['o07-text-delete', 'admin', `${management}/text-exercises/${ids.text}`, button(/^\s*Delete\s*$/i), 'text'],
        ['o08-language-select', 'admin', `${management}/programming-exercises/new`, locator('#field_programmingLanguage')],
        ['o09-date-picker', 'admin', `${management}/exams/new`, locator('tumaet-ui-date-picker button, jhi-date-time-picker button')],
        ['o10-faq-delete', 'admin', `${management}/faqs`, button(/^\s*Delete\s*$/i)],
        ['o11-exam-students-add', 'admin', `${management}/exams/${ids.exam}/students`, button(/Import|Add|Register/i), 'exam'],
        ['o12-code-button', 'student', `${overview}/exercises/${ids.programming}`, button(/^\s*Code\s*$/i), 'programming'],
        ['o13-student-account', 'student', `${overview}/exercises`, locator('#account-menu, [data-testid="account-menu"]')],
        ['o14-exercise-filter', 'student', `${overview}/exercises`, button(/Filter/i)],
    ];
    return overlays.filter(([, , , , ...needed]) => needed.every((key) => ids[key] !== undefined));
}
