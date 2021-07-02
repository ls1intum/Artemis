import { Posting } from 'app/entities/metis/posting.model';
import { PostingsService } from 'app/shared/metis/postings.service';
import { Directive, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { User } from 'app/core/user/user.model';

@Directive()
export abstract class PostingsHeaderDirective<T extends Posting> implements OnInit {
    @Input() posting: T;
    @Input() user: User;
    @Input() isAtLeastTutorInCourse: boolean;
    @Input() courseId: number;
    @Output() onDelete: EventEmitter<T> = new EventEmitter<T>();
    @Output() onUpdate: EventEmitter<T> = new EventEmitter<T>();
    isAuthorOfPosting: boolean;

    protected constructor(protected postingService: PostingsService<T>) {}

    ngOnInit(): void {
        this.isAuthorOfPosting = this.user ? this.posting?.author!.id === this.user.id : false;
    }

    deletePosting(): void {
        this.postingService.delete(this.courseId, this.posting).subscribe(() => {
            this.onDelete.emit(this.posting);
        });
    }
}
