import { Directive, inject, signal } from '@angular/core';
import { FormGroup } from '@angular/forms';
import { faFilter, faLongArrowAltDown, faLongArrowAltUp, faPlus, faSearch, faTimes } from '@fortawesome/free-solid-svg-icons';
import { PostContextFilter, PostSortCriterion, SortDirection } from 'app/communication/communication.util';
import { ButtonType } from 'app/shared-ui/components/buttons/button/button.component';
import { Post } from 'app/communication/shared/entities/post.model';
import { Course } from 'app/course/shared/entities/course.model';
import { Subscription } from 'rxjs';
import { CommunicationService } from 'app/communication/service/communication.service';

@Directive({
    providers: [CommunicationService],
})
export abstract class CourseDiscussionDirective {
    protected communicationService = inject(CommunicationService);

    searchText?: string;
    currentPostContextFilter!: PostContextFilter; // set by setFilterAndSort() (implemented in subclasses) before use
    formGroup!: FormGroup; // set by resetFormGroup() (implemented in subclasses)
    readonly ButtonType = ButtonType;
    readonly course = signal<Course | undefined>(undefined);
    readonly createdPost = signal<Post | undefined>(undefined);
    readonly posts = signal<Post[]>([]);
    readonly isLoading = signal(true);

    currentSortCriterion = PostSortCriterion.CREATION_DATE;
    readonly currentSortDirection = signal<SortDirection | undefined>(undefined);
    readonly SortBy = PostSortCriterion;
    readonly SortDirection = SortDirection;

    protected postsSubscription?: Subscription;
    protected paramSubscription?: Subscription;

    // Icons
    faPlus = faPlus;
    faTimes = faTimes;
    faFilter = faFilter;
    faSearch = faSearch;
    faLongArrowAltUp = faLongArrowAltUp;
    faLongArrowAltDown = faLongArrowAltDown;

    /**
     * on changing any filter, the communication service is invoked to deliver all posts for the
     * currently set context, filtered on the server
     */
    onSelectContext(): void {
        this.setFilterAndSort();
        this.communicationService.getFilteredPosts(this.currentPostContextFilter);
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
