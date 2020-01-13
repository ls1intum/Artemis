import { Course } from 'app/entities/course';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';

export class MockCourseService {
    create = (course: Course) => of({} as HttpResponse<Course>);
}
