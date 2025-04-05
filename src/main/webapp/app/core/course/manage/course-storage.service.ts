import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Course } from 'app/core/shared/entities/course.model';
import { SubjectObservablePair } from 'app/shared/util/rxjs.utils';

/**
 * This service is used to store {@link Course} objects for the currently logged-in user.
 * The methods {@link CourseManagementService#findAllForDashboard} and {@link CourseManagementService#findOneForDashboard} retrieve one or multiple {@link Course} objects and save them in this service.
 * This way, multiple components that need a course can access it without having to retrieve it again from the server.
 * Some components update the course object and can use the {@link updateCourse} method to make the changes available to the entire application.
 * Components that need to be notified about these changes can use the {@link subscribeToCourseUpdates} method.
 */
@Injectable({ providedIn: 'root' })
export class CourseStorageService {
    private storedCourses: Course[] = [];

    private readonly courseUpdateSubscriptions: Map<number, SubjectObservablePair<Course>> = new Map();

    setCourses(courses?: Course[]) {
        this.storedCourses = courses ?? [];
    }

    getCourse(courseId: number) {
        return this.storedCourses.find((course) => course.id === courseId);
    }

    updateCourse(course?: Course): void {
        if (course) {
            // filter out the old course object with the same id
            this.storedCourses = this.storedCourses.filter((existingCourse) => existingCourse.id !== course.id);
            this.storedCourses.push(course);
            return this.courseUpdateSubscriptions.get(course.id!)?.subject.next(course);
        }
    }

    subscribeToCourseUpdates(courseId: number): Observable<Course> {
        if (!this.courseUpdateSubscriptions.has(courseId)) {
            this.courseUpdateSubscriptions.set(courseId, new SubjectObservablePair());
        }
        return this.courseUpdateSubscriptions.get(courseId)!.observable;
    }
}
