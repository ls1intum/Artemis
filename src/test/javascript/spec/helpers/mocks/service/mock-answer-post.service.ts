import { Observable, of } from 'rxjs';
import { AnswerPost } from 'app/communication/shared/entities/answer-post.model';
import { HttpResponse } from '@angular/common/http';
import { metisCoursePosts } from '../../sample/metis-sample-data';

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
        const sourceAnswerPosts = metisCoursePosts.flatMap((post) => post.answers || []).filter((answerPost) => answerPostIds.includes(answerPost.id!));

        return of(sourceAnswerPosts);
    }
}
