import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { Course } from 'app/entities/course.model';
import { ArtemisTestModule } from '../../../test.module';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { Exam } from 'app/entities/exam.model';
import * as chai from 'chai';
import dayjs from 'dayjs';
import { ExamInformationDTO } from 'app/entities/exam-information.model';
import { StudentDTO } from 'app/entities/student-dto.model';
import { StudentExam } from 'app/entities/student-exam.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { ExamScoreDTO } from 'app/exam/exam-scores/exam-score-dtos.model';
import { StatsForDashboard } from 'app/course/dashboards/stats-for-dashboard.model';
import { TextSubmission } from 'app/entities/text-submission.model';

const expect = chai.expect;
describe('Exam Management Service Tests', () => {
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
            providers: [ExamManagementService],
            imports: [ArtemisTestModule, HttpClientTestingModule],
        });

        service = TestBed.inject(ExamManagementService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should create an exam', fakeAsync(() => {
        // GIVEN
        const mockExam: Exam = { id: 1 };
        const mockCopyExam = ExamManagementService.convertDateFromClient({ id: 1 });

        // WHEN
        service.create(course.id!, mockExam).subscribe((res) => expect(res.body).to.eq(mockExam));

        // THEN
        const req = httpMock.expectOne({ method: 'POST', url: `${service.resourceUrl}/${course.id!}/exams` });
        expect(req.request.body).to.include(mockCopyExam);

        // CLEANUP
        req.flush(mockExam);
        tick();
    }));

    it('should update an exam', fakeAsync(() => {
        // GIVEN
        const mockExam: Exam = { id: 1 };
        const mockCopyExam = ExamManagementService.convertDateFromClient({ id: 1 });

        // WHEN
        service.update(course.id!, mockExam).subscribe((res) => expect(res.body).to.eq(mockExam));

        // THEN
        const req = httpMock.expectOne({ method: 'PUT', url: `${service.resourceUrl}/${course.id!}/exams` });
        expect(req.request.body).to.include(mockCopyExam);

        // CLEANUP
        req.flush(mockExam);
        tick();
    }));

    it('should find an exam with no students and no exercise groups', fakeAsync(() => {
        // GIVEN
        const mockExam: Exam = { id: 1 };
        const expected: Exam = { id: 1 };
        const mockCopyExam = ExamManagementService.convertDateFromClient(expected);
        // WHEN
        service.find(course.id!, mockExam.id!).subscribe((res) => expect(res.body).to.deep.equal(mockCopyExam));

        // THEN
        const req = httpMock.expectOne({
            method: 'GET',
            url: `${service.resourceUrl}/${course.id!}/exams/${mockExam.id}?withStudents=false&withExerciseGroups=false`,
        });
        expect(req.request.url).to.equal(`${service.resourceUrl}/${course.id!}/exams/${mockExam.id}`);
        expect(req.request.params.get('withStudents')).to.equal('false');
        expect(req.request.params.get('withExerciseGroups')).to.equal('false');

        // CLEANUP
        req.flush(expected);
        tick();
    }));

    it('should get the exam title', fakeAsync(() => {
        // GIVEN
        const mockExam: Exam = { id: 1 };
        const expectedTitle = 'expectedTitle';

        // WHEN
        service.getTitle(mockExam.id!).subscribe((res) => expect(res.body).to.eq(expectedTitle));

        // THEN
        const req = httpMock.expectOne({ method: 'GET', url: `api/exams/${mockExam.id!}/title` });
        req.flush(expectedTitle);
        tick();
    }));

    it('should get exam scores', fakeAsync(() => {
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
        service.getExamScores(course.id!, mockExam.id!).subscribe((res) => expect(res.body).to.deep.eq(expectedExamScore));

        // THEN
        const req = httpMock.expectOne({
            method: 'GET',
            url: `${service.resourceUrl}/${course.id!}/exams/${mockExam.id}/scores`,
        });
        req.flush(mockExamScore);
        tick();
    }));

    it('should get stats for exam assessment dashboard', fakeAsync(() => {
        // GIVEN
        const mockExam: Exam = { id: 1 };
        const mockStatsForDashboard = new StatsForDashboard();
        const expectedStatsForDashboard = { ...mockStatsForDashboard };

        // WHEN
        service.getStatsForExamAssessmentDashboard(course.id!, mockExam.id!).subscribe((res) => expect(res.body).to.deep.eq(expectedStatsForDashboard));

        // THEN
        const req = httpMock.expectOne({
            method: 'GET',
            url: `${service.resourceUrl}/${course.id}/exams/${mockExam.id}/stats-for-exam-assessment-dashboard`,
        });
        req.flush(mockStatsForDashboard);
        tick();
    }));

    it('should find all exams for course', fakeAsync(() => {
        // GIVEN
        const mockExamResponse = [{ ...mockExamPopulated }];

        // WHEN
        service.findAllExamsForCourse(course.id!).subscribe((res) => expect(res.body).to.deep.equal([mockExamPopulated]));

        // THEN
        const req = httpMock.expectOne({ method: 'GET', url: `${service.resourceUrl}/${course.id!}/exams` });
        req.flush(mockExamResponse);
        tick();
    }));

    it('find all exams for which the instructors have access', fakeAsync(() => {
        // GIVEN
        const mockExamResponse = [{ ...mockExamPopulated }];

        // WHEN
        service.findAllExamsAccessibleToUser(course.id!).subscribe((res) => expect(res.body).to.deep.equal([mockExamPopulated]));

        // THEN
        const req = httpMock.expectOne({ method: 'GET', url: `${service.resourceUrl}/${course.id}/exams-for-user` });
        req.flush(mockExamResponse);
        tick();
    }));

    it('should find all current and upcoming exams', fakeAsync(() => {
        // GIVEN
        const mockExamResponse = [{ ...mockExamPopulated }];

        // WHEN
        service.findAllCurrentAndUpcomingExams().subscribe((res) => expect(res.body).to.deep.equal([mockExamPopulated]));

        // THEN
        const req = httpMock.expectOne({ method: 'GET', url: `${service.resourceUrl}/upcoming-exams` });
        req.flush(mockExamResponse);
        tick();
    }));

    it('should getExamWithInterestingExercisesForAssessmentDashboard with isTestRun=false', fakeAsync(() => {
        // GIVEN
        const mockExamResponse = [{ ...mockExamPopulated }];

        // WHEN
        service
            .getExamWithInterestingExercisesForAssessmentDashboard(course.id!, mockExamPopulated.id!, false)
            .subscribe((res) => expect(res.body).to.deep.equal([mockExamPopulated]));

        // THEN
        const req = httpMock.expectOne({
            method: 'GET',
            url: `${service.resourceUrl}/${course.id!}/exams/${mockExamPopulated.id}/exam-for-assessment-dashboard`,
        });
        req.flush(mockExamResponse);
        tick();
    }));

    it('should getExamWithInterestingExercisesForAssessmentDashboard with isTestRun=true', fakeAsync(() => {
        // GIVEN
        const mockExamResponse = [{ ...mockExamPopulated }];

        // WHEN
        service
            .getExamWithInterestingExercisesForAssessmentDashboard(course.id!, mockExamPopulated.id!, true)
            .subscribe((res) => expect(res.body).to.deep.equal([mockExamPopulated]));

        // THEN
        const req = httpMock.expectOne({
            method: 'GET',
            url: `${service.resourceUrl}/${course.id!}/exams/${mockExamPopulated.id}/exam-for-test-run-assessment-dashboard`,
        });
        req.flush(mockExamResponse);
        tick();
    }));

    it('should get latest individual end date of exam', fakeAsync(() => {
        // GIVEN
        const mockExam: Exam = { id: 1 };
        const mockResponse: ExamInformationDTO = { latestIndividualEndDate: dayjs() };
        const expected = { ...mockResponse };

        // WHEN
        service.getLatestIndividualEndDateOfExam(course.id!, mockExam.id!).subscribe((res) => expect(res.body).to.deep.equal(expected));

        // THEN
        const req = httpMock.expectOne({
            method: 'GET',
            url: `${service.resourceUrl}/${course.id!}/exams/${mockExam.id!}/latest-end-date`,
        });
        req.flush(mockResponse);
        tick();
    }));

    it('should delete an exam', fakeAsync(() => {
        // GIVEN
        const mockExam: Exam = { id: 1 };

        // WHEN
        service.delete(course.id!, mockExam.id!).subscribe((res) => expect(res.body).to.be.null);

        // THEN
        const req = httpMock.expectOne({
            method: 'DELETE',
            url: `${service.resourceUrl}/${course.id!}/exams/${mockExam.id!}`,
        });

        req.flush(null);
        tick();
    }));

    it('should add student to exam', fakeAsync(() => {
        // GIVEN
        const mockExam: Exam = { id: 1 };
        const mockStudentLogin = 'studentLogin';

        // WHEN
        service.addStudentToExam(course.id!, mockExam.id!, mockStudentLogin).subscribe((res) => expect(res.body).to.be.null);

        // THEN
        const req = httpMock.expectOne({
            method: 'POST',
            url: `${service.resourceUrl}/${course.id!}/exams/${mockExam.id!}/students/${mockStudentLogin}`,
        });
        req.flush(null);
        tick();
    }));

    it('should add students to exam', fakeAsync(() => {
        // GIVEN
        const mockExam: Exam = { id: 1 };
        const mockStudents: StudentDTO[] = [
            { firstName: 'firstName1', lastName: 'lastName1', registrationNumber: '1', login: 'login1' },
            { firstName: 'firstName2', lastName: 'lastName2', registrationNumber: '2', login: 'login2' },
        ];
        const expected: StudentDTO[] = [
            { firstName: 'firstName1', lastName: 'lastName1', registrationNumber: '1', login: 'login1' },
            { firstName: 'firstName2', lastName: 'lastName2', registrationNumber: '2', login: 'login2' },
        ];

        // WHEN
        service.addStudentsToExam(course.id!, mockExam.id!, mockStudents).subscribe((res) => expect(res.body).to.deep.equal(mockStudents));

        // THEN
        const req = httpMock.expectOne({
            method: 'POST',
            url: `${service.resourceUrl}/${course.id!}/exams/${mockExam.id!}/students`,
        });
        expect(req.request.body).to.eq(mockStudents);

        // CLEAN
        req.flush(expected);
        tick();
    }));

    it('should remove student from exam with no participations and submission', fakeAsync(() => {
        // GIVEN
        const mockExam: Exam = { id: 1 };
        const mockStudentLogin = 'studentLogin';

        // WHEN
        service.removeStudentFromExam(course.id!, mockExam.id!, mockStudentLogin).subscribe((res) => expect(res.body).to.be.null);

        // THEN
        const req = httpMock.expectOne({
            method: 'DELETE',
            url: `${service.resourceUrl}/${course.id!}/exams/${mockExam.id!}/students/${mockStudentLogin}?withParticipationsAndSubmission=false`,
        });
        req.flush(null);
        tick();

        service.removeStudentFromExam(course.id!, mockExam.id!, mockStudentLogin, true).subscribe((res) => expect(res.body).to.be.null);

        // THEN
        const req2 = httpMock.expectOne({
            method: 'DELETE',
            url: `${service.resourceUrl}/${course.id!}/exams/${mockExam.id!}/students/${mockStudentLogin}?withParticipationsAndSubmission=true`,
        });
        req2.flush(null);
        tick();
    }));

    it('should remove student from exam with participations and submission', fakeAsync(() => {
        // GIVEN
        const mockExam: Exam = { id: 1 };
        const mockStudentLogin = 'studentLogin';

        // WHEN
        service.removeStudentFromExam(course.id!, mockExam.id!, mockStudentLogin, true).subscribe((res) => expect(res.body).to.be.null);

        // THEN
        const req = httpMock.expectOne({
            method: 'DELETE',
            url: `${service.resourceUrl}/${course.id!}/exams/${mockExam.id!}/students/${mockStudentLogin}?withParticipationsAndSubmission=true`,
        });
        req.flush(null);
        tick();
    }));

    it('remove all students from an exam', fakeAsync(() => {
        // GIVEN
        const mockExam: Exam = { id: 1 };
        const mockResponse = {};

        // WHEN
        service.removeAllStudentsFromExam(course.id!, mockExam.id!, false).subscribe((resp) => expect(resp.body).to.be.deep.equal({}));

        // THEN
        const req = httpMock.expectOne({
            method: 'DELETE',
            url: `${service.resourceUrl}/${course.id!}/exams/${mockExam.id!}/students?withParticipationsAndSubmission=false`,
        });
        req.flush(mockResponse);
        tick();
    }));

    it('should generate student exams', fakeAsync(() => {
        // GIVEN
        const mockExam: Exam = { id: 1 };
        const mockStudentExams: StudentExam[] = [{ exam: mockExam }];
        const expected: StudentExam[] = [{ exam: { id: 1 } }];
        // WHEN
        service.generateStudentExams(course.id!, mockExam.id!).subscribe((res) => expect(res.body).to.deep.equal(mockStudentExams));

        // THEN
        const req = httpMock.expectOne({
            method: 'POST',
            url: `${service.resourceUrl}/${course.id!}/exams/${mockExam.id!}/generate-student-exams`,
        });
        req.flush(expected);
        tick();
    }));

    it('should create test run', fakeAsync(() => {
        // GIVEN
        const mockExam: Exam = { id: 1 };
        const mockStudentExam: StudentExam = { exam: mockExam };
        const expected: StudentExam = { exam: { id: 1 } };
        // WHEN
        service.createTestRun(course.id!, mockExam.id!, mockStudentExam).subscribe((res) => expect(res.body).to.deep.equal(mockStudentExam));

        // THEN
        const req = httpMock.expectOne({
            method: 'POST',
            url: `${service.resourceUrl}/${course.id!}/exams/${mockExam.id!}/test-run`,
        });
        req.flush(expected);
        tick();
    }));

    it('should delete test run', fakeAsync(() => {
        // GIVEN
        const mockExam: Exam = { id: 1 };
        const mockStudentExam: StudentExam = { exam: mockExam, id: 2 };
        const expected: StudentExam = { exam: mockExam, id: 2 };
        // WHEN
        service.deleteTestRun(course.id!, mockExam.id!, mockStudentExam.id!).subscribe((res) => expect(res.body).to.deep.equal(mockStudentExam));

        // THEN
        const req = httpMock.expectOne({
            method: 'DELETE',
            url: `${service.resourceUrl}/${course.id}/exams/${mockExam.id}/test-run/${mockStudentExam.id}`,
        });
        req.flush(expected);
        tick();
    }));

    it('should find all test runs for exam', fakeAsync(() => {
        // GIVEN
        const mockExam: Exam = { id: 1 };
        const mockStudentExams: StudentExam[] = [{ exam: mockExam, id: 2 }];
        const expected: StudentExam[] = [{ exam: mockExam, id: 2 }];
        // WHEN
        service.findAllTestRunsForExam(course.id!, mockExam.id!).subscribe((res) => expect(res.body).to.deep.equal(mockStudentExams));

        // THEN
        const req = httpMock.expectOne({
            method: 'GET',
            url: `${service.resourceUrl}/${course.id}/exams/${mockExam.id}/test-runs`,
        });
        req.flush(expected);
        tick();
    }));

    it('should generate missing student for exam', fakeAsync(() => {
        // GIVEN
        const mockExam: Exam = { id: 1 };
        const mockStudentExams: StudentExam[] = [{ exam: mockExam, id: 2 }];
        const expected: StudentExam[] = [{ exam: mockExam, id: 2 }];
        // WHEN
        service.generateMissingStudentExams(course.id!, mockExam.id!).subscribe((res) => expect(res.body).to.deep.equal(mockStudentExams));

        // THEN
        const req = httpMock.expectOne({
            method: 'POST',
            url: `${service.resourceUrl}/${course.id}/exams/${mockExam.id}/generate-missing-student-exams`,
        });
        req.flush(expected);
        tick();
    }));

    it('should start exercises', fakeAsync(() => {
        // GIVEN
        const mockExam: Exam = { id: 1 };
        const mockStudentExams: StudentExam[] = [{ exam: mockExam, id: 1 }];
        const expected: StudentExam[] = [{ exam: mockExam, id: 1 }];

        // WHEN
        service.startExercises(course.id!, mockExam.id!).subscribe((res) => expect(res.body).to.deep.equal(mockStudentExams));

        // THEN
        const req = httpMock.expectOne({
            method: 'POST',
            url: `${service.resourceUrl}/${course.id}/exams/${mockExam.id}/student-exams/start-exercises`,
        });
        req.flush(expected);
        tick();
    }));

    it('should evaluate quiz exercises', fakeAsync(() => {
        // GIVEN
        const mockExam: Exam = { id: 1 };
        const mockEvaluatedExercises = 1;
        const expected = 1;

        // WHEN
        service.evaluateQuizExercises(course.id!, mockExam.id!).subscribe((res) => expect(res.body).to.equal(mockEvaluatedExercises));

        // THEN
        const req = httpMock.expectOne({
            method: 'POST',
            url: `${service.resourceUrl}/${course.id}/exams/${mockExam.id}/student-exams/evaluate-quiz-exercises`,
        });
        req.flush(expected);
        tick();
    }));

    it('should assess unsubmitted exam modelling and text participations', fakeAsync(() => {
        // GIVEN
        const mockExam: Exam = { id: 1 };
        const mockUnsubmittedExercises = 1;
        const expected = 1;

        // WHEN
        service.assessUnsubmittedExamModelingAndTextParticipations(course.id!, mockExam.id!).subscribe((res) => expect(res.body).to.equal(mockUnsubmittedExercises));

        // THEN
        const req = httpMock.expectOne({
            method: 'POST',
            url: `${service.resourceUrl}/${course.id}/exams/${mockExam.id}/student-exams/assess-unsubmitted-and-empty-student-exams`,
        });

        req.flush(expected);
        tick();
    }));

    it('should unlock all repositories', fakeAsync(() => {
        // GIVEN
        const mockExam: Exam = { id: 1 };
        const mockRepoCount = 1;
        const expected = 1;
        // WHEN
        service.unlockAllRepositories(course.id!, mockExam.id!).subscribe((res) => expect(res.body).to.equal(mockRepoCount));

        // THEN
        const req = httpMock.expectOne({
            method: 'POST',
            url: `${service.resourceUrl}/${course.id}/exams/${mockExam.id}/student-exams/unlock-all-repositories`,
        });
        req.flush(expected);
        tick();
    }));

    it('should lock all repositories', fakeAsync(() => {
        // GIVEN
        const mockExam: Exam = { id: 1 };
        const mockRepoCount = 1;
        const expected = 1;

        // WHEN
        service.lockAllRepositories(course.id!, mockExam.id!).subscribe((res) => expect(res.body).to.equal(mockRepoCount));

        // THEN
        const req = httpMock.expectOne({
            method: 'POST',
            url: `${service.resourceUrl}/${course.id}/exams/${mockExam.id}/student-exams/lock-all-repositories`,
        });
        req.flush(expected);
        tick();
    }));

    it('should update order', fakeAsync(() => {
        // GIVEN
        const mockExam: Exam = { id: 1 };
        const mockExerciseGroups: ExerciseGroup[] = [{ exam: mockExam, id: 1 }];
        const expected: ExerciseGroup[] = [{ exam: mockExam, id: 1 }];

        // WHEN
        service.updateOrder(course.id!, mockExam.id!, mockExerciseGroups).subscribe((res) => expect(res.body).to.deep.equal(mockExerciseGroups));

        // THEN
        const req = httpMock.expectOne({
            method: 'PUT',
            url: `${service.resourceUrl}/${course.id}/exams/${mockExam.id}/exercise-groups-order`,
        });
        req.flush(expected);
        tick();
    }));

    it('should enroll all registered students to exam', fakeAsync(() => {
        // GIVEN
        const mockExam: Exam = { id: 1 };
        const expected: StudentDTO[] = [
            { firstName: 'firstName1', lastName: 'lastName1', registrationNumber: '1', login: 'login1' },
            { firstName: 'firstName2', lastName: 'lastName2', registrationNumber: '2', login: 'login2' },
        ];

        service.addAllStudentsOfCourseToExam(course.id!, mockExam.id!).subscribe((res) => expect(res.body === null));

        const req = httpMock.expectOne({
            method: 'POST',
            url: `${service.resourceUrl}/${course.id!}/exams/${mockExam.id!}/register-course-students`,
        });
        req.flush(expected);
        tick();
    }));

    it('should find all locked submissions from exam', fakeAsync(() => {
        // GIVEN
        const mockExam: Exam = { id: 1 };
        const mockResponse = [new TextSubmission()];
        const expected = [new TextSubmission()];

        // WHEN
        service.findAllLockedSubmissionsOfExam(course.id!, mockExam.id!).subscribe((res) => expect(res.body).to.be.deep.equal(expected));

        // THEN
        const req = httpMock.expectOne({
            method: 'GET',
            url: `${service.resourceUrl}/${course.id!}/exams/${mockExam.id!}/lockedSubmissions`,
        });
        req.flush(mockResponse);
        tick();
    }));

    it('should download the exam from archive', fakeAsync(() => {
        // GIVEN
        const mockExam: Exam = { id: 1 };
        const mockResponse = new Blob();
        const expected = new Blob();

        // WHEN
        service.downloadExamArchive(course.id!, mockExam.id!).subscribe((res) => expect(res.body).to.deep.eq(expected));

        // THEN
        const req = httpMock.expectOne({
            method: 'GET',
            url: `${service.resourceUrl}/${course.id}/exams/${mockExam.id}/download-archive`,
        });
        req.flush(mockResponse);
        tick();
    }));

    it('should archive the exam', fakeAsync(() => {
        // GIVEN
        const mockExam: Exam = { id: 1, studentExams: [{ id: 1 }] };

        // WHEN
        service.archiveExam(course.id!, mockExam.id!).subscribe((res) => expect(res.body).to.deep.eq({}));

        // THEN
        const req = httpMock.expectOne({
            method: 'PUT',
            url: `${service.resourceUrl}/${course.id!}/exams/${mockExam.id}/archive`,
        });
        req.flush({});
        tick();
    }));

    it('should reset an exam', fakeAsync(() => {
        // GIVEN
        const mockExam: Exam = { id: 1 };
        const mockResponse = { id: 1 };
        const expected = { id: 1 };

        // WHEN
        service.reset(course.id!, mockExam.id!).subscribe((res) => expect(res.body).to.deep.eq(expected));

        // THEN
        const req = httpMock.expectOne({
            method: 'DELETE',
            url: `${service.resourceUrl}/${course.id!}/exams/${mockExam.id!}/reset`,
        });

        req.flush(mockResponse);
        tick();
    }));
});
