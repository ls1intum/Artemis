import { TestBed } from '@angular/core/testing';
import { TextExerciseResolver } from 'app/text/manage/text-exercise/service/text-exercise-resolver.service';
import { TextExerciseService } from 'app/text/manage/text-exercise/service/text-exercise.service';
import { MockProvider } from 'ng-mocks';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { ActivatedRouteSnapshot } from '@angular/router';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { of } from 'rxjs';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';
import { Course } from 'app/core/course/shared/entities/course.model';

describe('TextExerciseResolver', () => {
    let resolver: TextExerciseResolver;
    let route: ActivatedRouteSnapshot;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                MockProvider(CourseManagementService),
                MockProvider(TextExerciseService),
                MockProvider(ExerciseGroupService),
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        });

        resolver = TestBed.inject(TextExerciseResolver);
        route = {} as ActivatedRouteSnapshot;
    });

    it('should resolve a TextExercise when exerciseId is provided', () => {
        const dummyExercise = new TextExercise(undefined, undefined);
        const textExerciseService = TestBed.inject(TextExerciseService);
        jest.spyOn(textExerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: dummyExercise })));

        route.params = { exerciseId: '123' };

        resolver.resolve(route).subscribe((result) => {
            expect(result).toEqual(dummyExercise);
        });

        expect(textExerciseService.find).toHaveBeenCalledWith('123', true, true);
    });

    it('should resolve a TextExercise for a course and exercise group', () => {
        const dummyExerciseGroup = { id: 3 } as ExerciseGroup;
        const exerciseGroupService = TestBed.inject(ExerciseGroupService);
        jest.spyOn(exerciseGroupService, 'find').mockReturnValue(of(new HttpResponse({ body: dummyExerciseGroup })));

        route.params = { courseId: '1', examId: '2', exerciseGroupId: '3' };

        resolver.resolve(route).subscribe((result) => {
            expect(result).toEqual(
                expect.objectContaining({
                    exerciseGroup: dummyExerciseGroup,
                }),
            );
        });

        expect(exerciseGroupService.find).toHaveBeenCalledWith('1', '2', '3');
    });

    it('should resolve a TextExercise for a course without an exercise group', () => {
        const dummyCourse = { id: 1 } as Course;
        const courseService = TestBed.inject(CourseManagementService);
        jest.spyOn(courseService, 'find').mockReturnValue(of(new HttpResponse({ body: dummyCourse })));

        route.params = { courseId: '1' };

        resolver.resolve(route).subscribe((result) => {
            expect(result).toEqual(
                expect.objectContaining({
                    course: dummyCourse,
                }),
            );
        });

        expect(courseService.find).toHaveBeenCalledWith('1');
    });
});
