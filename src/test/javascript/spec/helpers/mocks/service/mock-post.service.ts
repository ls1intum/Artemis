import { Observable, of } from 'rxjs';
import { Post } from 'app/communication/shared/entities/post.model';
import { HttpHeaders, HttpResponse } from '@angular/common/http';
import { DisplayPriority, PostContextFilter } from 'app/communication/communication.util';
import { messagesBetweenUser1User2, communicationCoursePosts, communicationPostExerciseUser1, communicationTags } from '../../sample/communication-sample-data';

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
        if (postContextFilter.conversationIds) {
            // Return a copy to avoid tests mutating shared data
            return of({
                body: [...messagesBetweenUser1User2],
                headers: new HttpHeaders({
                    'X-Total-Count': messagesBetweenUser1User2.length.toString(),
                }),
            }) as Observable<HttpResponse<Post[]>>;
        } else {
            return of({
                body: !postContextFilter.pageSize ? [...communicationCoursePosts] : communicationCoursePosts.slice(0, postContextFilter.pageSize),
                headers: new HttpHeaders({
                    'X-Total-Count': communicationCoursePosts.length.toString(),
                }),
            }) as Observable<HttpResponse<Post[]>>;
        }
    }

    getAllPostTagsByCourseId(courseId: number): Observable<HttpResponse<string[]>> {
        return of({ body: communicationTags }) as Observable<HttpResponse<string[]>>;
    }

    computeSimilarityScoresWithCoursePosts(post: Post, courseId: number): Observable<HttpResponse<Post[]>> {
        return of({ body: [communicationPostExerciseUser1] }) as Observable<HttpResponse<Post[]>>;
    }

    getSourcePostsByIds(courseId: number, postIds: number[]): Observable<Post[]> {
        const sourcePosts = communicationCoursePosts.filter((post) => postIds.includes(post.id!));
        return of(sourcePosts);
    }
}
