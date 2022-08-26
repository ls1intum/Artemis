import { AfterViewInit, Component, Input, OnDestroy, OnInit } from '@angular/core';
import interact from 'interactjs';
import { PageType } from 'app/shared/metis/metis.util';
import { ActivatedRoute, Router } from '@angular/router';
import { MetisService } from 'app/shared/metis/metis.service';
import { Post } from 'app/entities/metis/post.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { faArrowLeft, faChevronLeft, faGripLinesVertical, faXmark } from '@fortawesome/free-solid-svg-icons';
import { CourseDiscussionDirective } from 'app/shared/metis/course-discussion.directive';
import { FormBuilder } from '@angular/forms';

@Component({
    selector: 'jhi-thread-sidebar',
    templateUrl: './thread-sidebar.component.html',
    styleUrls: ['./thread-sidebar.component.scss'],
    providers: [MetisService],
})
export class ThreadSidebarComponent extends CourseDiscussionDirective implements OnInit, AfterViewInit, OnDestroy {
    collapsed = true;
    currentPostId?: number;
    post?: Post;

    @Input() set activePost(activePost: Post) {
        if (activePost) {
            this.post = activePost;
            this.collapsed = false;
        }
    }

    readonly pageType = PageType.PAGE_SECTION;

    // Icons
    faXmark = faXmark;
    faChevronLeft = faChevronLeft;
    faGripLinesVertical = faGripLinesVertical;
    faArrowLeft = faArrowLeft;

    constructor(
        protected metisService: MetisService,
        private activatedRoute: ActivatedRoute,
        private courseManagementService: CourseManagementService,
        private router: Router,
        private formBuilder: FormBuilder,
    ) {
        super(metisService);
    }

    /**
     * on initialization: initializes the metis service, fetches the posts for the exercise or lecture the discussion section is placed at,
     * creates the subscription to posts to stay updated on any changes of posts in this course
     */
    ngOnInit(): void {}

    /**
     * on leaving the page, the modal should be closed
     */
    ngOnDestroy(): void {
        super.onDestroy();
    }

    /**
     * defines a function that returns the post id as unique identifier,
     * by this means, Angular determines which post in the collection of posts has to be reloaded/destroyed on changes
     */
    postsTrackByFn = (index: number, post: Post): number => post.id!;

    /**
     * makes discussion section expandable by configuring 'interact'
     */
    ngAfterViewInit(): void {
        interact('.expanded-thread')
            .resizable({
                edges: { left: '.draggable-left', right: false, bottom: false, top: false },
                modifiers: [
                    // Set maximum width
                    interact.modifiers!.restrictSize({
                        min: { width: 375, height: 0 },
                        max: { width: 600, height: 4000 },
                    }),
                ],
                inertia: true,
            })
            .on('resizestart', function (event: any) {
                event.target.classList.add('card-resizable');
            })
            .on('resizeend', function (event: any) {
                event.target.classList.remove('card-resizable');
            })
            .on('resizemove', function (event: any) {
                const target = event.target;
                target.style.width = event.rect.width + 'px';
            });
    }

    resetCurrentPost() {
        this.post = undefined;
        this.currentPostId = undefined;
        this.router.navigate([], {
            queryParams: {
                postId: this.currentPostId,
            },
            queryParamsHandling: 'merge',
        });
    }

    resetFormGroup(): void {}

    setFilterAndSort(): void {}
}
