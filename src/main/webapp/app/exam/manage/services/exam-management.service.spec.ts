import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Course } from 'app/course/shared/entities/course.model';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { ExamStudentDTO, ExamStudentSearch } from 'app/exam/manage/students/exam-student-dto.model';
import { UserForRegistration } from 'app/shared-ui/user-registration-modal/user-for-registration.model';
import { SortingOrder } from 'app/foundation/pagination/pageable-table';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { toExamUpdateDTO } from 'app/exam/manage/services/exam-update-dto.model';
import dayjs from 'dayjs/esm';
import { ExamInformationDTO } from 'app/exam/shared/entities/exam-information.model';
import { StudentDTO } from 'app/core/shared/entities/student-dto.model';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { ExamScoreDTO } from 'app/exam/manage/exam-scores/exam-score-dtos.model';
import { StatsForDashboard } from 'app/assessment/shared/assessment-dashboard/stats-for-dashboard.model';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { AccountService } from 'app/core/auth/account.service';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { UMLDiagramType } from '@tumaet/apollon';
import { provideHttpClient } from '@angular/common/http';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
describe('Exam Management Service Tests', () => {
    setupTestBed({ zoneless: true });

    let service: ExamManagementService;
    let httpMock: HttpTestingController;

    const course = { id: 456 } as Course;
    const mockExamPopulated: Exam = {
        id: 1,
        startDate: undefined,
        endDate: undefined,
        visibleDate: undefined,
        publishResultsDate: undefined,
        examStudentReviewStart: undefined,
        examStudentReviewEnd: undefined,
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                ExamManagementService,
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
            ],
        });

        service = TestBed.inject(ExamManagementService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should create an exam', async () => {
        // GIVEN
        const mockExam: Exam = { id: 1 };
        const expectedDto = toExamUpdateDTO({ id: 1 } as Exam);

        // WHEN
        service.create(course.id!, mockExam).subscribe((res) => expect(res.body).toEqual(mockExam));

        // THEN
        const req = httpMock.expectOne({ method: 'POST', url: `${service.resourceUrl}/${course.id!}/exams` });
        expect(req.request.body).toEqual(expectedDto);

        // CLEANUP
        req.flush(mockExam);
        await Promise.resolve();
    });

    it('should update an exam', async () => {
        // GIVEN
        const mockExam: Exam = { id: 1 };
        const expectedDto = toExamUpdateDTO({ id: 1 } as Exam);

        // WHEN
        service.update(course.id!, mockExam).subscribe((res) => expect(res.body).toEqual(mockExam));

        // THEN
        const req = httpMock.expectOne({ method: 'PUT', url: `${service.resourceUrl}/${course.id!}/exams` });
        expect(req.request.body).toEqual(expectedDto);

        // CLEANUP
        req.flush(mockExam);
        await Promise.resolve();
    });

    it('should import an exam', async () => {
        // GIVEN
        const mockExam: Exam = { id: 1 };
        const expectedDto = ExamManagementService.convertExamToImportDTO({ id: 1 } as Exam, course.id!);

        // WHEN
        service.import(course.id!, mockExam).subscribe((res) => expect(res.body).toEqual(mockExam));

        // THEN
        const req = httpMock.expectOne({ method: 'POST', url: `${service.resourceUrl}/${course.id!}/exam-import` });
        expect(req.request.body).toEqual(expectedDto);

        // CLEANUP
        req.flush(mockExam);
        await Promise.resolve();
    });

    it('should import an exercise group', async () => {
        // GIVEN
        const mockExam: Exam = { id: 1 };
        const mockExerciseGroup = [{ id: 2 } as ExerciseGroup];

        // WHEN
        service.importExerciseGroup(course.id!, mockExam.id!, mockExerciseGroup).subscribe((res) => expect(res.body).toEqual(mockExerciseGroup));

        // THEN
        const req = httpMock.expectOne({ method: 'POST', url: `${service.resourceUrl}/${course.id!}/exams/${mockExam.id!}/import-exercise-group` });
        expect(req.request.body).toEqual(mockExerciseGroup);

        // CLEANUP
        req.flush(mockExerciseGroup);
        await Promise.resolve();
    });

    it('should find an exam with exercises and without course id', async () => {
        // GIVEN
        const mockExam: Exam = { id: 1, exerciseGroups: [{ id: 2 } as ExerciseGroup] };
        const expected: Exam = { id: 1, exerciseGroups: [{ id: 2 } as ExerciseGroup] };
        // WHEN
        service.findWithExercisesAndWithoutCourseId(mockExam.id!).subscribe((res) => expect(res.body).toEqual(expected));

        // THEN
        const req = httpMock.expectOne({
            method: 'GET',
            url: `api/exam/exams/${mockExam.id}`,
        });
        expect(req.request.url).toBe(`api/exam/exams/${mockExam.id}`);

        // CLEANUP
        req.flush(expected);
        await Promise.resolve();
    });

    it('should find an exam without exercise groups', async () => {
        // GIVEN
        const mockExam: Exam = { id: 1 };
        const expected: Exam = { id: 1 };
        // WHEN
        service.find(course.id!, mockExam.id!).subscribe((res) => expect(res.body).toEqual(expected));

        // THEN
        const req = httpMock.expectOne({
            method: 'GET',
            url: `${service.resourceUrl}/${course.id!}/exams/${mockExam.id}?withExerciseGroups=false`,
        });
        expect(req.request.url).toBe(`${service.resourceUrl}/${course.id!}/exams/${mockExam.id}`);
        expect(req.request.params.get('withExerciseGroups')).toBe('false');

        // CLEANUP
        req.flush(expected);
        await Promise.resolve();
    });

    it('should get exam scores', async () => {
        // GIVEN
        const mockExam: Exam = { id: 1 };
        const mockExamScore: ExamScoreDTO = {
            examId: mockExam.id!,
            title: '',
            averagePointsAchieved: 1,
            exerciseGroups: [],
            maxPoints: 1,
            hasSecondCorrectionAndStarted: false,
            studentResults: [],
        };
        const expectedExamScore = { ...mockExamScore };

        // WHEN
        service.getExamScores(course.id!, mockExam.id!).subscribe((res) => expect(res.body).toEqual(expectedExamScore));

        // THEN
        const req = httpMock.expectOne({
            method: 'GET',
            url: `${service.resourceUrl}/${course.id!}/exams/${mockExam.id}/scores`,
        });
        req.flush(mockExamScore);
        await Promise.resolve();
    });

    it('should get stats for exam assessment dashboard', async () => {
        // GIVEN
        const mockExam: Exam = { id: 1 };
        const mockStatsForDashboard = new StatsForDashboard();
        const expectedStatsForDashboard = { ...mockStatsForDashboard };

        // WHEN
        service.getStatsForExamAssessmentDashboard(course.id!, mockExam.id!).subscribe((res) => expect(res.body).toEqual(expectedStatsForDashboard));

        // THEN
        const req = httpMock.expectOne({
            method: 'GET',
            url: `${service.resourceUrl}/${course.id}/exams/${mockExam.id}/stats-for-exam-assessment-dashboard`,
        });
        req.flush(mockStatsForDashboard);
        await Promise.resolve();
    });

    it('should find all exams for course', async () => {
        // GIVEN
        const mockExamResponse = [{ ...mockExamPopulated }];

        // WHEN
        service.findAllExamsForCourse(course.id!).subscribe((res) => expect(res.body).toEqual([mockExamPopulated]));

        // THEN
        const req = httpMock.expectOne({ method: 'GET', url: `${service.resourceUrl}/${course.id!}/exams` });
        req.flush(mockExamResponse);
        await Promise.resolve();
    });

    it('find all exams for which the instructors have access', async () => {
        // GIVEN
        const mockExamResponse = [{ ...mockExamPopulated }];

        // WHEN
        service.findAllExamsAccessibleToUser(course.id!).subscribe((res) => expect(res.body).toEqual([mockExamPopulated]));

        // THEN
        const req = httpMock.expectOne({ method: 'GET', url: `${service.resourceUrl}/${course.id}/exams-for-user` });
        req.flush(mockExamResponse);
        await Promise.resolve();
    });

    it('should find all current and upcoming exams', async () => {
        // GIVEN
        const mockExamResponse = [{ ...mockExamPopulated }];

        // WHEN
        service.findAllCurrentAndUpcomingExams().subscribe((res) => expect(res.body).toEqual([mockExamPopulated]));

        // THEN
        const req = httpMock.expectOne({ method: 'GET', url: `${service.adminResourceUrl}/upcoming-exams` });
        req.flush(mockExamResponse);
        await Promise.resolve();
    });

    it('should getExamWithInterestingExercisesForAssessmentDashboard with isTestRun=false', async () => {
        // GIVEN
        const mockExamResponse = [{ ...mockExamPopulated }];

        // WHEN
        service.getExamWithInterestingExercisesForAssessmentDashboard(course.id!, mockExamPopulated.id!, false).subscribe((res) => expect(res.body).toEqual([mockExamPopulated]));

        // THEN
        const req = httpMock.expectOne({
            method: 'GET',
            url: `${service.resourceUrl}/${course.id!}/exams/${mockExamPopulated.id}/exam-for-assessment-dashboard`,
        });
        req.flush(mockExamResponse);
        await Promise.resolve();
    });

    it('should getExamWithInterestingExercisesForAssessmentDashboard with isTestRun=true', async () => {
        // GIVEN
        const mockExamResponse = [{ ...mockExamPopulated }];

        // WHEN
        service.getExamWithInterestingExercisesForAssessmentDashboard(course.id!, mockExamPopulated.id!, true).subscribe((res) => expect(res.body).toEqual([mockExamPopulated]));

        // THEN
        const req = httpMock.expectOne({
            method: 'GET',
            url: `${service.resourceUrl}/${course.id!}/exams/${mockExamPopulated.id}/exam-for-test-run-assessment-dashboard`,
        });
        req.flush(mockExamResponse);
        await Promise.resolve();
    });

    it('should get latest individual end date of exam', async () => {
        // GIVEN
        const mockExam: Exam = { id: 1 };
        const mockResponse: ExamInformationDTO = { latestIndividualEndDate: dayjs() };
        const expected = { ...mockResponse };

        // WHEN
        service.getLatestIndividualEndDateOfExam(course.id!, mockExam.id!).subscribe((res) => expect(res.body).toEqual(expected));

        // THEN
        const req = httpMock.expectOne({
            method: 'GET',
            url: `${service.resourceUrl}/${course.id!}/exams/${mockExam.id!}/latest-end-date`,
        });
        req.flush(mockResponse);
        await Promise.resolve();
    });

    it('should delete an exam', async () => {
        // GIVEN
        const mockExam: Exam = { id: 1 };

        // WHEN
        service.delete(course.id!, mockExam.id!).subscribe((res) => expect(res.body).toBeNull());

        // THEN
        const req = httpMock.expectOne({
            method: 'DELETE',
            url: `${service.resourceUrl}/${course.id!}/exams/${mockExam.id!}`,
        });

        req.flush(null);
        await Promise.resolve();
    });

    it('should add student to exam', async () => {
        // GIVEN
        const mockExam: Exam = { id: 1 };
        const mockStudentLogin = 'studentLogin';

        // WHEN
        service.addStudentToExam(course.id!, mockExam.id!, mockStudentLogin).subscribe((res) => expect(res.body).toBeNull());

        // THEN
        const req = httpMock.expectOne({
            method: 'POST',
            url: `${service.resourceUrl}/${course.id!}/exams/${mockExam.id!}/students/${mockStudentLogin}`,
        });
        req.flush(null);
        await Promise.resolve();
    });

    it('should add students to exam', async () => {
        // GIVEN
        const mockExam: Exam = { id: 1 };
        const mockStudents: StudentDTO[] = [
            { firstName: 'firstName1', lastName: 'lastName1', registrationNumber: '1', login: 'login1', email: '' },
            { firstName: 'firstName2', lastName: 'lastName2', registrationNumber: '2', login: 'login2', email: '' },
        ];
        const expected: StudentDTO[] = [
            { firstName: 'firstName1', lastName: 'lastName1', registrationNumber: '1', login: 'login1', email: '' },
            { firstName: 'firstName2', lastName: 'lastName2', registrationNumber: '2', login: 'login2', email: '' },
        ];

        // WHEN
        service.addStudentsToExam(course.id!, mockExam.id!, mockStudents).subscribe((res) => expect(res.body).toEqual(mockStudents));

        // THEN
        const req = httpMock.expectOne({
            method: 'POST',
            url: `${service.resourceUrl}/${course.id!}/exams/${mockExam.id!}/students`,
        });
        expect(req.request.body).toEqual(mockStudents);

        // CLEAN
        req.flush(expected);
        await Promise.resolve();
    });

    it('should remove student from exam with no participations and submission', async () => {
        // GIVEN
        const mockExam: Exam = { id: 1 };
        const mockStudentLogin = 'studentLogin';

        // WHEN
        service.removeStudentFromExam(course.id!, mockExam.id!, mockStudentLogin).subscribe((res) => expect(res.body).toBeNull());

        // THEN
        const req = httpMock.expectOne({
            method: 'DELETE',
            url: `${service.resourceUrl}/${course.id!}/exams/${mockExam.id!}/students/${mockStudentLogin}?withParticipationsAndSubmission=false`,
        });
        req.flush(null);
        await Promise.resolve();

        service.removeStudentFromExam(course.id!, mockExam.id!, mockStudentLogin, true).subscribe((res) => expect(res.body).toBeNull());

        // THEN
        const req2 = httpMock.expectOne({
            method: 'DELETE',
            url: `${service.resourceUrl}/${course.id!}/exams/${mockExam.id!}/students/${mockStudentLogin}?withParticipationsAndSubmission=true`,
        });
        req2.flush(null);
        await Promise.resolve();
    });

    it('should remove student from exam with participations and submission', async () => {
        // GIVEN
        const mockExam: Exam = { id: 1 };
        const mockStudentLogin = 'studentLogin';

        // WHEN
        service.removeStudentFromExam(course.id!, mockExam.id!, mockStudentLogin, true).subscribe((res) => expect(res.body).toBeNull());

        // THEN
        const req = httpMock.expectOne({
            method: 'DELETE',
            url: `${service.resourceUrl}/${course.id!}/exams/${mockExam.id!}/students/${mockStudentLogin}?withParticipationsAndSubmission=true`,
        });
        req.flush(null);
        await Promise.resolve();
    });

    it('remove all students from an exam', async () => {
        // GIVEN
        const mockExam: Exam = { id: 1 };
        const mockResponse = {};

        // WHEN
        service.removeAllStudentsFromExam(course.id!, mockExam.id!, false).subscribe((resp) => expect(resp.body).toEqual({}));

        // THEN
        const req = httpMock.expectOne({
            method: 'DELETE',
            url: `${service.resourceUrl}/${course.id!}/exams/${mockExam.id!}/students?withParticipationsAndSubmission=false`,
        });
        req.flush(mockResponse);
        await Promise.resolve();
    });

    it('should generate student exams', async () => {
        // GIVEN
        const mockExam: Exam = { id: 1 };
        const mockStudentExams: StudentExam[] = [{ exam: mockExam, numberOfExamSessions: 0 }];
        const expected: StudentExam[] = [{ exam: { id: 1 }, numberOfExamSessions: 0 }];
        // WHEN
        service.generateStudentExams(course.id!, mockExam.id!).subscribe((res) => expect(res.body).toEqual(mockStudentExams));

        // THEN
        const req = httpMock.expectOne({
            method: 'POST',
            url: `${service.resourceUrl}/${course.id!}/exams/${mockExam.id!}/generate-student-exams`,
        });
        req.flush(expected);
        await Promise.resolve();
    });

    it('should create test run', async () => {
        // GIVEN
        const mockExam: Exam = { id: 1 };
        const mockStudentExam: StudentExam = { exam: mockExam, numberOfExamSessions: 0 };
        const expected: StudentExam = { exam: { id: 1 }, numberOfExamSessions: 0 };
        // WHEN
        service.createTestRun(course.id!, mockExam.id!, mockStudentExam).subscribe((res) => expect(res.body).toEqual(mockStudentExam));

        // THEN
        const req = httpMock.expectOne({
            method: 'POST',
            url: `${service.resourceUrl}/${course.id!}/exams/${mockExam.id!}/test-runs`,
        });
        req.flush(expected);
        await Promise.resolve();
    });

    it('should delete test run', async () => {
        // GIVEN
        const mockExam: Exam = { id: 1 };
        const mockStudentExam: StudentExam = { exam: mockExam, id: 2, numberOfExamSessions: 0 };
        const expected: StudentExam = { exam: mockExam, id: 2, numberOfExamSessions: 0 };
        // WHEN
        service.deleteTestRun(course.id!, mockExam.id!, mockStudentExam.id!).subscribe((res) => expect(res.body).toEqual(mockStudentExam));

        // THEN
        const req = httpMock.expectOne({
            method: 'DELETE',
            url: `${service.resourceUrl}/${course.id}/exams/${mockExam.id}/test-runs/${mockStudentExam.id}`,
        });
        req.flush(expected);
        await Promise.resolve();
    });

    it('should find all test runs for exam', async () => {
        // GIVEN
        const mockExam: Exam = { id: 1 };
        const mockStudentExams: StudentExam[] = [{ exam: mockExam, id: 2, numberOfExamSessions: 0 }];
        const expected: StudentExam[] = [{ exam: mockExam, id: 2, numberOfExamSessions: 0 }];
        // WHEN
        service.findAllTestRunsForExam(course.id!, mockExam.id!).subscribe((res) => expect(res.body).toEqual(mockStudentExams));

        // THEN
        const req = httpMock.expectOne({
            method: 'GET',
            url: `${service.resourceUrl}/${course.id}/exams/${mockExam.id}/test-runs`,
        });
        req.flush(expected);
        await Promise.resolve();
    });

    it('should generate missing student for exam', async () => {
        // GIVEN
        const mockExam: Exam = { id: 1 };
        const mockStudentExams: StudentExam[] = [{ exam: mockExam, id: 2, numberOfExamSessions: 0 }];
        const expected: StudentExam[] = [{ exam: mockExam, id: 2, numberOfExamSessions: 0 }];
        // WHEN
        service.generateMissingStudentExams(course.id!, mockExam.id!).subscribe((res) => expect(res.body).toEqual(mockStudentExams));

        // THEN
        const req = httpMock.expectOne({
            method: 'POST',
            url: `${service.resourceUrl}/${course.id}/exams/${mockExam.id}/generate-missing-student-exams`,
        });
        req.flush(expected);
        await Promise.resolve();
    });

    it('should start exercises', async () => {
        // GIVEN
        const mockExam: Exam = { id: 1 };
        const mockStudentExams: StudentExam[] = [{ exam: mockExam, id: 1, numberOfExamSessions: 0 }];
        const expected: StudentExam[] = [{ exam: mockExam, id: 1, numberOfExamSessions: 0 }];

        // WHEN
        service.startExercises(course.id!, mockExam.id!).subscribe((res) => expect(res.body).toEqual(mockStudentExams));

        // THEN
        const req = httpMock.expectOne({
            method: 'POST',
            url: `${service.resourceUrl}/${course.id}/exams/${mockExam.id}/student-exams/start-exercises`,
        });
        req.flush(expected);
        await Promise.resolve();
    });

    it('should evaluate quiz exercises', async () => {
        // GIVEN
        const mockExam: Exam = { id: 1 };
        const mockEvaluatedExercises = 1;
        const expected = 1;

        // WHEN
        service.evaluateQuizExercises(course.id!, mockExam.id!).subscribe((res) => expect(res.body).toEqual(mockEvaluatedExercises));

        // THEN
        const req = httpMock.expectOne({
            method: 'POST',
            url: `${service.resourceUrl}/${course.id}/exams/${mockExam.id}/student-exams/evaluate-quiz-exercises`,
        });
        req.flush(expected);
        await Promise.resolve();
    });

    it('should assess unsubmitted exam modelling and text participations', async () => {
        // GIVEN
        const mockExam: Exam = { id: 1 };
        const mockUnsubmittedExercises = 1;
        const expected = 1;

        // WHEN
        service.assessUnsubmittedExamModelingAndTextParticipations(course.id!, mockExam.id!).subscribe((res) => expect(res.body).toEqual(mockUnsubmittedExercises));

        // THEN
        const req = httpMock.expectOne({
            method: 'POST',
            url: `${service.resourceUrl}/${course.id}/exams/${mockExam.id}/student-exams/assess-unsubmitted-and-empty-student-exams`,
        });

        req.flush(expected);
        await Promise.resolve();
    });

    it('should update order', async () => {
        // GIVEN
        const mockExam: Exam = { id: 1 };
        const mockExerciseGroups: ExerciseGroup[] = [{ exam: mockExam, id: 1 }];
        const expected: ExerciseGroup[] = [{ exam: mockExam, id: 1 }];

        // WHEN
        service.updateOrder(course.id!, mockExam.id!, mockExerciseGroups).subscribe((res) => expect(res.body).toEqual(mockExerciseGroups));

        // THEN
        const req = httpMock.expectOne({
            method: 'PUT',
            url: `${service.resourceUrl}/${course.id}/exams/${mockExam.id}/exercise-groups-order`,
        });
        req.flush(expected);
        await Promise.resolve();
    });

    it('should enroll all registered students to exam', async () => {
        // GIVEN
        const mockExam: Exam = { id: 1 };
        const expected: StudentDTO[] = [
            { firstName: 'firstName1', lastName: 'lastName1', registrationNumber: '1', login: 'login1', email: '' },
            { firstName: 'firstName2', lastName: 'lastName2', registrationNumber: '2', login: 'login2', email: '' },
        ];

        service.addAllStudentsOfCourseToExam(course.id!, mockExam.id!).subscribe((res) => expect(res.body).toBeUndefined());

        const req = httpMock.expectOne({
            method: 'POST',
            url: `${service.resourceUrl}/${course.id!}/exams/${mockExam.id!}/register-course-students`,
        });
        req.flush(expected);
        await Promise.resolve();
    });

    it('should find all locked submissions from exam', async () => {
        // GIVEN
        const mockExam: Exam = { id: 1 };
        const mockResponse = [new TextSubmission()];
        const expected = [new TextSubmission()];

        // WHEN
        service.findAllLockedSubmissionsOfExam(course.id!, mockExam.id!).subscribe((res) => expect(res.body).toEqual(expected));

        // THEN
        const req = httpMock.expectOne({
            method: 'GET',
            url: `${service.resourceUrl}/${course.id!}/exams/${mockExam.id!}/locked-submissions`,
        });
        req.flush(mockResponse);
        await Promise.resolve();
    });

    it('should download the exam from archive', async () => {
        const mockExam: Exam = { id: 1 };

        const windowSpy = vi.spyOn(window, 'open').mockImplementation(() => null);
        service.downloadExamArchive(course.id!, mockExam.id!);
        expect(windowSpy).toHaveBeenCalledWith('api/exam/courses/456/exams/1/download-archive', '_blank');
    });

    it('should archive the exam', async () => {
        // GIVEN
        const mockExam: Exam = { id: 1, studentExams: [{ id: 1, numberOfExamSessions: 0 }] };

        // WHEN
        service.archiveExam(course.id!, mockExam.id!).subscribe((res) => expect(res.body).toEqual({}));

        // THEN
        const req = httpMock.expectOne({
            method: 'PUT',
            url: `${service.resourceUrl}/${course.id!}/exams/${mockExam.id}/archive`,
        });
        req.flush({});
        await Promise.resolve();
    });

    it('should reset an exam', async () => {
        const accountService = TestBed.inject(AccountService);
        const accountServiceSpy = vi.spyOn(accountService, 'setAccessRightsForCourse').mockImplementation(() => undefined);

        // GIVEN
        const mockExam: Exam = { id: 1, course };

        // WHEN
        service.reset(course.id!, mockExam.id!).subscribe((res) => expect(res.body).toEqual(mockExam));

        // THEN
        const req = httpMock.expectOne({
            method: 'DELETE',
            url: `${service.resourceUrl}/${course.id!}/exams/${mockExam.id!}/reset`,
        });

        req.flush(mockExam);
        await Promise.resolve();

        expect(accountServiceSpy).toHaveBeenCalledOnce();
        expect(accountServiceSpy).toHaveBeenCalledWith(course);
    });

    it('should make GET request to retrieve exam exercises that potentially have plagiarism cases', async () => {
        const exerciseGroup = new ExerciseGroup();
        const textExercise = new TextExercise(undefined, exerciseGroup);
        const modelingExercise = new ModelingExercise(UMLDiagramType.ActivityDiagram, course, exerciseGroup);
        const programmingExercise = new ProgrammingExercise(undefined, exerciseGroup);

        const exercises = [textExercise, modelingExercise, programmingExercise];
        service.getExercisesWithPotentialPlagiarismForExam(1, 1).subscribe((resp) => expect(resp).toEqual(exercises));
        const req = httpMock.expectOne({ method: 'GET', url: 'api/exam/courses/1/exams/1/exercises-with-potential-plagiarism' });
        req.flush(exercises);
        await Promise.resolve();
    });

    it('should verify user attendance', async () => {
        // GIVEN
        const mockExam: Exam = { id: 1 };

        // WHEN
        service.isAttendanceChecked(course.id!, mockExam.id!).subscribe((res) => expect(res.body).toBe(true));

        // THEN
        const req = httpMock.expectOne({ method: 'GET', url: `${service.resourceUrl}/${course.id!}/exams/${mockExam.id!}/attendance` });

        // CLEANUP
        req.flush(true);
    });

    describe('findExamStudentsPaged', () => {
        const examId = 1;
        const baseSearch: ExamStudentSearch = {
            page: 0,
            pageSize: 20,
            sortingOrder: SortingOrder.ASCENDING,
            sortedColumn: 'name',
            searchTerm: '',
        };

        it('should send GET request with correct URL and params', async () => {
            // GIVEN
            const mockBody: ExamStudentDTO[] = [{ id: 10, login: 'student1' }];

            // WHEN
            service.findExamStudentsPaged(course.id!, examId, baseSearch).subscribe((result) => {
                expect(result.content).toHaveLength(1);
                expect(result.content[0].login).toBe('student1');
                expect(result.totalElements).toBe(1);
            });

            // THEN
            const req = httpMock.expectOne((r) => r.method === 'GET' && r.url === `${service.resourceUrl}/${course.id!}/exams/${examId}/exam-students/paged`);
            expect(req.request.params.get('page')).toBe('0');
            expect(req.request.params.get('pageSize')).toBe('20');
            expect(req.request.params.get('sortingOrder')).toBe(SortingOrder.ASCENDING);
            expect(req.request.params.get('sortedColumn')).toBe('name');
            expect(req.request.params.get('searchTerm')).toBe('');
            expect(req.request.params.has('filterProp')).toBe(false);

            // CLEANUP
            req.flush(mockBody, { headers: { 'X-Total-Count': '1' } });
        });

        it('should include filterProp in params when provided', async () => {
            // GIVEN
            const searchWithFilter: ExamStudentSearch = { ...baseSearch, filterProp: 'Submitted' };

            // WHEN
            service.findExamStudentsPaged(course.id!, examId, searchWithFilter).subscribe();

            // THEN
            const req = httpMock.expectOne((r) => r.method === 'GET' && r.url === `${service.resourceUrl}/${course.id!}/exams/${examId}/exam-students/paged`);
            expect(req.request.params.get('filterProp')).toBe('Submitted');

            // CLEANUP
            req.flush([]);
        });

        it('should read totalElements from X-Total-Count header', async () => {
            // GIVEN
            let capturedTotal: number | undefined;

            // WHEN
            service.findExamStudentsPaged(course.id!, examId, baseSearch).subscribe((result) => {
                capturedTotal = result.totalElements;
            });

            // THEN
            const req = httpMock.expectOne((r) => r.method === 'GET' && r.url === `${service.resourceUrl}/${course.id!}/exams/${examId}/exam-students/paged`);

            // CLEANUP
            req.flush([], { headers: { 'X-Total-Count': '42' } });

            expect(capturedTotal).toBe(42);
        });

        it('should convert startedDate and submissionDate from server format', async () => {
            // GIVEN
            const isoDate = '2024-06-15T10:00:00Z';
            const mockBody: ExamStudentDTO[] = [{ id: 10, startedDate: isoDate as any, submissionDate: isoDate as any }];
            let capturedRow: ExamStudentDTO | undefined;

            // WHEN
            service.findExamStudentsPaged(course.id!, examId, baseSearch).subscribe((result) => {
                capturedRow = result.content[0];
            });

            // THEN
            const req = httpMock.expectOne((r) => r.method === 'GET' && r.url === `${service.resourceUrl}/${course.id!}/exams/${examId}/exam-students/paged`);

            // CLEANUP
            req.flush(mockBody, { headers: { 'X-Total-Count': '1' } });

            expect(capturedRow?.startedDate).toBeDefined();
            expect(capturedRow?.submissionDate).toBeDefined();
            // convertDateFromServer wraps them in dayjs — verify they are dayjs objects
            expect(typeof capturedRow?.startedDate?.isValid).toBe('function');
            expect(typeof capturedRow?.submissionDate?.isValid).toBe('function');
        });

        it('should default totalElements to 0 when X-Total-Count header is absent', async () => {
            // GIVEN
            let capturedTotal: number | undefined;

            // WHEN
            service.findExamStudentsPaged(course.id!, examId, baseSearch).subscribe((result) => {
                capturedTotal = result.totalElements;
            });

            // THEN
            const req = httpMock.expectOne((r) => r.method === 'GET' && r.url === `${service.resourceUrl}/${course.id!}/exams/${examId}/exam-students/paged`);

            // CLEANUP
            req.flush([]);

            expect(capturedTotal).toBe(0);
        });
    });

    describe('searchUsersForExamRegistration', () => {
        const examId = 1;

        it('should send GET request with correct URL and params', async () => {
            // GIVEN
            const mockUsers: UserForRegistration[] = [{ id: 1, login: 'alice', name: 'Alice', isRegistered: false }];

            // WHEN
            service.searchUsersForExamRegistration(course.id!, examId, 'alice', 0, 10).subscribe((result) => {
                expect(result.content).toHaveLength(1);
                expect(result.content[0].login).toBe('alice');
                expect(result.totalElements).toBe(1);
            });

            // THEN
            const req = httpMock.expectOne((r) => r.method === 'GET' && r.url === `${service.resourceUrl}/${course.id!}/exams/${examId}/students/search`);
            expect(req.request.params.get('searchTerm')).toBe('alice');
            expect(req.request.params.get('page')).toBe('0');
            expect(req.request.params.get('size')).toBe('10');

            // CLEANUP
            req.flush(mockUsers, { headers: { 'X-Total-Count': '1' } });
        });

        it('should read totalElements from X-Total-Count header', async () => {
            // GIVEN
            let capturedTotal: number | undefined;

            // WHEN
            service.searchUsersForExamRegistration(course.id!, examId, 'alice', 0, 10).subscribe((result) => {
                capturedTotal = result.totalElements;
            });

            // THEN
            const req = httpMock.expectOne((r) => r.method === 'GET' && r.url === `${service.resourceUrl}/${course.id!}/exams/${examId}/students/search`);

            // CLEANUP
            req.flush([], { headers: { 'X-Total-Count': '99' } });

            expect(capturedTotal).toBe(99);
        });

        it('should default totalElements to 0 when X-Total-Count header is absent', async () => {
            // GIVEN
            let capturedTotal: number | undefined;

            // WHEN
            service.searchUsersForExamRegistration(course.id!, examId, 'alice', 0, 10).subscribe((result) => {
                capturedTotal = result.totalElements;
            });

            // THEN
            const req = httpMock.expectOne((r) => r.method === 'GET' && r.url === `${service.resourceUrl}/${course.id!}/exams/${examId}/students/search`);

            // CLEANUP
            req.flush([]);

            expect(capturedTotal).toBe(0);
        });

        it('should return empty content array when response body is null', async () => {
            // GIVEN
            let capturedContent: UserForRegistration[] | undefined;

            // WHEN
            service.searchUsersForExamRegistration(course.id!, examId, 'alice', 0, 10).subscribe((result) => {
                capturedContent = result.content;
            });

            // THEN
            const req = httpMock.expectOne((r) => r.method === 'GET' && r.url === `${service.resourceUrl}/${course.id!}/exams/${examId}/students/search`);

            // CLEANUP
            req.flush(null, { headers: { 'X-Total-Count': '0' } });

            expect(capturedContent).toEqual([]);
        });
    });
});
