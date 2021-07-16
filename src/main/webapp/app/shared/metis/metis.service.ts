import { CourseWideContext, Post } from 'app/entities/metis/post.model';
import { PostService } from 'app/shared/metis/post/post.service';
import { Exercise } from 'app/entities/exercise.model';
import { Lecture } from 'app/entities/lecture.model';
import { BehaviorSubject, Observable } from 'rxjs';

interface PostFilter {
    exercise?: Exercise;
    lecture?: Lecture;
    courseWideContext?: CourseWideContext;
}

export class MetisService {
    private posts$: BehaviorSubject<Post[]> = new BehaviorSubject<Post[]>([]);
    private filteredPosts$: BehaviorSubject<Post[]> = new BehaviorSubject<Post[]>([]);
    private tags$: BehaviorSubject<string[]> = new BehaviorSubject<string[]>([]);

    get filteredPosts(): Observable<Post[]> {
        return this.filteredPosts$.asObservable();
    }

    get tags(): Observable<string[]> {
        return this.tags$;
    }

    /**
     * Use this to set posts from outside.
     * @param posts
     */
    setPosts(posts: Post[]): void {
        this.posts$.next(posts);
    }

    filterPosts(postFilter: PostFilter): void {
        const filteredPosts = this.posts$.getValue().filter((post) => {
            if (postFilter.lecture?.id && postFilter.lecture.id !== post.lecture!.id) {
                return false;
            }
            if (postFilter.exercise?.id && postFilter.exercise.id !== post.exercise!.id) {
                return false;
            }
            if (postFilter.courseWideContext && postFilter.courseWideContext !== post.courseWideContext) {
                return false;
            }
            return true;
        });
        this.filteredPosts$.next(filteredPosts);
    }

    constructor(private postService: PostService) {}
}
