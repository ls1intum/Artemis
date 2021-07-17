import { HttpResponse } from '@angular/common/http';
import { BehaviorSubject, of } from 'rxjs';
import { Course } from 'app/entities/course.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { Exercise } from 'app/entities/exercise.model';

export class MockCourseManagementService {
    mockExercises: Exercise[] = [new TextExercise(undefined, undefined)];

    find = (courseId: number) => of([{ id: 456 } as Course]);

    findWithExercises = (courseId: number) => {
        const mockExercise = new TextExercise(undefined, undefined);
        mockExercise.id = 1;
        mockExercise.teamMode = true;

        const mockHttpBody = {
            exercises: [mockExercise],
        };

        const mockHttpResponse = new HttpResponse({ body: mockHttpBody });

        return of(mockHttpResponse);
    };

    coursesForNotificationsMock: BehaviorSubject<Course[] | undefined> = new BehaviorSubject<Course[] | undefined>(undefined);
    //this method is used in notification.service.spec
    getCoursesForNotifications = () => {
        return this.coursesForNotificationsMock.asObservable();
    };
}
