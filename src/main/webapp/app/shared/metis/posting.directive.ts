import { Posting } from 'app/entities/metis/posting.model';
import { PostingsService } from 'app/shared/metis/postings.service';
import { Directive, Input, OnInit } from '@angular/core';
import { User } from 'app/core/user/user.model';

@Directive()
export abstract class PostingDirective<T extends Posting> implements OnInit {
    @Input() posting: T;
    @Input() user: User;
    @Input() isAtLeastTutorInCourse: boolean;
    @Input() courseId: number;
    content?: string;
    maxContentLength = 1000;
    isEditMode: boolean;
    isLoading = false;

    // Only allow certain html tags and attributes
    allowedHtmlTags: string[] = ['a', 'b', 'strong', 'i', 'em', 'mark', 'small', 'del', 'ins', 'sub', 'sup', 'p', 'blockquote', 'pre', 'code', 'span', 'li', 'ul', 'ol'];
    allowedHtmlAttributes: string[] = ['href', 'class', 'id'];

    protected constructor(protected postingService: PostingsService<T>) {}

    ngOnInit(): void {
        this.content = this.posting.content;
    }

    /**
     * Updates the text of the selected posting
     */
    updatePosting(): void {
        this.isLoading = true;
        this.posting.content = this.content;
        this.postingService.update(this.courseId, this.posting).subscribe({
            next: () => {
                this.isEditMode = false;
            },
            error: () => {
                this.isLoading = false;
            },
            complete: () => {
                this.isLoading = false;
            },
        });
    }

    /**
     * toggles the edit Mode
     * set the editor text to the answer text
     */
    toggleEditMode(): void {
        this.isEditMode = !this.isEditMode;
        this.content = this.posting.content;
    }
}
