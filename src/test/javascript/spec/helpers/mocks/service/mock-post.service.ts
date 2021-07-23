import { Observable, of } from 'rxjs';
import { Post } from 'app/entities/metis/post.model';
import { HttpResponse } from '@angular/common/http';

export class MockPostService {
    create(courseId: number, post: Post): Observable<HttpResponse<Post>> {
        return of({ body: post }) as Observable<HttpResponse<Post>>;
    }

    update(courseId: number, post: Post): Observable<HttpResponse<Post>> {
        return of({ body: post }) as Observable<HttpResponse<Post>>;
    }

    updateVotes(courseId: number, postId: number, voteChange: number): Observable<HttpResponse<Post>> {
        return of({ body: {} }) as Observable<HttpResponse<Post>>;
    }

    delete(post: Post): Observable<HttpResponse<Post>> {
        return of({ body: {} }) as Observable<HttpResponse<Post>>;
    }

    getAllPostTagsByCourseId(courseId: number): Observable<HttpResponse<string[]>> {
        return of({ body: ['mockTag1', 'mockTag2'] }) as Observable<HttpResponse<string[]>>;
    }

    getAllPostsByCourseId(courseId: number): Observable<HttpResponse<Post[]>> {
        return of({ body: {} }) as Observable<HttpResponse<Post[]>>;
    }

    getAllPostsByLectureId(courseId: number, lectureId: number): Observable<HttpResponse<Post[]>> {
        return of({ body: {} }) as Observable<HttpResponse<Post[]>>;
    }

    getAllPostsByExerciseId(courseId: number, exerciserId: number): Observable<HttpResponse<Post[]>> {
        return of({ body: {} }) as Observable<HttpResponse<Post[]>>;
    }
}
