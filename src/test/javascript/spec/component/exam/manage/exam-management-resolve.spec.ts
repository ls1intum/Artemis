import { HttpResponse } from '@angular/common/http';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ActivatedRouteSnapshot } from '@angular/router';
import { Exam } from 'app/entities/exam.model';
import { StudentExamWithGradeDTO } from 'app/exam/exam-scores/exam-score-dtos.model';
import { ExamResolve, ExerciseGroupResolve, StudentExamResolve } from 'app/exam/manage/exam-management-resolve.service';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';
import { StudentExamService } from 'app/exam/manage/student-exams/student-exam.service';
import { MockProvider } from 'ng-mocks';
import { of } from 'rxjs';
import { ArtemisTestModule } from '../../../test.module';

describe('Exam Resolve', () => {
    let resolve: ExamResolve;
    let examManagementService: ExamManagementService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [ExamResolve, MockProvider(ExamManagementService)],
            imports: [ArtemisTestModule],
        });

        resolve = TestBed.inject(ExamResolve);
        examManagementService = TestBed.inject(ExamManagementService);
    });

    afterEach(() => jest.restoreAllMocks());

    it('should fetch the exam if courseId and examId are given', fakeAsync(() => {
        const findSpy = jest.spyOn(examManagementService, 'find').mockReturnValue(of(new HttpResponse({ status: 200, body: { id: 1 } })));
        const examObservable = resolve.resolve({
            params: {
                courseId: 1,
                examId: 2,
            },
            data: {
                requestOptions: {
                    withStudents: true,
                    withExerciseGroups: true,
                },
            },
        } as any as ActivatedRouteSnapshot);

        let receivedExam = undefined;
        examObservable.subscribe((exam) => (receivedExam = exam));
        tick();

        expect(findSpy).toHaveBeenCalledOnce();
        expect(findSpy).toHaveBeenCalledWith(1, 2, true, true);
        expect(receivedExam).toEqual({ id: 1 });
    }));

    it('should create a new exam if courseId and/or examId are not given', fakeAsync(() => {
        const findSpy = jest.spyOn(examManagementService, 'find');
        const examObservable = resolve.resolve({
            params: {},
            data: {
                requestOptions: {
                    withStudents: true,
                    withExerciseGroups: true,
                },
            },
        } as any as ActivatedRouteSnapshot);

        let receivedExam = undefined;
        examObservable.subscribe((exam) => (receivedExam = exam));
        tick();

        expect(findSpy).not.toHaveBeenCalled();
        expect(receivedExam).toEqual(new Exam());
    }));

    it('should fetch the exam for an import', fakeAsync(() => {
        const findSpy = jest.spyOn(examManagementService, 'findWithExercisesAndWithoutCourseId').mockReturnValue(of(new HttpResponse({ status: 200, body: { id: 1 } })));
        const examObservable = resolve.resolve({
            params: {
                examId: 2,
            },
            url: of([{ path: 'import' }]),
            data: {
                requestOptions: {
                    forImport: true,
                },
            },
        } as any as ActivatedRouteSnapshot);

        let receivedExam = undefined;
        examObservable.subscribe((exam) => (receivedExam = exam));
        tick();

        expect(findSpy).toHaveBeenCalledOnce();
        expect(findSpy).toHaveBeenCalledWith(2);
        expect(receivedExam).toEqual({ id: 1 });
    }));
});

describe('Exam Group Resolve', () => {
    let resolve: ExerciseGroupResolve;
    let exerciseGroupService: ExerciseGroupService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [ExerciseGroupResolve, MockProvider(ExerciseGroupService)],
            imports: [ArtemisTestModule],
        });

        resolve = TestBed.inject(ExerciseGroupResolve);
        exerciseGroupService = TestBed.inject(ExerciseGroupService);
    });

    afterEach(() => jest.restoreAllMocks());

    it('should fetch the exercise group if courseId, examId and exerciseGroupId are given', fakeAsync(() => {
        const findSpy = jest.spyOn(exerciseGroupService, 'find').mockReturnValue(of(new HttpResponse({ status: 200, body: { id: 1 } })));
        const examObservable = resolve.resolve({
            params: {
                courseId: 1,
                examId: 2,
                exerciseGroupId: 3,
            },
        } as any as ActivatedRouteSnapshot);

        let receivedExamGroup = undefined;
        examObservable.subscribe((exam) => (receivedExamGroup = exam));
        tick();

        expect(findSpy).toHaveBeenCalledOnce();
        expect(findSpy).toHaveBeenCalledWith(1, 2, 3);
        expect(receivedExamGroup).toEqual({ id: 1 });
    }));

    it('should create a new exam group if courseId, examId and/or examGroupId are not given', fakeAsync(() => {
        const findSpy = jest.spyOn(exerciseGroupService, 'find');
        const examObservable = resolve.resolve({
            params: {},
        } as any as ActivatedRouteSnapshot);

        let receivedExamGroup = undefined;
        examObservable.subscribe((exam) => (receivedExamGroup = exam));
        tick();

        expect(findSpy).not.toHaveBeenCalled();
        expect(receivedExamGroup).toEqual({ isMandatory: true });
    }));
});

describe('Student Exam Resolve', () => {
    let resolve: StudentExamResolve;
    let studentExamService: StudentExamService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [StudentExamResolve, MockProvider(StudentExamService)],
            imports: [ArtemisTestModule],
        });

        resolve = TestBed.inject(StudentExamResolve);
        studentExamService = TestBed.inject(StudentExamService);
    });

    afterEach(() => jest.restoreAllMocks());

    it('should fetch the student exam if courseId, examId and studentExamId are given', fakeAsync(() => {
        const response = { status: 200, body: { maxPoints: 10, studentExam: { id: 1 } } as StudentExamWithGradeDTO };
        const findSpy = jest.spyOn(studentExamService, 'find').mockReturnValue(of(new HttpResponse(response)));
        const examObservable = resolve.resolve({
            params: {
                courseId: 1,
                examId: 2,
                studentExamId: 3,
            },
        } as any as ActivatedRouteSnapshot);

        let receivedExamWithGrade: StudentExamWithGradeDTO | undefined = undefined;
        examObservable.subscribe((examWithGrade) => (receivedExamWithGrade = examWithGrade));
        tick();

        expect(findSpy).toHaveBeenCalledOnce();
        expect(findSpy).toHaveBeenCalledWith(1, 2, 3);
        expect(receivedExamWithGrade!.maxPoints).toBe(10);
        expect(receivedExamWithGrade!.studentExam).toEqual({ id: 1 });
    }));

    it('should create a new student exam if courseId and/or examId and/or studentExamId are not given', fakeAsync(() => {
        const findSpy = jest.spyOn(studentExamService, 'find');
        const examObservable = resolve.resolve({
            params: {},
        } as any as ActivatedRouteSnapshot);

        let receivedExam = undefined;
        examObservable.subscribe((exam) => (receivedExam = exam));
        tick();

        expect(findSpy).not.toHaveBeenCalled();
        expect(receivedExam).toEqual(new StudentExamWithGradeDTO());
    }));
});
