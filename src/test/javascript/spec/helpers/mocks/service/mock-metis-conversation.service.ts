import { Course } from 'app/entities/course.model';
import { EMPTY, Observable } from 'rxjs';

export class MockMetisConversationService {
    get course(): Course | undefined {
        return undefined;
    }

    public setUpConversationService = (course: Course): Observable<never> => {
        return EMPTY;
    };
}
