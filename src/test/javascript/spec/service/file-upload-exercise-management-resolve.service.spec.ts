import { FileUploadExerciseManagementResolve } from 'app/exercises/file-upload/manage/file-upload-exercise-management-resolve.service';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot } from '@angular/router';
import { EntityResponseType, FileUploadExerciseService } from 'app/exercises/file-upload/manage/file-upload-exercise.service';
import { fileUploadExercise, MockFileUploadExerciseService } from '../helpers/mocks/service/mock-file-upload-exercise.service';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { MockCourseManagementService } from '../helpers/mocks/service/mock-course-management.service';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';
import { MockProvider } from 'ng-mocks';
import { Observable, of } from 'rxjs';

describe('FileUploadExerciseManagementResolve', () => {
    let service: FileUploadExerciseManagementResolve;
    let fileUploadService: FileUploadExerciseService;
    let courseManagementService: CourseManagementService;
    let exerciseGroupService: ExerciseGroupService;

    let exerciseFindStub: jest.SpyInstance;
    let courseFindStub: jest.SpyInstance;
    let exerciseGroupFindStub: jest.SpyInstance;

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

                exerciseFindStub = jest.spyOn(fileUploadService, 'find');
                courseFindStub = jest.spyOn(courseManagementService, 'find');
                exerciseGroupFindStub = jest.spyOn(exerciseGroupService, 'find').mockReturnValue(of({ body: fileUploadExercise }) as Observable<EntityResponseType>);
            });
    });

    it('should process empty params correctly', () => {
        const snapshot = { params: {} } as unknown as ActivatedRouteSnapshot;

        service.resolve(snapshot);

        verifyCalls(false, false, false);
    });

    it('should process exercise ID correctly', () => {
        const snapshot = {
            params: { exerciseId: 42 },
        } as unknown as ActivatedRouteSnapshot;

        service.resolve(snapshot);

        expect(exerciseFindStub).toHaveBeenCalledWith(42);
        verifyCalls(true, false, false);
    });

    it('should process course ID without exam ID and exercise Group ID correctly', () => {
        const snapshot = {
            params: { courseId: 3 },
        } as unknown as ActivatedRouteSnapshot;

        service.resolve(snapshot);

        expect(courseFindStub).toHaveBeenCalledWith(3);
        verifyCalls(false, true, false);
    });

    it('should process course ID with exam ID and without exercise Group ID correctly', () => {
        const snapshot = {
            params: { courseId: 3, examId: 4 },
        } as unknown as ActivatedRouteSnapshot;

        service.resolve(snapshot);

        expect(courseFindStub).toHaveBeenCalledWith(3);
        verifyCalls(false, true, false);
    });

    it('should process course ID with exam ID and exercise Group ID correctly', () => {
        const snapshot = {
            params: { courseId: 3, examId: 4, exerciseGroupId: 5 },
        } as unknown as ActivatedRouteSnapshot;

        service.resolve(snapshot);

        expect(exerciseGroupFindStub).toHaveBeenCalledWith(3, 4, 5);
        verifyCalls(false, false, true);
    });

    const verifyCalls = (exerciseServiceCalled: boolean, courseServiceCalled: boolean, exerciseGroupServiceCalled: boolean) => {
        expect(exerciseFindStub).toHaveBeenCalledTimes(exerciseServiceCalled ? 1 : 0);
        expect(courseFindStub).toHaveBeenCalledTimes(courseServiceCalled ? 1 : 0);
        expect(exerciseGroupFindStub).toHaveBeenCalledTimes(exerciseGroupServiceCalled ? 1 : 0);
    };
});
