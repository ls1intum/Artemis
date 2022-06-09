import { Directive } from '@angular/core';
import { FormGroup } from '@angular/forms';
import { faFilter, faLongArrowAltDown, faLongArrowAltUp, faPlus, faSearch, faTimes } from '@fortawesome/free-solid-svg-icons';
import { PostContextFilter, PostSortCriterion, SortDirection } from 'app/shared/metis/metis.util';
import { ButtonType } from '../components/button.component';
import { Post } from 'app/entities/metis/post.model';
import { Course } from 'app/entities/course.model';
import { Subscription } from 'rxjs';
import { MetisService } from 'app/shared/metis/metis.service';

@Directive({
    providers: [MetisService],
})
export abstract class CourseDiscussionDirective {
    searchText?: string;
    currentPostContextFilter: PostContextFilter;
    formGroup: FormGroup;
    readonly ButtonType = ButtonType;
    course?: Course;
    createdPost: Post;
    posts: Post[];
    isLoading = true;

    currentSortCriterion = PostSortCriterion.CREATION_DATE;
    currentSortDirection?: SortDirection;
    readonly SortBy = PostSortCriterion;
    readonly SortDirection = SortDirection;

    protected postsSubscription: Subscription;
    protected paramSubscription: Subscription;

    // Icons
    faPlus = faPlus;
    faTimes = faTimes;
    faFilter = faFilter;
    faSearch = faSearch;
    faLongArrowAltUp = faLongArrowAltUp;
    faLongArrowAltDown = faLongArrowAltDown;

    protected constructor(protected metisService: MetisService) {}

    /**
     * on changing any filter, the metis service is invoked to deliver all posts for the
     * currently set context, filtered on the server
     */
    onSelectContext(): void {
        this.setFilterAndSort();
        this.metisService.getFilteredPosts(this.currentPostContextFilter);
    }

    /**
     * on leaving the page, should unsubscribe from subscriptions
     */
    onDestroy(): void {
        this.paramSubscription?.unsubscribe();
        this.postsSubscription?.unsubscribe();
    }

    abstract setFilterAndSort(): void;

    abstract resetFormGroup(): void;
}
