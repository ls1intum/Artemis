import { Observable, of } from 'rxjs';
import { AnswerPost } from 'app/communication/shared/entities/answer-post.model';
import { HttpResponse } from '@angular/common/http';
import { communicationCoursePosts } from '../../sample/communication-sample-data';

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

    getSourceAnswerPostsByIds(courseId: number, answerPostIds: number[]): Observable<AnswerPost[]> {
        const sourceAnswerPosts = communicationCoursePosts.flatMap((post) => post.answers || []).filter((answerPost) => answerPostIds.includes(answerPost.id!));

        return of(sourceAnswerPosts);
    }
}
