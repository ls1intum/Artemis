import { HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Course } from 'app/entities/course.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { Exercise } from 'app/entities/exercise.model';

export class MockCourseManagementService {
    mockExercises: Exercise[] = [new TextExercise(undefined, undefined)];

    find = (courseId: number) => Observable.of([{ id: 456 } as Course]);

    findWithExercises = (courseId: number) => {
        const mockExercise = new TextExercise(undefined, undefined);
        mockExercise.id = 1;
        mockExercise.teamMode = true;

        const mockHttpBody = {
            exercises: [mockExercise],
        };

        const mockHttpResponse = new HttpResponse({ body: mockHttpBody });

        return Observable.of(mockHttpResponse);
    };
}
