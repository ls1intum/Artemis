import { Observable, of } from 'rxjs';
import { Post } from 'app/entities/metis/post.model';
import { HttpHeaders, HttpResponse } from '@angular/common/http';
import { CourseWideContext, DisplayPriority, PostContextFilter } from 'app/shared/metis/metis.util';
import {
    messagesBetweenUser1User2,
    metisCoursePosts,
    metisExercisePosts,
    metisLecturePosts,
    metisPostExerciseUser1,
    metisPostOrganization,
    metisPostRandom,
    metisPostTechSupport,
    metisTags,
} from '../../sample/metis-sample-data';

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
        if (postContextFilter.courseWideContext === CourseWideContext.TECH_SUPPORT) {
            return of({
                body: [metisPostTechSupport],
                headers: new HttpHeaders({
                    'X-Total-Count': '1',
                }),
            }) as Observable<HttpResponse<Post[]>>;
        }
        if (postContextFilter.courseWideContext === CourseWideContext.RANDOM) {
            return of({
                body: [metisPostRandom],
                headers: new HttpHeaders({
                    'X-Total-Count': '1',
                }),
            }) as Observable<HttpResponse<Post[]>>;
        }
        if (postContextFilter.courseWideContext === CourseWideContext.ORGANIZATION) {
            return of({
                body: [metisPostOrganization],
                headers: new HttpHeaders({
                    'X-Total-Count': '1',
                }),
            }) as Observable<HttpResponse<Post[]>>;
        }
        if (postContextFilter.exerciseId) {
            return of({
                body: metisExercisePosts,
                headers: new HttpHeaders({
                    'X-Total-Count': metisExercisePosts.length.toString(),
                }),
            }) as Observable<HttpResponse<Post[]>>;
        }
        if (postContextFilter.lectureId) {
            return of({
                body: metisLecturePosts,
                headers: new HttpHeaders({
                    'X-Total-Count': metisLecturePosts.length.toString(),
                }),
            }) as Observable<HttpResponse<Post[]>>;
        }
        if (postContextFilter.conversationId) {
            return of({
                body: messagesBetweenUser1User2,
                headers: new HttpHeaders({
                    'X-Total-Count': messagesBetweenUser1User2.length.toString(),
                }),
            }) as Observable<HttpResponse<Post[]>>;
        } else {
            return of({
                body: !postContextFilter.pageSize ? metisCoursePosts : metisCoursePosts.slice(0, postContextFilter.pageSize),
                headers: new HttpHeaders({
                    'X-Total-Count': metisCoursePosts.length.toString(),
                }),
            }) as Observable<HttpResponse<Post[]>>;
        }
    }

    getAllPostTagsByCourseId(courseId: number): Observable<HttpResponse<string[]>> {
        return of({ body: metisTags }) as Observable<HttpResponse<string[]>>;
    }

    computeSimilarityScoresWithCoursePosts(post: Post, courseId: number): Observable<HttpResponse<Post[]>> {
        return of({ body: [metisPostExerciseUser1] }) as Observable<HttpResponse<Post[]>>;
    }
}
