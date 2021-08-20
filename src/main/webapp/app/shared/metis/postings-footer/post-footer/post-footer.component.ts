import { Component, OnChanges, OnInit } from '@angular/core';
import { PostingsFooterDirective } from 'app/shared/metis/postings-footer/postings-footer.directive';
import { Post } from 'app/entities/metis/post.model';
import { MetisService, PageType } from 'app/shared/metis/metis.service';
import { Router } from '@angular/router';

@Component({
    selector: 'jhi-post-footer',
    templateUrl: './post-footer.component.html',
    styleUrls: ['./post-footer.component.scss'],
})
export class PostFooterComponent extends PostingsFooterDirective<Post> implements OnInit, OnChanges {
    tags: string[];
    pageType: PageType;
    ePageType = PageType;
    associatedContextName?: string;
    contextNavigationComponents?: (string | number)[];
    courseId: number;

    constructor(private metisService: MetisService, private router: Router) {
        super();
        this.pageType = metisService.getPageType();
        this.courseId = metisService.getCourse().id!;
    }

    /**
     * on initialization: updates the post tags
     */
    ngOnInit(): void {
        this.updateTags();
        if (this.pageType === PageType.OVERVIEW) {
            if (this.posting.exercise) {
                this.associatedContextName = this.posting.exercise.title;
                this.contextNavigationComponents = ['courses', this.courseId, 'exercises', this.posting.exercise.id!];
            }
            if (this.posting.lecture) {
                this.associatedContextName = this.posting.lecture.title;
                this.contextNavigationComponents = ['courses', this.courseId, 'lectures', this.posting.lecture.id!];
            }
            if (this.posting.courseWideContext) {
                this.associatedContextName = this.posting.courseWideContext;
            }
        }
    }

    /**
     * on changes: updates the post tags
     */
    ngOnChanges(): void {
        this.updateTags();
    }

    navigateToContext(event: any) {
        this.router.navigate(this.contextNavigationComponents!);
    }

    /**
     * sets the current post tags, empty error if none exit
     */
    private updateTags(): void {
        if (this.posting.tags) {
            this.tags = this.posting.tags;
        } else {
            this.tags = [];
        }
    }
}
