import { Injectable, signal } from '@angular/core';
import { Course } from 'app/course/shared/entities/course.model';

@Injectable({ providedIn: 'root' })
export class CurrentCourseContextService {
    readonly course = signal<Course | undefined>(undefined);

    setCourse(course: Course | undefined): void {
        this.course.set(course);
    }

    clearCourse(): void {
        this.course.set(undefined);
    }
}
