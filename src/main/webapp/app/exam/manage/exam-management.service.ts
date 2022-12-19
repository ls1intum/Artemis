import { Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { filter, map, tap } from 'rxjs/operators';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import dayjs from 'dayjs/esm';
import { Exam } from 'app/entities/exam.model';
import { createRequestOption } from 'app/shared/util/request.util';
import { StudentDTO } from 'app/entities/student-dto.model';
import { StudentExam } from 'app/entities/student-exam.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { ExamScoreDTO } from 'app/exam/exam-scores/exam-score-dtos.model';
import { ExamInformationDTO } from 'app/entities/exam-information.model';
import { ExamChecklist } from 'app/entities/exam-checklist.model';
import { StatsForDashboard } from 'app/course/dashboards/stats-for-dashboard.model';
import { Submission, reconnectSubmissions } from 'app/entities/submission.model';
import { AccountService } from 'app/core/auth/account.service';
import { convertDateFromClient, convertDateFromServer } from 'app/utils/date.utils';
import { EntityTitleService, EntityType } from 'app/shared/layouts/navbar/entity-title.service';
import { ExamExerciseStartPreparationStatus } from 'app/exam/manage/student-exams/student-exams.component';

type EntityResponseType = HttpResponse<Exam>;
type EntityArrayResponseType = HttpResponse<Exam[]>;

@Injectable({ providedIn: 'root' })
export class ExamManagementService {
    public resourceUrl = SERVER_API_URL + 'api/courses';
    public adminResourceUrl = SERVER_API_URL + 'api/admin/courses';

    constructor(private router: Router, private http: HttpClient, private accountService: AccountService, private entityTitleService: EntityTitleService) {}

    /**
     * Create an exam on the server using a POST request.
     * @param courseId The course id.
     * @param exam The exam to create.
     */
    create(courseId: number, exam: Exam): Observable<EntityResponseType> {
        const copy = ExamManagementService.convertExamDatesFromClient(exam);
        return this.http
            .post<Exam>(`${this.resourceUrl}/${courseId}/exams`, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.processExamResponseFromServer(res)));
    }

    /**
     * Update an exam on the server using a PUT request.
     * @param courseId The course id.
     * @param exam The exam to update.
     */
    update(courseId: number, exam: Exam): Observable<EntityResponseType> {
        const copy = ExamManagementService.convertExamDatesFromClient(exam);
        return this.http
            .put<Exam>(`${this.resourceUrl}/${courseId}/exams`, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.processExamResponseFromServer(res)));
    }

    /**
     * Imports an exam on the server using a PUT request.
     * @param courseId The course id into which the exam should be imported
     * @param exam The exam with exercises to import.
     */
    import(courseId: number, exam: Exam): Observable<EntityResponseType> {
        const copy = ExamManagementService.convertExamDatesFromClient(exam);
        return this.http
            .post<Exam>(`${this.resourceUrl}/${courseId}/exam-import`, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.processExamResponseFromServer(res)));
    }

    /**
     * Imports an exam on the server using a PUT request.
     * @param courseId The course id into which the exercise groups should be imported
     * @param examId The exam id to which the exercise groups should be added
     * @param exerciseGroups the exercise groups to be added to the exam
     */
    importExerciseGroup(courseId: number, examId: number, exerciseGroups: ExerciseGroup[]): Observable<HttpResponse<ExerciseGroup[]>> {
        return this.http.post<ExerciseGroup[]>(`${this.resourceUrl}/${courseId}/exams/${examId}/import-exercise-group`, exerciseGroups, { observe: 'response' });
    }

    /**
     * Find an exam on the server using a GET request.
     * @param courseId The course id.
     * @param examId The id of the exam to get.
     * @param withStudents Boolean flag whether to fetch all students registered for the exam
     * @param withExerciseGroups Boolean flag whether to fetch all exercise groups of the exam
     */
    find(courseId: number, examId: number, withStudents = false, withExerciseGroups = false): Observable<EntityResponseType> {
        const options = createRequestOption({ withStudents, withExerciseGroups });
        return this.http
            .get<Exam>(`${this.resourceUrl}/${courseId}/exams/${examId}`, { params: options, observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.processExamResponseFromServer(res)));
    }

    /**
     * Find an exam on the server using a GET request with exercises for the exam import
     * @param examId The id of the exam to get.
     */
    findWithExercisesAndWithoutCourseId(examId: number): Observable<EntityResponseType> {
        return this.http.get<Exam>(`${SERVER_API_URL}api/exams/${examId}`, { observe: 'response' }).pipe(map((res: EntityResponseType) => this.processExamResponseFromServer(res)));
    }

    /**
     * Find all scores of an exam.
     * @param courseId The id of the course.
     * @param examId The id of the exam.
     */
    getExamScores(courseId: number, examId: number): Observable<HttpResponse<ExamScoreDTO>> {
        return this.http.get<ExamScoreDTO>(`${this.resourceUrl}/${courseId}/exams/${examId}/scores`, { observe: 'response' });
    }

    /**
     * Get the exam statistics used within the instructor exam checklist
     * @param courseId The id of the course.
     * @param examId The id of the exam.
     */
    getExamStatistics(courseId: number, examId: number): Observable<HttpResponse<ExamChecklist>> {
        return this.http.get<ExamChecklist>(`${this.resourceUrl}/${courseId}/exams/${examId}/statistics`, { observe: 'response' });
    }

    /**
     * returns the stats of the exam with the provided unique identifiers for the assessment dashboard
     * @param courseId - the id of the course
     * @param examId   - the id of the exam
     */
    getStatsForExamAssessmentDashboard(courseId: number, examId: number): Observable<HttpResponse<StatsForDashboard>> {
        return this.http.get<StatsForDashboard>(`${this.resourceUrl}/${courseId}/exams/${examId}/stats-for-exam-assessment-dashboard`, { observe: 'response' });
    }

    /**
     * Find all exams for the given course.
     * @param courseId The course id.
     */
    findAllExamsForCourse(courseId: number): Observable<EntityArrayResponseType> {
        return this.http
            .get<Exam[]>(`${this.resourceUrl}/${courseId}/exams`, { observe: 'response' })
            .pipe(map((res: EntityArrayResponseType) => this.processExamArrayResponseFromServer(res)));
    }

    /**
     * Find all exams where the in the course they are conducted the user has instructor rights
     * @param courseId The course id where the quiz should be created
     */
    findAllExamsAccessibleToUser(courseId: number): Observable<EntityArrayResponseType> {
        return this.http
            .get<Exam[]>(`${this.resourceUrl}/${courseId}/exams-for-user`, { observe: 'response' })
            .pipe(map((res: EntityArrayResponseType) => this.processExamArrayResponseFromServer(res)));
    }

    /**
     * Find all exams that are held today and in the future.
     */
    findAllCurrentAndUpcomingExams(): Observable<EntityArrayResponseType> {
        return this.http
            .get<Exam[]>(`${this.adminResourceUrl}/upcoming-exams`, { observe: 'response' })
            .pipe(map((res: EntityArrayResponseType) => this.processExamArrayResponseFromServer(res)));
    }

    /**
     * Returns the exam with the provided unique identifier for the assessment dashboard
     * @param courseId - the id of the course
     * @param examId - the id of the exam
     * @param isTestRun - boolean to determine whether it is a test run
     */
    getExamWithInterestingExercisesForAssessmentDashboard(courseId: number, examId: number, isTestRun: boolean): Observable<EntityResponseType> {
        let url: string;
        if (isTestRun) {
            url = `${this.resourceUrl}/${courseId}/exams/${examId}/exam-for-test-run-assessment-dashboard`;
        } else {
            url = `${this.resourceUrl}/${courseId}/exams/${examId}/exam-for-assessment-dashboard`;
        }
        return this.http.get<Exam>(url, { observe: 'response' }).pipe(map((res: EntityResponseType) => this.processExamResponseFromServer(res)));
    }

    getLatestIndividualEndDateOfExam(courseId: number, examId: number): Observable<HttpResponse<ExamInformationDTO>> {
        const url = `${this.resourceUrl}/${courseId}/exams/${examId}/latest-end-date`;
        return this.http.get<ExamInformationDTO>(url, { observe: 'response' }).pipe(
            map((res: HttpResponse<ExamInformationDTO>) => {
                res.body!.latestIndividualEndDate = dayjs(res.body!.latestIndividualEndDate);
                return res;
            }),
        );
    }

    /**
     * Delete an exam on the server using a DELETE request.
     * @param courseId The course id.
     * @param examId The id of the exam to delete.
     */
    delete(courseId: number, examId: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${courseId}/exams/${examId}`, { observe: 'response' });
    }

    /**
     * Add a student to the registered users for an exam
     * @param courseId The course id.
     * @param examId The id of the exam to which to add the student
     * @param studentLogin Login of the student
     */
    addStudentToExam(courseId: number, examId: number, studentLogin: string): Observable<HttpResponse<StudentDTO>> {
        return this.http.post<StudentDTO>(`${this.resourceUrl}/${courseId}/exams/${examId}/students/${studentLogin}`, undefined, { observe: 'response' });
    }

    /**
     * Add students to the registered users for an exam
     * @param courseId The course id.
     * @param examId The id of the exam to which to add the student
     * @param studentDtos Student DTOs of student to add to the exam
     * @return studentDtos of students that were not found in the system
     */
    addStudentsToExam(courseId: number, examId: number, studentDtos: StudentDTO[]): Observable<HttpResponse<StudentDTO[]>> {
        return this.http.post<StudentDTO[]>(`${this.resourceUrl}/${courseId}/exams/${examId}/students`, studentDtos, { observe: 'response' });
    }

    /**
     * Add all students of the course to the exam
     * @param courseId
     * @param examId
     * @return studentDtos of students that were not found in the system
     */
    addAllStudentsOfCourseToExam(courseId: number, examId: number): Observable<HttpResponse<void>> {
        return this.http.post<HttpResponse<void>>(`${this.resourceUrl}/${courseId}/exams/${examId}/register-course-students`, { observe: 'response' });
    }

    /**
     * Remove a student from the registered users for an exam
     * @param courseId The course id
     * @param examId The id of the exam from which to remove the student
     * @param studentLogin Login of the student
     * @param withParticipationsAndSubmission
     */
    removeStudentFromExam(courseId: number, examId: number, studentLogin: string, withParticipationsAndSubmission = false): Observable<HttpResponse<any>> {
        const options = createRequestOption({ withParticipationsAndSubmission });
        return this.http.delete<any>(`${this.resourceUrl}/${courseId}/exams/${examId}/students/${studentLogin}`, {
            params: options,
            observe: 'response',
        });
    }

    /**
     * Remove all students from an exam
     * @param courseId The course id
     * @param examId The id of the exam from which to remove the student
     * @param withParticipationsAndSubmission if participations and Submissions should also be removed
     */
    removeAllStudentsFromExam(courseId: number, examId: number, withParticipationsAndSubmission = false) {
        const options = createRequestOption({ withParticipationsAndSubmission });
        return this.http.delete<any>(`${this.resourceUrl}/${courseId}/exams/${examId}/students`, {
            params: options,
            observe: 'response',
        });
    }

    /**
     * Generate all student exams for all registered students of the exam.
     * @param courseId
     * @param examId
     * @returns a list with the generated student exams
     */
    generateStudentExams(courseId: number, examId: number): Observable<HttpResponse<StudentExam[]>> {
        return this.http.post<any>(`${this.resourceUrl}/${courseId}/exams/${examId}/generate-student-exams`, {}, { observe: 'response' });
    }

    /**
     * Generate a test run student exam based on the testRunConfiguration.
     * @param courseId the id of the course
     * @param examId the id of the exam
     * @param testRunConfiguration the desired configuration
     * @returns the created test run
     */
    createTestRun(courseId: number, examId: number, testRunConfiguration: StudentExam): Observable<HttpResponse<StudentExam>> {
        return this.http.post<StudentExam>(`${this.resourceUrl}/${courseId}/exams/${examId}/test-run`, testRunConfiguration, { observe: 'response' });
    }

    /**
     * Delete a test run
     * @param courseId the id of the course
     * @param examId the id of the exam
     * @param testRunId the id of the test run
     */
    deleteTestRun(courseId: number, examId: number, testRunId: number): Observable<HttpResponse<StudentExam>> {
        return this.http.delete<StudentExam>(`${this.resourceUrl}/${courseId}/exams/${examId}/test-run/${testRunId}`, { observe: 'response' });
    }

    /**
     * Find all the test runs for the exam
     * @param courseId the id of the course
     * @param examId the id of the exam
     */
    findAllTestRunsForExam(courseId: number, examId: number): Observable<HttpResponse<StudentExam[]>> {
        return this.http.get<StudentExam[]>(`${this.resourceUrl}/${courseId}/exams/${examId}/test-runs`, { observe: 'response' });
    }

    /**
     * Generate missing student exams for newly added students of the exam.
     * @param courseId
     * @param examId
     * @returns a list with the generated student exams
     */
    generateMissingStudentExams(courseId: number, examId: number): Observable<HttpResponse<StudentExam[]>> {
        return this.http.post<any>(`${this.resourceUrl}/${courseId}/exams/${examId}/generate-missing-student-exams`, {}, { observe: 'response' });
    }

    /**
     * Start all the exercises for all the student exams belonging to the exam
     * @param courseId course to which the exam belongs
     * @param examId exam to which the student exams belong
     */
    startExercises(courseId: number, examId: number): Observable<HttpResponse<void>> {
        return this.http.post<void>(`${this.resourceUrl}/${courseId}/exams/${examId}/student-exams/start-exercises`, {}, { observe: 'response' });
    }

    /**
     * Get the current progress of starting exercises for all students
     * @param courseId course to which the exam belongs
     * @param examId exam to which the student exams belong
     * @returns an object containing the status
     */
    getExerciseStartStatus(courseId: number, examId: number): Observable<HttpResponse<ExamExerciseStartPreparationStatus>> {
        return this.http
            .get<ExamExerciseStartPreparationStatus>(`${this.resourceUrl}/${courseId}/exams/${examId}/student-exams/start-exercises/status`, { observe: 'response' })
            .pipe(
                tap((res: HttpResponse<ExamExerciseStartPreparationStatus>) => {
                    if (res.body) {
                        res.body.startedAt = convertDateFromServer(res.body.startedAt);
                    }
                }),
            );
    }

    /**
     * Evaluate all the quiz exercises belonging to the exam
     * @param courseId id of the course to which the exam belongs
     * @param examId id of the exam for which the quiz exercises should be evaluated
     * @returns number of evaluated exercises
     */
    evaluateQuizExercises(courseId: number, examId: number): Observable<HttpResponse<number>> {
        return this.http.post<any>(`${this.resourceUrl}/${courseId}/exams/${examId}/student-exams/evaluate-quiz-exercises`, {}, { observe: 'response' });
    }

    /**
     * Assess all the modeling and text participations belonging to unsubmitted student exams
     * @param courseId id of the course to which the exam belongs
     * @param examId id of the exam
     * @returns number of evaluated participations
     */
    assessUnsubmittedExamModelingAndTextParticipations(courseId: number, examId: number): Observable<HttpResponse<number>> {
        return this.http.post<any>(`${this.resourceUrl}/${courseId}/exams/${examId}/student-exams/assess-unsubmitted-and-empty-student-exams`, {}, { observe: 'response' });
    }

    /**
     * Unlock all the programming exercises belonging to the exam
     * @param courseId id of the course to which the exam belongs
     * @param examId id of the exam for which the programming exercises should be unlocked
     * @returns number of exercises for which the repositories were unlocked
     */
    unlockAllRepositories(courseId: number, examId: number): Observable<HttpResponse<number>> {
        return this.http.post<any>(`${this.resourceUrl}/${courseId}/exams/${examId}/student-exams/unlock-all-repositories`, {}, { observe: 'response' });
    }

    /**
     * Lock all the programming exercises belonging to the exam
     * @param courseId id of the course to which the exam belongs
     * @param examId id of the exam for which the programming exercises should be locked
     * @returns number of exercises for which the repositories were locked
     */
    lockAllRepositories(courseId: number, examId: number): Observable<HttpResponse<number>> {
        return this.http.post<any>(`${this.resourceUrl}/${courseId}/exams/${examId}/student-exams/lock-all-repositories`, {}, { observe: 'response' });
    }

    /**
     * Save the exercise groups of an exam in the given order.
     * @param courseId The course id.
     * @param examId The exam id.
     * @param exerciseGroups List of exercise groups.
     */
    updateOrder(courseId: number, examId: number, exerciseGroups: ExerciseGroup[]): Observable<HttpResponse<ExerciseGroup[]>> {
        return this.http.put<ExerciseGroup[]>(`${this.resourceUrl}/${courseId}/exams/${examId}/exercise-groups-order`, exerciseGroups, { observe: 'response' });
    }

    /**
     * Resets an Exam with examId by deleting all its studentExams and participations.
     * @param courseId The course id.
     * @param examId The exam id.
     */
    reset(courseId: number, examId: number): Observable<EntityResponseType> {
        return this.http
            .delete<Exam>(`${this.resourceUrl}/${courseId}/exams/${examId}/reset`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.processExamResponseFromServer(res)));
    }

    public static convertExamDatesFromClient(exam: Exam): Exam {
        return Object.assign({}, exam, {
            startDate: convertDateFromClient(exam.startDate),
            endDate: convertDateFromClient(exam.endDate),
            visibleDate: convertDateFromClient(exam.visibleDate),
            publishResultsDate: convertDateFromClient(exam.publishResultsDate),
            examStudentReviewStart: convertDateFromClient(exam.examStudentReviewStart),
            examStudentReviewEnd: convertDateFromClient(exam.examStudentReviewEnd),
        });
    }

    private processExamResponseFromServer(res: EntityResponseType): EntityResponseType {
        if (res.body) {
            this.convertExamFromServerAndSendTitles(res.body);
        }
        return res;
    }

    private processExamArrayResponseFromServer(res: EntityArrayResponseType): EntityArrayResponseType {
        if (res.body) {
            res.body.forEach(this.convertExamFromServerAndSendTitles.bind(this));
        }
        return res;
    }

    private convertExamFromServerAndSendTitles(exam: Exam) {
        exam.startDate = convertDateFromServer(exam.startDate);
        exam.endDate = convertDateFromServer(exam.endDate);
        exam.visibleDate = convertDateFromServer(exam.visibleDate);
        exam.publishResultsDate = convertDateFromServer(exam.publishResultsDate);
        exam.examStudentReviewStart = convertDateFromServer(exam.examStudentReviewStart);
        exam.examStudentReviewEnd = convertDateFromServer(exam.examStudentReviewEnd);

        if (exam.course) {
            this.accountService.setAccessRightsForCourse(exam.course);
        }

        this.sendTitlesToEntityTitleService(exam);
    }

    findAllLockedSubmissionsOfExam(courseId: number, examId: number) {
        return this.http.get<Submission[]>(`${this.resourceUrl}/${courseId}/exams/${examId}/lockedSubmissions`, { observe: 'response' }).pipe(
            filter((res) => !!res.body),
            tap((res) => reconnectSubmissions(res.body!)),
        );
    }

    /**
     * Downloads the exam archive of the specified examId. Returns an error
     * if the archive does not exist.
     * @param courseId
     * @param examId The id of the exam
     */
    downloadExamArchive(courseId: number, examId: number): Observable<HttpResponse<Blob>> {
        return this.http.get(`${this.resourceUrl}/${courseId}/exams/${examId}/download-archive`, {
            observe: 'response',
            responseType: 'blob',
        });
    }

    /**
     * Archives the exam of the specified examId.
     * @param courseId the id of the course of the exam
     * @param examId The id of the exam to archive
     */
    archiveExam(courseId: number, examId: number): Observable<HttpResponse<any>> {
        return this.http.put(`${this.resourceUrl}/${courseId}/exams/${examId}/archive`, {}, { observe: 'response' });
    }

    private sendTitlesToEntityTitleService(exam: Exam | undefined | null) {
        this.entityTitleService.setTitle(EntityType.EXAM, [exam?.id], exam?.title);
    }
}
