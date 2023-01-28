import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Course } from 'app/entities/course.model';
import { SubjectObservablePair } from 'app/utils/rxjs.utils';

@Injectable({ providedIn: 'root' })
export class CourseStorageService {
    private storedCourses: Course[] = [];

    private readonly courseUpdates: Map<number, SubjectObservablePair<Course>> = new Map();

    setCourses(courses: Course[]) {
        this.storedCourses = courses;
    }

    getCourse(courseId: number) {
        return this.storedCourses.find((course) => course.id === courseId);
    }

    updateCourse(course: Course) {
        // filter out the old course object with the same id
        this.storedCourses = this.storedCourses.filter((existingCourse) => existingCourse.id !== course.id);
        this.storedCourses.push(course);
    }

    notifyCourseUpdatesSubscribers(course: Course | null): void {
        if (course) {
            return this.courseUpdates.get(course.id!)?.subject.next(course);
        }
    }

    subscribeToCourseUpdates(courseId: number): Observable<Course> {
        if (!this.courseUpdates.has(courseId)) {
            this.courseUpdates.set(courseId, new SubjectObservablePair());
        }
        return this.courseUpdates.get(courseId)!.observable;
    }
}
