import { Service, inject } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { filter, map, tap } from 'rxjs/operators';
import { Observable, throwError } from 'rxjs';
import dayjs from 'dayjs/esm';
import { ExamApi } from 'app/openapi/api/exam-api';
import { ExamUserApi } from 'app/openapi/api/exam-user-api';
import { ExerciseGroupApi } from 'app/openapi/api/exercise-group-api';
import { StudentExamApi } from 'app/openapi/api/student-exam-api';
import { AdminExamApi } from 'app/openapi/api/admin-exam-api';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { ExamInformationDTO } from 'app/exam/shared/entities/exam-information.model';
import { Submission, reconnectSubmissions } from 'app/exercise/shared/entities/submission/submission.model';
import { AccountService } from 'app/core/auth/account.service';
import { convertDateStringFromServer } from 'app/foundation/util/date.utils';
import { ExamWideAnnouncementEvent } from 'app/exam/overview/services/exam-participation-live-events.service';
import { EntityTitleService, EntityType } from 'app/core/navbar/entity-title.service';
import { EntitySummaryCategory } from 'app/shared-ui/delete-dialog/delete-dialog.model';
import { buildExamDeletionSummary } from 'app/exam/manage/services/exam-deletion-summary.util';
import { toExamUpdateDTO } from 'app/exam/manage/services/exam-update-dto.model';
import { ExamExerciseStartPreparationStatus } from 'app/exam/manage/services/exam-exercise-start-preparation-status.model';
import { ExamStudentDTO, ExamStudentSearch } from 'app/exam/manage/students/exam-student-dto.model';
import { PageableResult } from 'app/foundation/pagination/pageable-table';
import { UserForRegistration, UserSearchResult } from 'app/shared-ui/user-registration-modal/user-for-registration.model';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import { ExamImportResultDTO, ExerciseGroupImportResultDTO } from 'app/exam/shared/entities/exam-import-result.model';
import { ExamScoreDTO } from 'app/exam/manage/exam-scores/exam-score-dtos.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import type { ExamImport } from 'app/openapi/model/exam-import';
import type { ExerciseGroupImport } from 'app/openapi/model/exercise-group-import';
import type { ExerciseImport } from 'app/openapi/model/exercise-import';
import { ExamImportProgress } from 'app/exam/shared/entities/exam-import-progress.model';
import { cloneWith, hydrate } from 'app/foundation/util/deep-clone.util';
import { ExamSource, toExam, toRows } from 'app/exam/manage/util/generated-exam.util';

/** The error of a save that the server would reject for a missing mandatory field. The exam form cannot be submitted that way, so this is a guard for the types. */
function missingMandatoryField(url: string): Observable<never> {
    return throwError(() => new HttpErrorResponse({ status: 400, statusText: 'Bad Request', url }));
}

@Service()
export class ExamManagementService {
    private examApi = inject(ExamApi);
    private exerciseGroupApi = inject(ExerciseGroupApi);
    private examUserApi = inject(ExamUserApi);
    private studentExamApi = inject(StudentExamApi);
    private adminExamApi = inject(AdminExamApi);
    private accountService = inject(AccountService);
    private entityTitleService = inject(EntityTitleService);
    private websocketService = inject(WebsocketService);

    public resourceUrl = 'api/exam/courses';

    /**
     * Create an exam on the server.
     * @param courseId The course id.
     * @param exam The exam to create.
     */
    create(courseId: number, exam: Exam): Observable<Exam> {
        const dto = toExamUpdateDTO(exam);
        const { title, visibleDate, startDate, endDate } = dto;
        if (!title || !visibleDate || !startDate || !endDate) {
            return missingMandatoryField(`/api/exam/courses/${courseId}/exams`);
        }
        return this.examApi.createExam(courseId, cloneWith(dto, { title, visibleDate, startDate, endDate })).pipe(map((created) => this.processExam(created)));
    }

    /**
     * Update an exam on the server.
     * @param courseId The course id.
     * @param exam The exam to update.
     */
    update(courseId: number, exam: Exam): Observable<Exam> {
        const dto = toExamUpdateDTO(exam);
        const { title, visibleDate, startDate, endDate } = dto;
        if (!title || !visibleDate || !startDate || !endDate) {
            return missingMandatoryField(`/api/exam/courses/${courseId}/exams`);
        }
        return this.examApi.updateExam(courseId, cloneWith(dto, { title, visibleDate, startDate, endDate })).pipe(map((updated) => this.processExam(updated)));
    }

    /**
     * Update the working time of an exam on the server using a PATCH request.
     * @param courseId The course id.
     * @param examId The exam id.
     * @param workingTimeChange The amount of time in seconds by which the working time should be increased or decreased. It can be positive or negative, but must not be 0.
     */
    updateWorkingTime(courseId: number, examId: number, workingTimeChange: number): Observable<Exam> {
        return this.examApi.updateExamWorkingTime(courseId, examId, workingTimeChange).pipe(map((exam) => this.processExam(exam)));
    }

    getAnnouncements(courseId: number, examId: number): Observable<ExamWideAnnouncementEvent[]> {
        return this.examApi.getExamAnnouncements(courseId, examId).pipe(
            map((events) => {
                const announcements = toRows<ExamWideAnnouncementEvent>(events);
                announcements.forEach((event) => this.convertCreatedDate(event));
                return announcements;
            }),
        );
    }

    createAnnouncement(courseId: number, examId: number, content: string): Observable<ExamWideAnnouncementEvent> {
        return this.examApi.createExamAnnouncement(courseId, examId, content).pipe(
            map((created) => {
                const [announcement] = toRows<ExamWideAnnouncementEvent>([created]);
                this.convertCreatedDate(announcement);
                return announcement;
            }),
        );
    }

    private convertCreatedDate(event: ExamWideAnnouncementEvent) {
        if (event.createdDate) {
            event.createdDate = dayjs(event.createdDate);
        }
    }

    /**
     * Imports an exam together with its exercises on the server.
     * <p>
     * The result carries the full {@link ExamImportResultDTO}: the created exam plus the titles of any
     * exercises that were skipped or only incompletely imported. The import progress dialog renders these (and the live
     * websocket progress for the given {@code importId}) instead of a transient toast.
     *
     * @param courseId the course id into which the exam should be imported
     * @param exam the exam with exercises to import
     * @param importId a client-generated id correlating this import with its websocket progress channel
     */
    import(courseId: number, exam: Exam, importId: string): Observable<ExamImportResultDTO> {
        const dto = ExamManagementService.convertExamToImportDTO(exam, courseId);
        if (!dto) {
            return missingMandatoryField(`/api/exam/courses/${courseId}/exam-import`);
        }
        return this.examApi.importExamWithExercises(courseId, dto, importId).pipe(
            map((response) => hydrate(new ExamImportResultDTO(), response)),
            // Prime the title cache for the imported exam.
            tap((result) => this.registerExam(result.exam)),
        );
    }

    /**
     * Imports exercise groups into an existing exam on the server.
     * <p>
     * The response carries the full {@link ExerciseGroupImportResultDTO} (the exam's exercise groups plus the skipped /
     * incomplete exercise titles) so the import progress dialog can show the outcome.
     *
     * @param courseId the course id into which the exercise groups should be imported
     * @param examId the exam id to which the exercise groups should be added
     * @param exerciseGroups the exercise groups to be added to the exam
     * @param importId a client-generated id correlating this import with its websocket progress channel
     */
    importExerciseGroup(courseId: number, examId: number, exerciseGroups: ExerciseGroup[], importId: string): Observable<ExerciseGroupImportResultDTO> {
        const dtos = ExamManagementService.convertExerciseGroupsToImportDTOs(exerciseGroups);
        if (!dtos) {
            return missingMandatoryField(`/api/exam/courses/${courseId}/exams/${examId}/import-exercise-group`);
        }
        return this.exerciseGroupApi.importExerciseGroup(courseId, examId, dtos, importId).pipe(map((response) => hydrate(new ExerciseGroupImportResultDTO(), response)));
    }

    /**
     * Generates a unique id for an import operation. The same id is sent to the server (so it emits progress on a matching
     * channel) and used to subscribe to that channel via {@link subscribeToImportProgress}.
     */
    generateImportId(): string {
        return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
    }

    /**
     * Subscribes to the live progress of the import identified by {@code importId}. The server sends one
     * {@link ExamImportProgress} message per exercise transition and a terminal message when the import finishes.
     *
     * @param importId the id returned by {@link generateImportId} and passed to the import request
     */
    subscribeToImportProgress(importId: string): Observable<ExamImportProgress> {
        return this.websocketService.subscribe<ExamImportProgress>(`/user/topic/exam-import/${importId}`);
    }

    /**
     * Find an exam on the server.
     * @param courseId The course id.
     * @param examId The id of the exam to get.
     * @param withExerciseGroups Boolean flag whether to fetch all exercise groups of the exam
     */
    find(courseId: number, examId: number, withExerciseGroups = false): Observable<Exam> {
        return this.examApi.getExam(courseId, examId, withExerciseGroups).pipe(map((exam) => this.processExam(exam)));
    }

    /**
     * Find an exam on the server with exercises for the exam import
     * @param examId The id of the exam to get.
     */
    findWithExercisesAndWithoutCourseId(examId: number): Observable<Exam> {
        return this.examApi.getExamForImportWithExercises(examId).pipe(map((exam) => this.processExam(exam)));
    }

    /**
     * Find all exams for the given course.
     * @param courseId The course id.
     */
    findAllExamsForCourse(courseId: number): Observable<Exam[]> {
        return this.examApi.getExamsForCourse(courseId).pipe(map((exams) => exams.map((exam) => this.processExam(exam))));
    }

    /**
     * Find all exams where the in the course they are conducted the user has instructor rights
     * @param courseId The course id where the quiz should be created
     */
    findAllExamsAccessibleToUser(courseId: number): Observable<Exam[]> {
        return this.examApi.getExamsWithQuizExercisesForUser(courseId).pipe(map((exams) => exams.map((exam) => this.processExam(exam))));
    }

    /**
     * Find all exams that are held today and in the future.
     */
    findAllCurrentAndUpcomingExams(): Observable<Exam[]> {
        return this.adminExamApi.getCurrentAndUpcomingExams().pipe(map((exams) => exams.map((exam) => this.processExam(exam))));
    }

    /**
     * Returns the exam with the provided unique identifier for the assessment dashboard
     * @param courseId - the id of the course
     * @param examId - the id of the exam
     * @param isTestRun - boolean to determine whether it is a test run
     */
    getExamWithInterestingExercisesForAssessmentDashboard(courseId: number, examId: number, isTestRun: boolean): Observable<Exam> {
        const request = isTestRun ? this.examApi.getExamForTestRunAssessmentDashboard(courseId, examId) : this.examApi.getExamForAssessmentDashboard(courseId, examId);
        return request.pipe(map((exam) => this.processExam(exam)));
    }

    getLatestIndividualEndDateOfExam(courseId: number, examId: number): Observable<ExamInformationDTO> {
        return this.examApi
            .getLatestIndividualEndDateOfExam(courseId, examId)
            .pipe(map((information) => ({ latestIndividualEndDate: dayjs(information.latestIndividualEndDate) })));
    }

    /**
     * Returns what is deleted together with the exam, grouped into categories for the delete dialog.
     * @param courseId The course id.
     * @param examId The exam id.
     */
    getDeletionSummary(courseId: number, examId: number): Observable<EntitySummaryCategory[]> {
        return this.examApi.getDeletionSummary(courseId, examId).pipe(map((summary) => (summary ? buildExamDeletionSummary(summary) : [])));
    }

    /**
     * Find all scores of an exam.
     * @param courseId The id of the course.
     * @param examId The id of the exam.
     */
    getExamScores(courseId: number, examId: number): Observable<ExamScoreDTO> {
        return this.examApi.getExamScore(courseId, examId).pipe(map((scores) => hydrate(new ExamScoreDTO(), scores)));
    }

    /**
     * Returns exercises in the exam that have submissions eligible for plagiarism detection.
     * @param courseId The course id.
     * @param examId The exam id.
     */
    getExercisesWithPotentialPlagiarismForExam(courseId: number, examId: number): Observable<Exercise[]> {
        return this.examApi.getAllExercisesWithPotentialPlagiarismForExam(courseId, examId).pipe(map((exercises) => toRows<Exercise>(exercises)));
    }

    /**
     * Fetch a paginated, searchable, and sortable page of students registered for the exam.
     * @param courseId The course id.
     * @param examId The exam id.
     * @param search Page index, page size, search term, sort column, and sort direction.
     * @returns A page of {@link ExamStudentDTO} rows with date fields converted to Dayjs instances.
     */
    findExamStudentsPaged(courseId: number, examId: number, search: ExamStudentSearch): Observable<PageableResult<ExamStudentDTO>> {
        return this.examUserApi
            .getExamStudentsPaged(courseId, examId, search.page, search.pageSize, search.sortingOrder, search.sortedColumn, search.searchTerm, search.filterProp || undefined)
            .pipe(
                map((res) => {
                    const rows = res.body ?? [];
                    const content = toRows<ExamStudentDTO>(rows);
                    content.forEach((row, index) => {
                        row.startedDate = convertDateStringFromServer(rows[index].startedDate);
                        row.submissionDate = convertDateStringFromServer(rows[index].submissionDate);
                    });
                    return { content, totalElements: Number(res.headers.get('X-Total-Count') ?? 0) };
                }),
            );
    }

    /**
     * Search for Artemis users that can be registered for the exam.
     * Matches the search term anywhere in the login, full name, email or registration number.
     * Each result is flagged with {@code isRegistered} if already registered for the exam.
     * @param courseId The course id.
     * @param examId The exam id.
     * @param searchTerm The text entered by the instructor.
     * @param page Zero-based page index.
     * @param size Number of results per page.
     * @returns A {@link UserSearchResult} with matching users and total count.
     */
    searchUsersForExamRegistration(courseId: number, examId: number, searchTerm: string, page: number, size: number): Observable<UserSearchResult> {
        return this.examUserApi.searchStudentsForExamRegistration(courseId, examId, searchTerm, page, size).pipe(
            map((res) => ({
                content: toRows<UserForRegistration>(res.body ?? []),
                totalElements: Number(res.headers.get('X-Total-Count') ?? 0),
            })),
        );
    }

    /**
     * Get the current progress of starting exercises for all students
     * @param courseId course to which the exam belongs
     * @param examId exam to which the student exams belong
     * @returns the status with the start date as dayjs, or null if the server sent none
     */
    getExerciseStartStatus(courseId: number, examId: number): Observable<ExamExerciseStartPreparationStatus | null> {
        return this.studentExamApi
            .getExerciseStartStatus(courseId, examId)
            .pipe(map((status) => (status ? cloneWith(status, { startedAt: convertDateStringFromServer(status.startedAt) }) : null)));
    }

    /**
     * Save the exercise groups of an exam in the given order.
     * @param courseId The course id.
     * @param examId The exam id.
     * @param exerciseGroups List of exercise groups.
     */
    updateOrder(courseId: number, examId: number, exerciseGroups: ExerciseGroup[]): Observable<void> {
        // Only the group ids in the desired order are sent. The server persists the order and returns no body — the
        // caller already holds the fully-detailed groups in exactly this order.
        return this.examApi.updateOrderOfExerciseGroups(
            courseId,
            examId,
            exerciseGroups.map((group) => group.id!),
        );
    }

    /**
     * Resets an Exam with examId by deleting all its studentExams and participations and unregistering all its students.
     * @param courseId The course id.
     * @param examId The exam id.
     */
    reset(courseId: number, examId: number): Observable<Exam> {
        return this.examApi.resetExam(courseId, examId).pipe(map((exam) => this.processExam(exam)));
    }

    /**
     * Converts an Exam to the body of the exam import request.
     * @param exam The exam to convert
     * @param courseId The target course id
     * @returns the body, or undefined if the exam or one of its exercises lacks a field the server requires
     */
    private static convertExamToImportDTO(exam: Exam, courseId: number): ExamImport | undefined {
        const { id, ...editableFields } = toExamUpdateDTO(exam);
        const { title, visibleDate, startDate, endDate } = editableFields;
        const exerciseGroups = exam.exerciseGroups && ExamManagementService.convertExerciseGroupsToImportDTOs(exam.exerciseGroups);
        if (!title || !visibleDate || !startDate || !endDate || (exam.exerciseGroups && !exerciseGroups)) {
            return undefined;
        }
        return cloneWith(editableFields, { title, visibleDate, startDate, endDate, courseId, exerciseGroups });
    }

    /**
     * Converts exercise groups (as loaded for the import dialogs) to the import DTO shape the server expects: the group
     * title and mandatory flag plus, per exercise, the source exercise id, type and the overrides the dialog can edit.
     * @param exerciseGroups The exercise groups to convert
     * @returns the groups, or undefined if an exercise lacks the id or type the server requires
     */
    private static convertExerciseGroupsToImportDTOs(exerciseGroups: ExerciseGroup[]): ExerciseGroupImport[] | undefined {
        const groups: ExerciseGroupImport[] = [];
        for (const group of exerciseGroups) {
            const exercises: ExerciseImport[] = [];
            for (const exercise of group.exercises ?? []) {
                if (exercise.id === undefined || exercise.type === undefined) {
                    return undefined;
                }
                exercises.push({
                    id: exercise.id,
                    exerciseType: exercise.type,
                    title: exercise.title,
                    shortName: exercise.shortName,
                    maxPoints: exercise.maxPoints,
                    bonusPoints: exercise.bonusPoints,
                });
            }
            groups.push({ title: group.title, isMandatory: group.isMandatory ?? true, exercises: group.exercises && exercises });
        }
        return groups;
    }

    private processExam(source: ExamSource): Exam {
        const exam = toExam(source);
        this.registerExam(exam);
        return exam;
    }

    /** Sets the access rights of the course of a converted exam and primes the title cache. */
    private registerExam(exam: Pick<Exam, 'id' | 'title' | 'course'>) {
        if (exam.course) {
            this.accountService.setAccessRightsForCourse(exam.course);
        }
        this.entityTitleService.setTitle(EntityType.EXAM, [exam.id], exam.title);
    }

    /**
     * Returns all submissions for the exam that are currently locked by a tutor for assessment.
     * @param courseId The course id.
     * @param examId The exam id.
     */
    findAllLockedSubmissionsOfExam(courseId: number, examId: number): Observable<Submission[]> {
        return this.examApi.getLockedSubmissionsForExam(courseId, examId).pipe(
            filter((locked) => !!locked),
            map((locked) => toRows<Submission>(locked)),
            tap((submissions) => reconnectSubmissions(submissions)),
        );
    }

    /**
     * Downloads the exam archive of the specified examId. Returns an error
     * if the archive does not exist.
     * @param courseId
     * @param examId The id of the exam
     */
    downloadExamArchive(courseId: number, examId: number): void {
        const url = `${this.resourceUrl}/${courseId}/exams/${examId}/download-archive`;
        window.open(url, '_blank');
    }
}
