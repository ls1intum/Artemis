import { Component, OnInit } from '@angular/core';
import { Conversation, ConversationDto } from 'app/entities/metis/conversation/conversation.model';
import { Post } from 'app/entities/metis/post.model';
import { MetisService } from 'app/shared/metis/metis.service';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ActivatedRoute } from '@angular/router';
import { Subject, switchMap, take } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { Course } from 'app/entities/course.model';
import { finalize } from 'rxjs/operators';
import { MessagingService } from 'app/shared/metis/messaging.service';
import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/core/util/alert.service';

@Component({
    selector: 'jhi-course-messages',
    templateUrl: './course-messages.component.html',
    providers: [MetisService, MessagingService],
})
export class CourseMessagesComponent implements OnInit {
    // ToDo: vielleicht so einen Conversation Action listener machen, der dann die entsprechenden Methoden aufruft
    refreshConversations$ = new Subject<void>();

    conversations: ConversationDto[];
    activeConversation?: ConversationDto;

    isLoading = false;
    course: Course;

    postInThread: Post;
    showPostThread = false;
    constructor(
        private courseManagementService: CourseManagementService,
        private activatedRoute: ActivatedRoute,
        public messagingService: MessagingService,
        private alertService: AlertService,
    ) {}

    onConversationSelected(conversation: ConversationDto | undefined) {
        this.activeConversation = conversation;
    }

    setPostInThread(post?: Post) {
        this.showPostThread = false;

        if (!!post) {
            this.postInThread = post;
            this.showPostThread = true;
        }
    }

    refreshConversations() {
        this.isLoading = true;
        return this.messagingService.getConversationsOfUser(this.course?.id!).pipe(finalize(() => (this.isLoading = false)));
    }

    ngOnInit(): void {
        this.isLoading = true;
        this.activatedRoute
            .parent!.parent!.paramMap.pipe(
                take(1),
                switchMap((params) => {
                    const courseId = Number(params.get('courseId'));
                    return this.courseManagementService.findOneForDashboard(courseId);
                }),
                finalize(() => (this.isLoading = false)),
            )
            .subscribe({
                next: (courseResult) => {
                    this.course = courseResult.body!;
                    this.messagingService.getConversationsOfUser(this.course.id!).subscribe(() => {
                        this.messagingService.conversations.subscribe((conversations: ConversationDto[]) => {
                            this.conversations = conversations ?? [];
                            if (this.conversations.length > 0 && !this.activeConversation) {
                                // emit the value to fetch conversation posts on post overview tab
                                // ToDo: Überlegen welche conversation hier ausgewählt werden soll
                                this.activeConversation = this.conversations.first()!;
                            }
                        });
                    });
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
        this.refreshConversations$.subscribe(() => this.refreshConversations().subscribe());
    }

    onChannelOverViewModalResult(result: number | number[]) {
        this.refreshConversations().subscribe({
            next: () => {
                if (Array.isArray(result)) {
                    // result represents array of ids of conversations that were unsubscribed
                    if (this.activeConversation && result.includes(this.activeConversation.id!)) {
                        this.activeConversation = undefined;
                    }
                } else {
                    // result represent id of conversation that should be viewed
                    if (this.activeConversation && result !== this.activeConversation.id) {
                        this.activeConversation = this.conversations.find((conversation) => conversation.id === result);
                    }
                }
            },
        });
    }

    onNewConversationCreated(newConversation: Conversation) {
        this.messagingService.createConversation(this.course?.id!, newConversation).subscribe({
            next: (conversation: Conversation) => {
                this.activeConversation = conversation;
                this.refreshConversations$.next();
            },
            error: (res: HttpErrorResponse) => onError(this.alertService, res),
        });
    }
}
