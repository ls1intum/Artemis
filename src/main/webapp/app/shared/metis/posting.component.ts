import { Posting } from 'app/entities/metis/posting.model';
import { PostingsService } from 'app/shared/metis/postings.service';
import { Directive, Input, OnInit } from '@angular/core';
import { User } from 'app/core/user/user.model';
import { ActivatedRoute } from '@angular/router';

@Directive()
export abstract class PostingComponent<T extends Posting> implements OnInit {
    @Input() posting: T;
    @Input() user: User;
    @Input() isAtLeastTutorInCourse: boolean;
    content?: string;
    maxContentLength = 1000;
    isEditMode: boolean;
    isLoading = false;
    courseId: number;

    // Only allow certain html tags and attributes
    allowedHtmlTags: string[] = ['a', 'b', 'strong', 'i', 'em', 'mark', 'small', 'del', 'ins', 'sub', 'sup', 'p', 'blockquote', 'pre', 'code', 'span', 'li', 'ul', 'ol'];
    allowedHtmlAttributes: string[] = ['href', 'class', 'id'];

    protected constructor(protected postingService: PostingsService<T>, protected route: ActivatedRoute) {}

    ngOnInit(): void {
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
        this.content = this.posting.content;
    }
}
