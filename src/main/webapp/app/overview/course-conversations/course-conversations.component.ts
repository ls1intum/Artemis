import { Component, OnDestroy, OnInit, ViewEncapsulation } from '@angular/core';
import { ConversationDto } from 'app/entities/metis/conversation/conversation.model';
import { Post } from 'app/entities/metis/post.model';
import { Subject, takeUntil } from 'rxjs';
import { MetisConversationService } from 'app/shared/metis/metis-conversation.service';
import { getAsChannelDto } from 'app/entities/metis/conversation/channel.model';

@Component({
    selector: 'jhi-course-conversations',
    templateUrl: './course-conversations.component.html',
    styleUrls: ['./course-conversations.component.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class CourseConversationsComponent implements OnInit, OnDestroy {
    private ngUnsubscribe = new Subject<void>();

    isLoading = false;
    isServiceSetUp = false;
    postInThread: Post;
    showPostThread = false;
    activeConversation?: ConversationDto = undefined;
    conversationsOfUser: ConversationDto[] = [];
    // MetisConversationService is created in course overview, so we can use it here
    constructor(public metisConversationService: MetisConversationService) {}

    getAsChannel = getAsChannelDto;
    setPostInThread(post?: Post) {
        this.showPostThread = false;

        if (post) {
            this.postInThread = post;
            this.showPostThread = true;
        }
    }
    ngOnInit(): void {
        this.metisConversationService.isServiceSetup$.subscribe((isServiceSetUp: boolean) => {
            if (isServiceSetUp) {
                // service is fully set up in course-overview, now we can subscribe to the respective observables
                this.subscribeToActiveConversation();
                this.subscribeToConversationsOfUser();
                this.subscribeToLoading();
                this.isServiceSetUp = true;
            }
        });
    }

    ngOnDestroy() {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
    }

    private subscribeToActiveConversation() {
        this.metisConversationService.activeConversation$.pipe(takeUntil(this.ngUnsubscribe)).subscribe((conversation: ConversationDto) => {
            this.activeConversation = conversation;
        });
    }

    private subscribeToConversationsOfUser() {
        this.metisConversationService.conversationsOfUser$.pipe(takeUntil(this.ngUnsubscribe)).subscribe((conversations: ConversationDto[]) => {
            this.conversationsOfUser = conversations ?? [];
        });
    }

    private subscribeToLoading() {
        this.metisConversationService.isLoading$.pipe(takeUntil(this.ngUnsubscribe)).subscribe((isLoading: boolean) => {
            this.isLoading = isLoading;
        });
    }
}
