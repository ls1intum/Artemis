import { HttpResponse } from '@angular/common/http';
import { User } from 'app/core/user/user.model';
import { BehaviorSubject, Observable, of } from 'rxjs';
import { Course, CourseGroup } from 'app/entities/course.model';
import { TextExercise } from 'app/entities/text/text-exercise.model';
import { EntityArrayResponseType } from 'app/course/manage/course-management.service';

export class MockCourseManagementService {
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
    getCoursesForNotifications = () => {
        return this.coursesForNotificationsMock.asObservable();
    };

    findAllCategoriesOfCourse = () => {
        return of();
    };

    getAllUsersInCourseGroup(courseId: number, courseGroup: CourseGroup): Observable<HttpResponse<User[]>> {
        return of(new HttpResponse({ body: [] }));
    }

    getAll(): Observable<EntityArrayResponseType> {
        return of(new HttpResponse({ body: [] }));
    }

    getNumberOfAllowedComplaintsInCourse(courseId: number): Observable<number> {
        return of(3);
    }
}
