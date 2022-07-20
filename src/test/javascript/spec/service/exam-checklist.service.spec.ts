import { HttpResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Course } from 'app/entities/course.model';
import { ExamChecklist } from 'app/entities/exam-checklist.model';
import { Exam } from 'app/entities/exam.model';
import { MockProvider } from 'ng-mocks';
import { of, take } from 'rxjs';
import { ArtemisTestModule } from '../test.module';
import { ExamChecklistService } from 'app/exam/manage/exams/exam-checklist-component/exam-checklist.service';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { getExerciseGroups } from '../component/exam/manage/exams/exam-checklist.component.spec';

describe('ExamChecklistService', () => {
    let service: ExamChecklistService;
    const exam = new Exam();
    const examChecklist = new ExamChecklist();
    let examManagementService: ExamManagementService;

    let result: boolean;
    let numericResult: number;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            providers: [MockProvider(ExamManagementService)],
        })
            .compileComponents()
            .then(() => {
                service = TestBed.inject(ExamChecklistService);
                examManagementService = TestBed.inject(ExamManagementService);
            });
    });

    beforeEach(() => {
        // reset exam
        exam.id = 1;
        exam.title = 'Example Exam';
        exam.numberOfRegisteredUsers = 3;
        exam.maxPoints = 100;
        exam.course = new Course();
        exam.course.id = 2;

        examChecklist.numberOfGeneratedStudentExams = 1;
    });

    it('should indicate correctly whether all exercises are generated', () => {
        result = service.checkAllExamsGenerated(exam, examChecklist);

        expect(result).toBeFalse();

        examChecklist.numberOfGeneratedStudentExams = 2;

        result = service.checkAllExamsGenerated(exam, examChecklist);

        expect(result).toBeFalse();

        examChecklist.numberOfGeneratedStudentExams = 3;

        result = service.checkAllExamsGenerated(exam, examChecklist);

        expect(result).toBeTrue();
    });

    it('should return exam statistics correctly', () => {
        const getExamStatisticsStub = jest.spyOn(examManagementService, 'getExamStatistics').mockReturnValue(of({ body: examChecklist } as HttpResponse<ExamChecklist>));

        service
            .getExamStatistics(exam)
            .pipe(take(1))
            .subscribe((checklist) => expect(checklist).toEqual(examChecklist));

        expect(getExamStatisticsStub).toHaveBeenCalledOnce();
        expect(getExamStatisticsStub).toHaveBeenCalledWith(2, 1);
    });

    describe('test checkTotalPointsMandatory', () => {
        it('should return false if max points do not equal within an exercise group', () => {
            exam.exerciseGroups = getExerciseGroups(false);

            result = service.checkTotalPointsMandatory(false, exam);

            expect(result).toBeFalse();
        });

        it('should return true if exam points can be reached by mandatory points', () => {
            exam.exerciseGroups = getExerciseGroups(true);
            exam.exerciseGroups[0].isMandatory = true;

            result = service.checkTotalPointsMandatory(true, exam);

            expect(result).toBeTrue();
        });

        it('should return to true if exam points can be reached by optional points', () => {
            exam.exerciseGroups = getExerciseGroups(true);

            result = service.checkTotalPointsMandatory(true, exam);

            expect(result).toBeTrue();
        });

        it('should set totalPointsMandatoryOptional to false if exam points cannot be reached by mandatory points + optional points', () => {
            exam.exerciseGroups = getExerciseGroups(true);
            exam.maxPoints = 300;

            result = service.checkTotalPointsMandatory(true, exam);

            expect(result).toBeFalse();
        });
    });

    describe('test checkPointsExercisesEqual', () => {
        it('should return false if no exercise groups are present', () => {
            exam.exerciseGroups = undefined;

            result = service.checkPointsExercisesEqual(exam);

            expect(result).toBeFalse();
        });
        it('should return checkPointsExercisesEqual as true if max points equal in each exercise group', () => {
            exam.exerciseGroups = getExerciseGroups(true);

            result = service.checkPointsExercisesEqual(exam);

            expect(result).toBeTrue();
        });
        it('should return checkPointsExercisesEqual as false if max points do not equal in each exercise group', () => {
            exam.exerciseGroups = getExerciseGroups(false);

            result = service.checkPointsExercisesEqual(exam);

            expect(result).toBeFalse();
        });
    });

    describe('test checkEachGroupContainsExercise', () => {
        it('should return false if no exercise groups are present', () => {
            exam.exerciseGroups = undefined;

            result = service.checkEachGroupContainsExercise(exam);

            expect(result).toBeFalse();
        });

        it('should return true if every exercise group contains at least one exercise', () => {
            exam.exerciseGroups = getExerciseGroups(false);

            result = service.checkEachGroupContainsExercise(exam);

            expect(result).toBeTrue();
        });

        it('should return false if an exercise group does not contain exercises', () => {
            exam.exerciseGroups = [{ id: 1, exercises: [] }];

            result = service.checkEachGroupContainsExercise(exam);

            expect(result).toBeFalse();
        });
    });

    describe('test checkAtLeastOneExerciseGroup', () => {
        it('should return true if there is at least one exercise group', () => {
            exam.exerciseGroups = getExerciseGroups(true);

            result = service.checkAtLeastOneExerciseGroup(exam);

            expect(result).toBeTrue();
        });

        it('should return false if no exercise groups exist', () => {
            exam.exerciseGroups = [];

            result = service.checkAtLeastOneExerciseGroup(exam);

            expect(result).toBeFalse();
        });
    });

    describe('test checkNumberOfExerciseGroups', () => {
        beforeEach(() => {
            exam.numberOfExercisesInExam = 1;
            exam.exerciseGroups = getExerciseGroups(true);
        });

        it('should return true if number of mandatory exercise groups is equal to number of exam exercises', () => {
            exam.exerciseGroups![0].isMandatory = true;
            result = service.checkNumberOfExerciseGroups(exam);

            expect(result).toBeTrue();
        });

        it('should return true if number of mandatory exercise groups is smaller than number of exam exercises', () => {
            result = service.checkNumberOfExerciseGroups(exam);

            expect(result).toBeTrue();
        });

        it('should return false if number of mandatory exercise groups is greater than number of exam exercises', () => {
            const newExerciseGroup = {
                id: 2,
                exercises: [
                    {
                        id: 4,
                        maxPoints: 101,
                        numberOfAssessmentsOfCorrectionRounds: [{ inTime: 0, late: 0, total: 0 }],
                        studentAssignedTeamIdComputed: false,
                        secondCorrectionEnabled: false,
                    },
                ],
            };
            exam.exerciseGroups!.push(newExerciseGroup);
            exam.exerciseGroups!.forEach((group) => (group.isMandatory = true));

            result = service.checkNumberOfExerciseGroups(exam);

            expect(result).toBeFalse();
        });

        it('should return false if number of exam exercises is greater than number of exercise groups', () => {
            exam.numberOfExercisesInExam = 10;

            result = service.checkNumberOfExerciseGroups(exam);

            expect(result).toBeFalse();
        });
    });

    describe('test checkAtLeastOneRegisteredStudent', () => {
        it('should return true if at least one student is registered', () => {
            result = service.checkAtLeastOneRegisteredStudent(exam);

            expect(result).toBeTrue();
        });

        it('should return false if no student is registered', () => {
            exam.numberOfRegisteredUsers = 0;

            result = service.checkAtLeastOneRegisteredStudent(exam);

            expect(result).toBeFalse();
        });
    });

    describe('test calculateExercisePoints', () => {
        it('should return 0 if max points do not equal within an exercise group', () => {
            exam.exerciseGroups = getExerciseGroups(false);

            numericResult = service.calculateExercisePoints(false, exam);

            expect(numericResult).toBe(0);
        });

        it('should return 100 for the exam points with equal points within one exercise group with mandatory exercises', () => {
            exam.exerciseGroups = getExerciseGroups(true);
            exam.exerciseGroups[0].isMandatory = true;

            numericResult = service.calculateExercisePoints(true, exam);

            expect(numericResult).toBe(100);
        });

        it('should return 100 for the exam points with equal points within one exercise group with optional exercises', () => {
            exam.exerciseGroups = getExerciseGroups(true);

            numericResult = service.calculateExercisePoints(true, exam);

            expect(numericResult).toBe(100);
        });
    });
});
