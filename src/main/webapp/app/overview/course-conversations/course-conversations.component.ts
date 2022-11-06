import { Component, OnInit } from '@angular/core';
import { ConversationDto } from 'app/entities/metis/conversation/conversation.model';
import { Post } from 'app/entities/metis/post.model';
import { MetisService } from 'app/shared/metis/metis.service';
import { ActivatedRoute } from '@angular/router';
import { switchMap, take } from 'rxjs';
import { MetisConversationService } from 'app/shared/metis/metis-conversation.service';

@Component({
    selector: 'jhi-course-conversations',
    templateUrl: './course-conversations.component.html',
    providers: [MetisService, MetisConversationService],
})
export class CourseConversationsComponent implements OnInit {
    isLoading = false;
    isServiceSetUp = false;

    postInThread: Post;
    showPostThread = false;
    activeConversation?: ConversationDto = undefined;
    conversations: ConversationDto[] = [];
    constructor(private activatedRoute: ActivatedRoute, public metisConversationService: MetisConversationService) {}

    setPostInThread(post?: Post) {
        this.showPostThread = false;

        if (!!post) {
            this.postInThread = post;
            this.showPostThread = true;
        }
    }
    ngOnInit(): void {
        this.activatedRoute
            .parent!.parent!.paramMap.pipe(
                take(1),
                switchMap((params) => {
                    const courseId = Number(params.get('courseId'));
                    return this.metisConversationService.setUpConversationService(courseId);
                }),
            )
            .subscribe({
                complete: () => {
                    // service is fully set up, now we can subscribe to the respective observables
                    this.subscribeToActiveConversation();
                    this.subscribeToConversationsOfUser();
                    this.subscribeToLoading();
                    this.isServiceSetUp = true;
                },
            });
    }

    private subscribeToActiveConversation() {
        this.metisConversationService.activeConversation$.subscribe((conversation: ConversationDto) => {
            this.activeConversation = conversation;
        });
    }

    private subscribeToConversationsOfUser() {
        this.metisConversationService.conversationsOfUser$.subscribe((conversations: ConversationDto[]) => {
            this.conversations = conversations ?? [];
        });
    }

    private subscribeToLoading() {
        this.metisConversationService.isLoading$.subscribe((isLoading: boolean) => {
            this.isLoading = isLoading;
        });
    }
}
