import { Observable, of } from 'rxjs';
import { Post } from 'app/entities/metis/post.model';
import { HttpResponse } from '@angular/common/http';

export class MockPostService {
    create = (courseId: number, post: Post): Observable<Post> => of(post);
    update = (courseId: number, post: Post): Observable<Post> => of(post);
    updateVotes = (courseId: number, post: Post, voteChange: number): Observable<Post> => of(post);

    delete(post: Post): Observable<HttpResponse<any>> {
        return of();
    }
}
