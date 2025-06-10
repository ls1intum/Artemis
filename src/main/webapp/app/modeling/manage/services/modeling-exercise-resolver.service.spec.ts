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

describe('ModelingExerciseResolver', () => {
    let component: ModelingExerciseResolver;
    let currentRoute: ActivatedRouteSnapshot;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [MockProvider(CourseManagementService), MockProvider(ModelingExerciseService), provideHttpClient(), provideHttpClientTesting()],
        });

        component = TestBed.inject(ModelingExerciseResolver);

        currentRoute = {} as ActivatedRouteSnapshot;
    });

    it('should be created', () => {
        expect(component).toBeTruthy();
    });

    it('should resolve a ModelingExercise when exerciseId is provided', () => {
        const dummyExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, undefined, undefined);
        const modelingExerciseService = TestBed.inject(ModelingExerciseService);
        jest.spyOn(modelingExerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: dummyExercise })));

        currentRoute.params = { exerciseId: '123' };

        component.resolve(currentRoute).subscribe((result) => {
            expect(result).toEqual(dummyExercise);
        });

        expect(modelingExerciseService.find).toHaveBeenCalledWith('123');
    });
});
