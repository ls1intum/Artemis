import { Observable, of } from 'rxjs';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { Post } from 'app/entities/metis/post.model';
import { Posting } from 'app/entities/metis/posting.model';
import { User } from 'app/core/user/user.model';
import { Reaction } from 'app/entities/metis/reaction.model';
import { ContextInformation, PageType, PostContextFilter, RouteComponents } from 'app/shared/metis/metis.util';
import { Course } from 'app/entities/course.model';
import { Params } from '@angular/router';
import { metisCourse, metisCoursePosts, metisTags, metisUser1 } from '../../sample/metis-sample-data';
import { ProfileToggle } from 'app/shared/profile-toggle/profile-toggle.service';

let pageType: PageType;

export class MockProfileToggleServiceService {

    getProfileToggleActive(profile: ProfileToggle): Observable<boolean> {
        return of(true);
    };
}
