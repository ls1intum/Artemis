import { HttpResponse } from '@angular/common/http';
import { Reaction } from 'app/entities/metis/reaction.model';
import { Observable, of } from 'rxjs';

export class MockReactionService {
    create(courseId: number, reaction: Reaction): Observable<HttpResponse<Reaction>> {
        return of({ body: reaction }) as Observable<HttpResponse<Reaction>>;
    }

    delete(courseId: number, reaction: Reaction): Observable<HttpResponse<Reaction>> {
        return of({ body: {} }) as Observable<HttpResponse<Reaction>>;
    }
}
