import { TestBed } from '@angular/core/testing';
import { ModelingExerciseResolver } from 'app/modeling/manage/services/modeling-exercise-resolver.service';
import { ModelingExerciseService } from 'app/modeling/manage/services/modeling-exercise.service';
import { MockProvider } from 'ng-mocks';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';

describe('ModelingExerciseResolver', () => {
    let component: ModelingExerciseResolver;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [MockProvider(CourseManagementService), MockProvider(ModelingExerciseService), provideHttpClient(), provideHttpClientTesting()],
        });

        component = TestBed.inject(ModelingExerciseResolver);
    });

    it('should be created', () => {
        expect(component).toBeTruthy();
    });
});
