import { Observable } from 'rxjs';
import { Course } from 'app/entities/course.model';

export class MockCourseManagementService {
    find = (courseId: number) => Observable.of([{ id: 456 } as Course]);
}
