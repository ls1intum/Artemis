import { Observable, of } from 'rxjs';
import { DisplayPriority, Post } from 'app/entities/metis/post.model';
import { HttpResponse } from '@angular/common/http';

export class MockPostService {
    create(courseId: number, post: Post): Observable<HttpResponse<Post>> {
        return of({ body: post }) as Observable<HttpResponse<Post>>;
    }

    update(courseId: number, post: Post): Observable<HttpResponse<Post>> {
        return of({ body: post }) as Observable<HttpResponse<Post>>;
    }

    updatePostDisplayPriority(courseId: number, postId: number, post: Post): Observable<HttpResponse<Post>> {
        return of({ body: { id: postId, displayPriority: post.displayPriority } as Post }) as Observable<HttpResponse<Post>>;
    }

    delete(post: Post): Observable<HttpResponse<Post>> {
        return of({ body: {} }) as Observable<HttpResponse<Post>>;
    }

    getAllPostTagsByCourseId(courseId: number): Observable<HttpResponse<string[]>> {
        return of({ body: ['mockTag1', 'mockTag2'] }) as Observable<HttpResponse<string[]>>;
    }

    getAllPostsByCourseId(courseId: number): Observable<HttpResponse<Post[]>> {
        return of({ body: [{ id: 1, course: { id: courseId } }] }) as Observable<HttpResponse<Post[]>>;
    }

    getAllPostsByLectureId(courseId: number, lectureId: number): Observable<HttpResponse<Post[]>> {
        return of({ body: [{ id: 1, course: { id: courseId }, lecture: { id: lectureId } }] }) as Observable<HttpResponse<Post[]>>;
    }

    getAllPostsByExerciseId(courseId: number, exerciserId: number): Observable<HttpResponse<Post[]>> {
        return of({ body: [{ id: 1, course: { id: courseId }, exercise: { id: exerciserId } }] }) as Observable<HttpResponse<Post[]>>;
    }
}
