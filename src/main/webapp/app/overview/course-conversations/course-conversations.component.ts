import { Component, OnDestroy, OnInit, TemplateRef, ViewChild, ViewEncapsulation } from '@angular/core';
import { ConversationDto } from 'app/entities/metis/conversation/conversation.model';
import { Post } from 'app/entities/metis/post.model';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject, take, takeUntil } from 'rxjs';
import { MetisConversationService } from 'app/shared/metis/metis-conversation.service';
import { getAsChannelDto } from 'app/entities/metis/conversation/channel.model';
import { MetisService } from 'app/shared/metis/metis.service';
import { Course } from 'app/entities/course.model';
import { PageType } from 'app/shared/metis/metis.util';
import { BarControlConfiguration } from 'app/shared/tab-bar/tab-bar';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { ConversationCodeOfConductDialogComponent } from 'app/overview/course-conversations/dialogs/code-of-conduct/conversation-code-of-conduct-dialog.component';

@Component({
    selector: 'jhi-course-conversations',
    templateUrl: './course-conversations.component.html',
    styleUrls: ['./course-conversations.component.scss'],
    encapsulation: ViewEncapsulation.None,
    providers: [MetisService],
})
export class CourseConversationsComponent implements OnInit, OnDestroy {
    private ngUnsubscribe = new Subject<void>();
    course?: Course;
    isLoading = false;
    isServiceSetUp = false;
    postInThread?: Post;
    activeConversation?: ConversationDto = undefined;
    conversationsOfUser: ConversationDto[] = [];

    // The extracted controls template from our template to be rendered in the top bar of "CourseOverviewComponent"
    @ViewChild('controls', { static: false }) private controls: TemplateRef<any>;
    // Provides the control configuration to be read and used by "CourseOverviewComponent"
    public readonly controlConfiguration: BarControlConfiguration = {
        subject: new Subject<TemplateRef<any>>(),
    };

    // MetisConversationService is created in course overview, so we can use it here
    constructor(
        private router: Router,
        private activatedRoute: ActivatedRoute,
        public metisConversationService: MetisConversationService,
        public metisService: MetisService,
        private modalService: NgbModal,
    ) {}

    getAsChannel = getAsChannelDto;

    private subscribeToMetis() {
        this.metisService.posts.pipe(takeUntil(this.ngUnsubscribe)).subscribe((posts: Post[]) => {
            if (this.postInThread?.id && posts) {
                this.postInThread = posts.find((post) => post.id === this.postInThread?.id);
            }
        });
    }

    private setupMetis() {
        this.metisService.setPageType(PageType.OVERVIEW);
        this.metisService.setCourse(this.course!);
    }

    ngOnInit(): void {
        this.metisConversationService.isServiceSetup$.pipe(takeUntil(this.ngUnsubscribe)).subscribe((isServiceSetUp: boolean) => {
            if (isServiceSetUp) {
                this.course = this.metisConversationService.course;
                this.setupMetis();
                this.subscribeToMetis();
                this.subscribeToQueryParameter();
                // service is fully set up, now we can subscribe to the respective observables
                this.subscribeToActiveConversation();
                this.subscribeToConversationsOfUser();
                this.subscribeToLoading();
                this.isServiceSetUp = true;
                this.updateQueryParameters();
            }
        });
    }

    subscribeToQueryParameter() {
        this.activatedRoute.queryParams.pipe(take(1), takeUntil(this.ngUnsubscribe)).subscribe((queryParams) => {
            if (queryParams.conversationId) {
                this.metisConversationService.setActiveConversation(Number(queryParams.conversationId));
            }
        });
    }

    updateQueryParameters() {
        this.router.navigate([], {
            relativeTo: this.activatedRoute,
            queryParams: {
                conversationId: this.activeConversation?.id,
            },
            replaceUrl: true,
            queryParamsHandling: 'merge',
        });
    }

    ngAfterViewInit(): void {
        // Send our controls template to parent so it will be rendered in the top bar
        if (this.controls) {
            this.controlConfiguration.subject!.next(this.controls);
        }
    }

    ngOnDestroy() {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
    }

    openDialog() {
        const modalRef: NgbModalRef = this.modalService.open(ConversationCodeOfConductDialogComponent, {});
        modalRef.componentInstance.codeOfConduct = this.course?.courseInformationSharingMessagingCodeOfConduct;
    }

    private subscribeToActiveConversation() {
        this.metisConversationService.activeConversation$.pipe(takeUntil(this.ngUnsubscribe)).subscribe((conversation: ConversationDto) => {
            this.activeConversation = conversation;
            this.postInThread = undefined;
            this.updateQueryParameters();
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
