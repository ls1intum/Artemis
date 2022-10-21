import { FileUploadExerciseManagementResolve } from 'app/exercises/file-upload/manage/file-upload-exercise-management-resolve.service';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot } from '@angular/router';
import { EntityResponseType, FileUploadExerciseService } from 'app/exercises/file-upload/manage/file-upload-exercise.service';
import { fileUploadExercise, MockFileUploadExerciseService } from '../helpers/mocks/service/mock-file-upload-exercise.service';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { MockCourseManagementService } from '../helpers/mocks/service/mock-course-management.service';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';
import { MockProvider } from 'ng-mocks';
import { Observable, of, take } from 'rxjs';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';
import { Course } from 'app/entities/course.model';
import { HttpResponse } from '@angular/common/http';

describe('FileUploadExerciseManagementResolve', () => {
    let service: FileUploadExerciseManagementResolve;
    let fileUploadService: FileUploadExerciseService;
    let courseManagementService: CourseManagementService;
    let exerciseGroupService: ExerciseGroupService;

    let exerciseFindSpy: jest.SpyInstance;
    let courseFindSpy: jest.SpyInstance;
    let exerciseGroupFindStub: jest.SpyInstance;

    let result: Observable<FileUploadExercise>;

    const expectedExercisePerCourseIDOrExerciseGroupID = new FileUploadExercise({ id: 456 } as Course, undefined);
    expectedExercisePerCourseIDOrExerciseGroupID.filePattern = 'pdf, png';

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                { provide: FileUploadExerciseService, useClass: MockFileUploadExerciseService },
                { provide: CourseManagementService, useClass: MockCourseManagementService },
                MockProvider(ExerciseGroupService),
            ],
        })
            .compileComponents()
            .then(() => {
                service = TestBed.inject(FileUploadExerciseManagementResolve);
                fileUploadService = TestBed.inject(FileUploadExerciseService);
                courseManagementService = TestBed.inject(CourseManagementService);
                exerciseGroupService = TestBed.inject(ExerciseGroupService);

                exerciseFindSpy = jest.spyOn(fileUploadService, 'find');
                courseFindSpy = jest.spyOn(courseManagementService, 'find');
                exerciseGroupFindStub = jest.spyOn(exerciseGroupService, 'find').mockReturnValue(of({ body: fileUploadExercise }) as Observable<EntityResponseType>);
            });
    });

    it('should process empty params correctly', () => {
        const snapshot = { params: {} } as unknown as ActivatedRouteSnapshot;

        result = service.resolve(snapshot);

        verifyResult(new FileUploadExercise(undefined, undefined));
        verifyCalls(false, false, false);
    });

    it('should process exercise ID correctly', () => {
        const snapshot = {
            params: { exerciseId: 2 },
        } as unknown as ActivatedRouteSnapshot;

        result = service.resolve(snapshot);

        expect(exerciseFindSpy).toHaveBeenCalledWith(2);
        verifyResult(fileUploadExercise);
        verifyCalls(true, false, false);
    });

    it('should process course ID without exam ID and exercise Group ID correctly', () => {
        const snapshot = {
            params: { courseId: 456 },
        } as unknown as ActivatedRouteSnapshot;
        const course = { id: 456 } as Course;
        jest.spyOn(courseManagementService, 'find').mockReturnValue(of(new HttpResponse({ body: course })));

        result = service.resolve(snapshot);

        expect(courseFindSpy).toHaveBeenCalledWith(456);
        verifyResult(expectedExercisePerCourseIDOrExerciseGroupID);
        verifyCalls(false, true, false);
    });

    it('should process course ID with exam ID and without exercise Group ID correctly', () => {
        const snapshot = {
            params: { courseId: 456, examId: 4 },
        } as unknown as ActivatedRouteSnapshot;

        result = service.resolve(snapshot);

        expect(courseFindSpy).toHaveBeenCalledWith(456);
        verifyResult(expectedExercisePerCourseIDOrExerciseGroupID);
        verifyCalls(false, true, false);
    });

    it('should process course ID with exam ID and exercise Group ID correctly', () => {
        const snapshot = {
            params: { courseId: 456, examId: 4, exerciseGroupId: 5 },
        } as unknown as ActivatedRouteSnapshot;

        result = service.resolve(snapshot);

        expect(exerciseGroupFindStub).toHaveBeenCalledWith(456, 4, 5);
        verifyResult(fileUploadExercise);
        verifyCalls(false, false, true);
    });

    const verifyCalls = (exerciseServiceCalled: boolean, courseServiceCalled: boolean, exerciseGroupServiceCalled: boolean) => {
        expect(exerciseFindSpy).toHaveBeenCalledTimes(exerciseServiceCalled ? 1 : 0);
        expect(courseFindSpy).toHaveBeenCalledTimes(courseServiceCalled ? 1 : 0);
        expect(exerciseGroupFindStub).toHaveBeenCalledTimes(exerciseGroupServiceCalled ? 1 : 0);
    };

    const verifyResult = (expectedResult: FileUploadExercise) => {
        result.pipe(take(1)).subscribe((resultContent) => expect(resultContent).toEqual(expectedResult));
    };
});
