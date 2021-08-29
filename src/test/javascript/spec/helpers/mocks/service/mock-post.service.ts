import { Observable, of } from 'rxjs';
import { Post } from 'app/entities/metis/post.model';
import { HttpResponse } from '@angular/common/http';
import { DisplayPriority, PostContextFilter } from 'app/shared/metis/metis.util';

export class MockPostService {
    create(courseId: number, post: Post): Observable<HttpResponse<Post>> {
        return of({ body: post }) as Observable<HttpResponse<Post>>;
    }

    update(courseId: number, post: Post): Observable<HttpResponse<Post>> {
        return of({ body: post }) as Observable<HttpResponse<Post>>;
    }

    updatePostDisplayPriority(courseId: number, postId: number, displayPriority: DisplayPriority): Observable<HttpResponse<Post>> {
        return of({ body: { id: postId, displayPriority } as Post }) as Observable<HttpResponse<Post>>;
    }

    delete(post: Post): Observable<HttpResponse<Post>> {
        return of({ body: {} }) as Observable<HttpResponse<Post>>;
    }

    getPosts(courseId: number, postContextFilter: PostContextFilter): Observable<HttpResponse<Post[]>> {
        // Todo: write if else logic and return posts that match all the filter options
        return of({ body: [{ id: 1, course: { id: courseId } }] }) as Observable<HttpResponse<Post[]>>;
    }

    getAllPostTagsByCourseId(courseId: number): Observable<HttpResponse<string[]>> {
        return of({ body: ['mockTag1', 'mockTag2'] }) as Observable<HttpResponse<string[]>>;
    }
}
