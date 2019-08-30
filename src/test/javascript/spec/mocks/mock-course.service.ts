import { Course } from 'app/entities/course';
import { of } from 'rxjs';

export class MockCourseService {
    find = (courseId: number) => of(new Course());
}
