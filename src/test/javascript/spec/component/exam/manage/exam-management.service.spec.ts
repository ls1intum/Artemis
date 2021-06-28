import { TestBed, tick, fakeAsync } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { Course } from 'app/entities/course.model';
import { ArtemisTestModule } from '../../../test.module';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { Exam } from 'app/entities/exam.model';
import * as chai from 'chai';
import * as moment from 'moment';
import { ExamInformationDTO } from 'app/entities/exam-information.model';
import { StudentDTO } from 'app/entities/student-dto.model';
import { StudentExam } from 'app/entities/student-exam.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { ExamScoreDTO } from 'app/exam/exam-scores/exam-score-dtos.model';
import { StatsForDashboard } from 'app/course/dashboards/instructor-course-dashboard/stats-for-dashboard.model';
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

    it('should create an exam', () => {
        // GIVEN
        const mockExam: Exam = {};
        const mockCopyExam = ExamManagementService.convertDateFromClient(mockExam);

        // WHEN
        service.create(course.id!, mockExam).subscribe((res) => expect(res.body).to.eq(mockExam));

        // THEN
        const req = httpMock.expectOne({ method: 'POST', url: `${service.resourceUrl}/${course.id!}/exams` });
        expect(req.request.body).to.include(mockCopyExam);

        // CLEANUP
        req.flush(mockExam);
    });

    it('should update an exam', () => {
        // GIVEN
        const mockExam: Exam = {};
        const mockCopyExam = ExamManagementService.convertDateFromClient(mockExam);

        // WHEN
        service.update(course.id!, mockExam).subscribe((res) => expect(res.body).to.eq(mockExam));

        // THEN
        const req = httpMock.expectOne({ method: 'PUT', url: `${service.resourceUrl}/${course.id!}/exams` });
        expect(req.request.body).to.include(mockCopyExam);

        // CLEANUP
        req.flush(mockExam);
    });

    it('should find an exam with no students and no exercise groups', () => {
        // GIVEN
        const mockExam: Exam = { id: 1 };

        // WHEN
        service.find(course.id!, mockExam.id!).subscribe((res) => expect(res.body).to.eq(mockExam));

        // THEN
        const req = httpMock.expectOne({ method: 'GET', url: `${service.resourceUrl}/${course.id!}/exams/${mockExam.id}?withStudents=false&withExerciseGroups=false` });
        expect(req.request.url).to.equal(`${service.resourceUrl}/${course.id!}/exams/${mockExam.id}`);
        expect(req.request.params.get('withStudents')).to.equal('false');
        expect(req.request.params.get('withExerciseGroups')).to.equal('false');

        // CLEANUP
        req.flush(mockExam);
    });

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
        const req = httpMock.expectOne({ method: 'GET', url: `${service.resourceUrl}/${course.id!}/exams/${mockExam.id}/scores` });
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
        const req = httpMock.expectOne({ method: 'GET', url: `${service.resourceUrl}/${course.id}/exams/${mockExam.id}/stats-for-exam-assessment-dashboard` });
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
        const req = httpMock.expectOne({ method: 'GET', url: `${service.resourceUrl}/${course.id!}/exams/${mockExamPopulated.id}/exam-for-assessment-dashboard` });
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
        const req = httpMock.expectOne({ method: 'GET', url: `${service.resourceUrl}/${course.id!}/exams/${mockExamPopulated.id}/exam-for-test-run-assessment-dashboard` });
        req.flush(mockExamResponse);
        tick();
    }));

    it('should get latest individual end date of exam', fakeAsync(() => {
        // GIVEN
        const mockExam: Exam = { id: 1 };
        const mockResponse: ExamInformationDTO = { latestIndividualEndDate: moment() };
        const expected = { ...mockResponse };

        // WHEN
        service.getLatestIndividualEndDateOfExam(course.id!, mockExam.id!).subscribe((res) => expect(res.body).to.deep.equal(expected));

        // THEN
        const req = httpMock.expectOne({ method: 'GET', url: `${service.resourceUrl}/${course.id!}/exams/${mockExam.id!}/latest-end-date` });
        req.flush(mockResponse);
        tick();
    }));

    it('should delete an exam', () => {
        // GIVEN
        const mockExam: Exam = { id: 1 };

        // WHEN
        service.delete(course.id!, mockExam.id!).subscribe((res) => expect(res.body).to.be.null);

        // THEN
        httpMock.expectOne({ method: 'DELETE', url: `${service.resourceUrl}/${course.id!}/exams/${mockExam.id!}` });
    });

    it('should add student to exam', () => {
        // GIVEN
        const mockExam: Exam = { id: 1 };
        const mockStudentLogin = 'studentLogin';

        // WHEN
        service.addStudentToExam(course.id!, mockExam.id!, mockStudentLogin).subscribe((res) => expect(res.body).to.be.null);

        // THEN
        httpMock.expectOne({ method: 'POST', url: `${service.resourceUrl}/${course.id!}/exams/${mockExam.id!}/students/${mockStudentLogin}` });
    });

    it('should add students to exam', () => {
        // GIVEN
        const mockExam: Exam = { id: 1 };
        const mockStudents: StudentDTO[] = [
            { firstName: 'firstName1', lastName: 'lastName1', registrationNumber: '1', login: 'login1' },
            { firstName: 'firstName2', lastName: 'lastName2', registrationNumber: '2', login: 'login2' },
        ];

        // WHEN
        service.addStudentsToExam(course.id!, mockExam.id!, mockStudents).subscribe((res) => expect(res.body).to.eq(mockStudents));

        // THEN
        const req = httpMock.expectOne({ method: 'POST', url: `${service.resourceUrl}/${course.id!}/exams/${mockExam.id!}/students` });
        expect(req.request.body).to.eq(mockStudents);

        // CLEAN
        req.flush(mockStudents);
    });

    it('should remove student from exam with no participations and submission', () => {
        // GIVEN
        const mockExam: Exam = { id: 1 };
        const mockStudentLogin = 'studentLogin';

        // WHEN
        service.removeStudentFromExam(course.id!, mockExam.id!, mockStudentLogin).subscribe((res) => expect(res.body).to.be.null);

        // THEN
        httpMock.expectOne({
            method: 'DELETE',
            url: `${service.resourceUrl}/${course.id!}/exams/${mockExam.id!}/students/${mockStudentLogin}?withParticipationsAndSubmission=false`,
        });

        service.removeStudentFromExam(course.id!, mockExam.id!, mockStudentLogin, true).subscribe((res) => expect(res.body).to.be.null);

        // THEN
        httpMock.expectOne({
            method: 'DELETE',
            url: `${service.resourceUrl}/${course.id!}/exams/${mockExam.id!}/students/${mockStudentLogin}?withParticipationsAndSubmission=true`,
        });
    });

    it('should remove student from exam with participations and submission', () => {
        // GIVEN
        const mockExam: Exam = { id: 1 };
        const mockStudentLogin = 'studentLogin';

        // WHEN
        service.removeStudentFromExam(course.id!, mockExam.id!, mockStudentLogin, true).subscribe((res) => expect(res.body).to.be.null);

        // THEN
        httpMock.expectOne({
            method: 'DELETE',
            url: `${service.resourceUrl}/${course.id!}/exams/${mockExam.id!}/students/${mockStudentLogin}?withParticipationsAndSubmission=true`,
        });
    });

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

    it('should generate student exams', () => {
        // GIVEN
        const mockExam: Exam = { id: 1 };
        const mockStudentExams: StudentExam[] = [{ exam: mockExam }];

        // WHEN
        service.generateStudentExams(course.id!, mockExam.id!).subscribe((res) => expect(res.body).to.eq(mockStudentExams));

        // THEN
        httpMock.expectOne({
            method: 'POST',
            url: `${service.resourceUrl}/${course.id!}/exams/${mockExam.id!}/generate-student-exams`,
        });
    });

    it('should create test run', () => {
        // GIVEN
        const mockExam: Exam = { id: 1 };
        const mockStudentExam: StudentExam = { exam: mockExam };

        // WHEN
        service.createTestRun(course.id!, mockExam.id!, mockStudentExam).subscribe((res) => expect(res.body).to.eq(mockStudentExam));

        // THEN
        httpMock.expectOne({
            method: 'POST',
            url: `${service.resourceUrl}/${course.id!}/exams/${mockExam.id!}/test-run`,
        });
    });

    it('should delete test run', () => {
        // GIVEN
        const mockExam: Exam = { id: 1 };
        const mockStudentExam: StudentExam = { exam: mockExam, id: 2 };

        // WHEN
        service.deleteTestRun(course.id!, mockExam.id!, mockStudentExam.id!).subscribe((res) => expect(res.body).to.eq(mockStudentExam));

        // THEN
        httpMock.expectOne({
            method: 'DELETE',
            url: `${service.resourceUrl}/${course.id}/exams/${mockExam.id}/test-run/${mockStudentExam.id}`,
        });
    });

    it('should find all test runs for exam', () => {
        // GIVEN
        const mockExam: Exam = { id: 1 };
        const mockStudentExams: StudentExam[] = [{ exam: mockExam, id: 2 }];

        // WHEN
        service.findAllTestRunsForExam(course.id!, mockExam.id!).subscribe((res) => expect(res.body).to.eq(mockStudentExams));

        // THEN
        httpMock.expectOne({
            method: 'GET',
            url: `${service.resourceUrl}/${course.id}/exams/${mockExam.id}/test-runs`,
        });
    });

    it('should generate missing student for exam', () => {
        // GIVEN
        const mockExam: Exam = { id: 1 };
        const mockStudentExams: StudentExam[] = [{ exam: mockExam, id: 2 }];

        // WHEN
        service.generateMissingStudentExams(course.id!, mockExam.id!).subscribe((res) => expect(res.body).to.eq(mockStudentExams));

        // THEN
        httpMock.expectOne({
            method: 'POST',
            url: `${service.resourceUrl}/${course.id}/exams/${mockExam.id}/generate-missing-student-exams`,
        });
    });

    it('should start exercises', () => {
        // GIVEN
        const mockExam: Exam = { id: 1 };
        const mockStudentExams: StudentExam[] = [{ exam: mockExam, id: 2 }];

        // WHEN
        service.startExercises(course.id!, mockExam.id!).subscribe((res) => expect(res.body).to.equal(mockStudentExams.length));

        // THEN
        httpMock.expectOne({
            method: 'POST',
            url: `${service.resourceUrl}/${course.id}/exams/${mockExam.id}/student-exams/start-exercises`,
        });
    });

    it('should evaluate quiz exercises', () => {
        // GIVEN
        const mockExam: Exam = { id: 1 };
        const mockEvaluatedExercises = 1;

        // WHEN
        service.evaluateQuizExercises(course.id!, mockExam.id!).subscribe((res) => expect(res.body).to.equal(mockEvaluatedExercises));

        // THEN
        httpMock.expectOne({
            method: 'POST',
            url: `${service.resourceUrl}/${course.id}/exams/${mockExam.id}/student-exams/evaluate-quiz-exercises`,
        });
    });

    it('should assess unsubmitted exam modelling and text participations', () => {
        // GIVEN
        const mockExam: Exam = { id: 1 };
        const mockUnsubmittedExercises = 1;

        // WHEN
        service.assessUnsubmittedExamModelingAndTextParticipations(course.id!, mockExam.id!).subscribe((res) => expect(res.body).to.equal(mockUnsubmittedExercises));

        // THEN
        httpMock.expectOne({
            method: 'POST',
            url: `${service.resourceUrl}/${course.id}/exams/${mockExam.id}/student-exams/assess-unsubmitted-and-empty-student-exams`,
        });
    });

    it('should unlock all repositories', () => {
        // GIVEN
        const mockExam: Exam = { id: 1 };
        const mockRepoCount = 1;

        // WHEN
        service.unlockAllRepositories(course.id!, mockExam.id!).subscribe((res) => expect(res.body).to.equal(mockRepoCount));

        // THEN
        httpMock.expectOne({
            method: 'POST',
            url: `${service.resourceUrl}/${course.id}/exams/${mockExam.id}/student-exams/unlock-all-repositories`,
        });
    });

    it('should lock all repositories', () => {
        // GIVEN
        const mockExam: Exam = { id: 1 };
        const mockRepoCount = 1;

        // WHEN
        service.lockAllRepositories(course.id!, mockExam.id!).subscribe((res) => expect(res.body).to.equal(mockRepoCount));

        // THEN
        httpMock.expectOne({
            method: 'POST',
            url: `${service.resourceUrl}/${course.id}/exams/${mockExam.id}/student-exams/lock-all-repositories`,
        });
    });

    it('should update order', () => {
        // GIVEN
        const mockExam: Exam = { id: 1 };
        const mockExerciseGroups: ExerciseGroup[] = [{ exam: mockExam, id: 1 }];

        // WHEN
        service.updateOrder(course.id!, mockExam.id!, mockExerciseGroups).subscribe((res) => expect(res.body).to.equal(mockExerciseGroups));

        // THEN
        httpMock.expectOne({
            method: 'PUT',
            url: `${service.resourceUrl}/${course.id}/exams/${mockExam.id}/exercise-groups-order`,
        });
    });

    it('should enroll all registered students to exam', () => {
        // GIVEN
        const mockExam: Exam = { id: 1 };
        const mockStudents: StudentDTO[] = [
            { firstName: 'firstName1', lastName: 'lastName1', registrationNumber: '1', login: 'login1' },
            { firstName: 'firstName2', lastName: 'lastName2', registrationNumber: '2', login: 'login2' },
        ];

        service.addAllStudentsOfCourseToExam(course.id!, mockExam.id!).subscribe((res) => expect(res.body).to.eq(mockStudents));

        httpMock.expectOne({ method: 'POST', url: `${service.resourceUrl}/${course.id!}/exams/${mockExam.id!}/register-course-students` });
    });

    it('should find all locked submissions from exam', fakeAsync(() => {
        // GIVEN
        const mockExam: Exam = { id: 1 };
        const mockResponse = [new TextSubmission()];
        const expected = [new TextSubmission()];

        // WHEN
        service.findAllLockedSubmissionsOfExam(course.id!, mockExam.id!).subscribe((res) => expect(res.body).to.be.deep.equal(expected));

        // THEN
        const req = httpMock.expectOne({ method: 'GET', url: `${service.resourceUrl}/${course.id!}/exams/${mockExam.id!}/lockedSubmissions` });
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
        const req = httpMock.expectOne({ method: 'GET', url: `${service.resourceUrl}/${course.id}/exams/${mockExam.id}/download-archive` });
        req.flush(mockResponse);
        tick();
    }));

    it('should archive the exam', fakeAsync(() => {
        // GIVEN
        const mockExam: Exam = { id: 1 };

        // WHEN
        service.archiveExam(course.id!, mockExam.id!).subscribe((res) => expect(res.body).to.deep.eq({}));

        // THEN
        const req = httpMock.expectOne({ method: 'PUT', url: `${service.resourceUrl}/${course.id!}/exams/${mockExam.id}/archive` });
        req.flush({});
        tick();
    }));
});
