import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Course } from 'app/entities/course.model';
import { SubjectObservablePair } from 'app/utils/rxjs.utils';

@Injectable({ providedIn: 'root' })
export class CourseStorageService {
    private storedCourses: Course[] = [];

    private readonly courseUpdateSubscriptions: Map<number, SubjectObservablePair<Course>> = new Map();

    setCourses(courses: Course[] | null) {
        this.storedCourses = courses ?? [];
    }

    getCourse(courseId: number) {
        return this.storedCourses.find((course) => course.id === courseId);
    }

    updateCourse(course: Course | null): void {
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
