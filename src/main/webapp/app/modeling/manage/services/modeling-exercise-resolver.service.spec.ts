import { TestBed } from '@angular/core/testing';
import { ModelingExerciseResolver } from 'app/modeling/manage/services/modeling-exercise-resolver.service';
import { ModelingExerciseService } from 'app/modeling/manage/services/modeling-exercise.service';
import { MockProvider } from 'ng-mocks';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { ActivatedRouteSnapshot } from '@angular/router';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { UMLDiagramType } from '@ls1intum/apollon';
import { of } from 'rxjs';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';
import { Course } from 'app/core/course/shared/entities/course.model';

describe('ModelingExerciseResolver', () => {
    let component: ModelingExerciseResolver;
    let currentRoute: ActivatedRouteSnapshot;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                MockProvider(CourseManagementService),
                MockProvider(ModelingExerciseService),
                MockProvider(ExerciseGroupService),
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        });

        component = TestBed.inject(ModelingExerciseResolver);

        currentRoute = {} as ActivatedRouteSnapshot;
    });

    it('should resolve a ModelingExercise when exerciseId is provided', () => {
        const dummyExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, undefined, undefined);
        const modelingExerciseService = TestBed.inject(ModelingExerciseService);
        jest.spyOn(modelingExerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: dummyExercise })));

        currentRoute.params = { exerciseId: '123' };

        component.resolve(currentRoute).subscribe((result) => {
            expect(result).toEqual(dummyExercise);
        });

        expect(modelingExerciseService.find).toHaveBeenCalledWith('123', true);
    });

    it('should resolve a ModelingExercise for a course and exercise group', () => {
        const dummyExerciseGroup = { id: 3 } as ExerciseGroup;
        const exerciseGroupService = TestBed.inject(ExerciseGroupService);
        jest.spyOn(exerciseGroupService, 'find').mockReturnValue(of(new HttpResponse({ body: dummyExerciseGroup })));

        currentRoute.params = { courseId: '1', examId: '2', exerciseGroupId: '3' };

        component.resolve(currentRoute).subscribe((result) => {
            expect(result).toEqual(
                expect.objectContaining({
                    diagramType: UMLDiagramType.ClassDiagram,
                    exerciseGroup: dummyExerciseGroup,
                }),
            );
        });

        expect(exerciseGroupService.find).toHaveBeenCalledWith('1', '2', '3');
    });

    it('should resolve a ModelingExercise for a course without an exercise group', () => {
        const dummyCourse = { id: 1 } as Course;
        const courseService = TestBed.inject(CourseManagementService);
        jest.spyOn(courseService, 'find').mockReturnValue(of(new HttpResponse({ body: dummyCourse })));

        currentRoute.params = { courseId: '1' };

        component.resolve(currentRoute).subscribe((result) => {
            expect(result).toEqual(
                expect.objectContaining({
                    diagramType: UMLDiagramType.ClassDiagram,
                    course: dummyCourse,
                }),
            );
        });

        expect(courseService.find).toHaveBeenCalledWith('1');
    });
});
