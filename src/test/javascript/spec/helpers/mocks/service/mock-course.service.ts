import { HttpResponse } from '@angular/common/http';
import { Course } from 'app/entities/course.model';
import { of } from 'rxjs';

export class MockCourseService {
    create = (course: Course) => of({} as HttpResponse<Course>);
}
