import { Observable, of } from 'rxjs';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { HttpResponse } from '@angular/common/http';

export class MockAnswerPostService {
    create(courseId: number, answerPost: AnswerPost): Observable<HttpResponse<AnswerPost>> {
        return of({ body: answerPost }) as Observable<HttpResponse<AnswerPost>>;
    }

    update(courseId: number, answerPost: AnswerPost): Observable<HttpResponse<AnswerPost>> {
        return of({ body: answerPost }) as Observable<HttpResponse<AnswerPost>>;
    }

    delete(answerPost: AnswerPost): Observable<HttpResponse<AnswerPost>> {
        return of({ body: {} }) as Observable<HttpResponse<AnswerPost>>;
    }
}
