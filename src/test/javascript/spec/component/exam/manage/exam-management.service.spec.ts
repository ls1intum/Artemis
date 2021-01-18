import { TestBed } from '@angular/core/testing';
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

const expect = chai.expect;
describe('Exam Management Service Tests', () => {
    let service: ExamManagementService;
    let httpMock: HttpTestingController;

    const course = { id: 456 } as Course;

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
        expect(req.request.params.get('withStudents')).to.be.false;
        expect(req.request.params.get('withExerciseGroups')).to.be.false;

        // CLEANUP
        req.flush(mockExam);
    });

    it('should get exam scores', () => {
        // GIVEN
        const mockExam: Exam = { id: 1 };
        const mockExamScore: ExamScoreDTO = { examId: mockExam.id!, title: '', averagePointsAchieved: 1, exerciseGroups: [], maxPoints: 1, studentResults: [] };

        // WHEN
        service.getExamScores(course.id!, mockExam.id!).subscribe((res) => expect(res.body).to.eq(mockExamScore));

        // THEN
        const req = httpMock.expectOne({ method: 'GET', url: `${service.resourceUrl}/${course.id!}/exams/${mockExam.id}/scores` });

        // CLEANUP
        req.flush(mockExam);
    });

    it('should find all exams for course', () => {
        // WHEN
        service.findAllExamsForCourse(course.id!).subscribe((res) => expect(res.body).to.equal([]));

        // THEN
        httpMock.expectOne({ method: 'GET', url: `${service.resourceUrl}/${course.id!}/exams` });
    });

    it('should find all current and upcoming exams', () => {
        // WHEN
        service.findAllCurrentAndUpcomingExams().subscribe((res) => expect(res.body).to.equal([]));

        // THEN
        httpMock.expectOne({ method: 'GET', url: `${service.resourceUrl}/upcoming-exams` });
    });

    it('should getExamWithInterestingExercisesForAssessmentDashboard with isTestRun=false', () => {
        // GIVEN
        const mockExam: Exam = { id: 1 };

        // WHEN
        service.getExamWithInterestingExercisesForAssessmentDashboard(course.id!, mockExam.id!, false).subscribe((res) => expect(res.body).to.equal([]));

        // THEN
        httpMock.expectOne({ method: 'GET', url: `${service.resourceUrl}/${course.id!}/exams/${mockExam.id}/for-exam-tutor-dashboard` });
    });

    it('should getExamWithInterestingExercisesForAssessmentDashboard with isTestRun=true', () => {
        // GIVEN
        const mockExam: Exam = { id: 1 };

        // WHEN
        service.getExamWithInterestingExercisesForAssessmentDashboard(course.id!, mockExam.id!, true).subscribe((res) => expect(res.body).to.equal([]));

        // THEN
        httpMock.expectOne({ method: 'GET', url: `${service.resourceUrl}/${course.id!}/exams/${mockExam.id}/for-exam-tutor-test-run-dashboard` });
    });

    it('should get latest individual end date of exam', () => {
        // GIVEN
        const mockExam: Exam = { id: 1 };
        const mockResponse: ExamInformationDTO = { latestIndividualEndDate: moment() };

        // WHEN
        service.getLatestIndividualEndDateOfExam(course.id!, mockExam.id!).subscribe((res) => expect(res.body).to.equal(mockResponse));

        // THEN
        httpMock.expectOne({ method: 'GET', url: `${service.resourceUrl}/${course.id!}/exams/${mockExam.id!}/latest-end-date` });
    });

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
            url: `${service.resourceUrl}/${course.id}/exams/${mockExam.id}/exerciseGroupsOrder`,
        });
    });
});
