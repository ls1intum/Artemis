import { Observable, of } from 'rxjs';
import { Post } from 'app/entities/metis/post.model';
import { HttpResponse } from '@angular/common/http';
import { CourseWideContext, DisplayPriority, PostContextFilter } from 'app/shared/metis/metis.util';
import {
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
            return of({ body: [metisPostTechSupport] }) as Observable<HttpResponse<Post[]>>;
        }
        if (postContextFilter.courseWideContext === CourseWideContext.RANDOM) {
            return of({ body: [metisPostRandom] }) as Observable<HttpResponse<Post[]>>;
        }
        if (postContextFilter.courseWideContext === CourseWideContext.ORGANIZATION) {
            return of({ body: [metisPostOrganization] }) as Observable<HttpResponse<Post[]>>;
        }
        if (postContextFilter.exerciseId) {
            return of({ body: metisExercisePosts }) as Observable<HttpResponse<Post[]>>;
        }
        if (postContextFilter.lectureId) {
            return of({ body: metisLecturePosts }) as Observable<HttpResponse<Post[]>>;
        } else {
            return of({ body: metisCoursePosts }) as Observable<HttpResponse<Post[]>>;
        }
    }

    getAllPostTagsByCourseId(courseId: number): Observable<HttpResponse<string[]>> {
        return of({ body: metisTags }) as Observable<HttpResponse<string[]>>;
    }

    computeSimilarityScoresWithCoursePosts(post: Post, courseId: number): Observable<HttpResponse<Post[]>> {
        return of({ body: [metisPostExerciseUser1] }) as Observable<HttpResponse<Post[]>>;
    }
}
