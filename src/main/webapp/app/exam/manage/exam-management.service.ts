import { Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { filter, map, tap } from 'rxjs/operators';

import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import * as moment from 'moment';
import { SERVER_API_URL } from 'app/app.constants';
import { Exam } from 'app/entities/exam.model';
import { createRequestOption } from 'app/shared/util/request-util';
import { StudentDTO } from 'app/entities/student-dto.model';
import { StudentExam } from 'app/entities/student-exam.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { ExamScoreDTO } from 'app/exam/exam-scores/exam-score-dtos.model';
import { ExamInformationDTO } from 'app/entities/exam-information.model';
import { ExamChecklist } from 'app/entities/exam-checklist.model';
import { StatsForDashboard } from 'app/course/dashboards/instructor-course-dashboard/stats-for-dashboard.model';
import { getLatestSubmissionResult, setLatestSubmissionResult, Submission } from 'app/entities/submission.model';

type EntityResponseType = HttpResponse<Exam>;
type EntityArrayResponseType = HttpResponse<Exam[]>;

@Injectable({ providedIn: 'root' })
export class ExamManagementService {
    public resourceUrl = SERVER_API_URL + 'api/courses';

    constructor(private router: Router, private http: HttpClient) {}

    /**
     * Create an exam on the server using a POST request.
     * @param courseId The course id.
     * @param exam The exam to create.
     */
    create(courseId: number, exam: Exam): Observable<EntityResponseType> {
        const copy = ExamManagementService.convertDateFromClient(exam);
        return this.http
            .post<Exam>(`${this.resourceUrl}/${courseId}/exams`, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => ExamManagementService.convertDateFromServer(res)));
    }

    /**
     * Update an exam on the server using a PUT request.
     * @param courseId The course id.
     * @param exam The exam to update.
     */
    update(courseId: number, exam: Exam): Observable<EntityResponseType> {
        const copy = ExamManagementService.convertDateFromClient(exam);
        return this.http
            .put<Exam>(`${this.resourceUrl}/${courseId}/exams`, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => ExamManagementService.convertDateFromServer(res)));
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
            .pipe(map((res: EntityResponseType) => ExamManagementService.convertDateFromServer(res)));
    }

    /**
     * Fetches the title of the exam with the given id
     *
     * @param examId the id of the exam
     * @return the title of the exam in an HttpResponse, or an HttpErrorResponse on error
     */
    getTitle(examId: number): Observable<HttpResponse<string>> {
        return this.http.get(`api/exams/${examId}/title`, { observe: 'response', responseType: 'text' });
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
            .pipe(map((res: EntityArrayResponseType) => ExamManagementService.convertDateArrayFromServer(res)));
    }

    /**
     * Find all exams where the in the course they are conducted the user has instructor rights
     * @param courseId The course id where the quiz should be created
     */
    findAllExamsAccessibleToUser(courseId: number): Observable<EntityArrayResponseType> {
        return this.http
            .get<Exam[]>(`${this.resourceUrl}/${courseId}/exams-for-user`, { observe: 'response' })
            .pipe(map((res: EntityArrayResponseType) => ExamManagementService.convertDateArrayFromServer(res)));
    }

    /**
     * Find all exams that are held today and in the future.
     */
    findAllCurrentAndUpcomingExams(): Observable<EntityArrayResponseType> {
        return this.http
            .get<Exam[]>(`${this.resourceUrl}/upcoming-exams`, { observe: 'response' })
            .pipe(map((res: EntityArrayResponseType) => ExamManagementService.convertDateArrayFromServer(res)));
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
        return this.http.get<Exam>(url, { observe: 'response' }).pipe(map((res: EntityResponseType) => ExamManagementService.convertDateFromServer(res)));
    }

    getLatestIndividualEndDateOfExam(courseId: number, examId: number): Observable<HttpResponse<ExamInformationDTO>> {
        const url = `${this.resourceUrl}/${courseId}/exams/${examId}/latest-end-date`;
        return this.http.get<ExamInformationDTO>(url, { observe: 'response' }).pipe(
            map((res: HttpResponse<ExamInformationDTO>) => {
                res.body!.latestIndividualEndDate = moment(res.body!.latestIndividualEndDate);
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
     * @returns a list with the generate student exams
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
     * @returns a list with the generate student exams
     */
    generateMissingStudentExams(courseId: number, examId: number): Observable<HttpResponse<StudentExam[]>> {
        return this.http.post<any>(`${this.resourceUrl}/${courseId}/exams/${examId}/generate-missing-student-exams`, {}, { observe: 'response' });
    }

    /**
     * Start all the exercises for all the student exams belonging to the exam
     * @param courseId course to which the exam belongs
     * @param examId exam to which the student exams belong
     * @returns number of generated participations
     */
    startExercises(courseId: number, examId: number): Observable<HttpResponse<number>> {
        return this.http.post<any>(`${this.resourceUrl}/${courseId}/exams/${examId}/student-exams/start-exercises`, {}, { observe: 'response' });
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

    public static convertDateFromClient(exam: Exam): Exam {
        return Object.assign({}, exam, {
            startDate: exam.startDate && moment(exam.startDate).isValid() ? exam.startDate.toJSON() : undefined,
            endDate: exam.endDate && moment(exam.endDate).isValid() ? exam.endDate.toJSON() : undefined,
            visibleDate: exam.visibleDate && moment(exam.visibleDate).isValid() ? exam.visibleDate.toJSON() : undefined,
            publishResultsDate: exam.publishResultsDate && moment(exam.publishResultsDate).isValid() ? exam.publishResultsDate.toJSON() : undefined,
            examStudentReviewStart: exam.examStudentReviewStart && moment(exam.examStudentReviewStart).isValid() ? exam.examStudentReviewStart.toJSON() : undefined,
            examStudentReviewEnd: exam.examStudentReviewEnd && moment(exam.examStudentReviewEnd).isValid() ? exam.examStudentReviewEnd.toJSON() : undefined,
        });
    }

    private static convertDateFromServer(res: EntityResponseType): EntityResponseType {
        if (res.body) {
            this.convertExamDate(res.body);
        }
        return res;
    }

    private static convertDateArrayFromServer(res: EntityArrayResponseType): EntityArrayResponseType {
        if (res.body) {
            res.body.forEach(this.convertExamDate);
        }
        return res;
    }

    private static convertExamDate(exam: Exam) {
        exam.startDate = exam.startDate ? moment(exam.startDate) : undefined;
        exam.endDate = exam.endDate ? moment(exam.endDate) : undefined;
        exam.visibleDate = exam.visibleDate ? moment(exam.visibleDate) : undefined;
        exam.publishResultsDate = exam.publishResultsDate ? moment(exam.publishResultsDate) : undefined;
        exam.examStudentReviewStart = exam.examStudentReviewStart ? moment(exam.examStudentReviewStart) : undefined;
        exam.examStudentReviewEnd = exam.examStudentReviewEnd ? moment(exam.examStudentReviewEnd) : undefined;
    }

    findAllLockedSubmissionsOfExam(courseId: number, examId: number) {
        return this.http.get<Submission[]>(`${this.resourceUrl}/${courseId}/exams/${examId}/lockedSubmissions`, { observe: 'response' }).pipe(
            filter((res) => !!res.body),
            tap((res) =>
                res.body!.forEach((submission: Submission) => {
                    // reconnect some associations
                    const latestResult = getLatestSubmissionResult(submission);
                    if (latestResult) {
                        latestResult.submission = submission;
                        latestResult.participation = submission.participation;
                        submission.participation!.results = [latestResult!];
                        setLatestSubmissionResult(submission, latestResult);
                    }
                }),
            ),
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
}
