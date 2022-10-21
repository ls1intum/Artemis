import { HttpResponse } from '@angular/common/http';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { Observable, of } from 'rxjs';

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
