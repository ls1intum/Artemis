import { HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';

import { Course } from 'app/entities/course.model';

export class MockCourseService {
    create = (course: Course) => of({} as HttpResponse<Course>);
}
