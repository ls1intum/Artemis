import { Component, OnChanges, OnDestroy, OnInit, inject, input } from '@angular/core';
import { ChatServiceMode, IrisChatService } from 'app/iris/overview/iris-chat.service';
import { Post } from 'app/entities/metis/post.model';
import { IrisLogoComponent, IrisLogoSize } from 'app/iris/overview/iris-logo/iris-logo.component';
import { Subscription } from 'rxjs';
import { IrisMessage } from 'app/entities/iris/iris-message.model';
import { AsPipe } from 'app/shared/pipes/as.pipe';
import { IrisTextMessageContent } from 'app/entities/iris/iris-content-type.model';
import { PROFILE_IRIS } from 'app/app.constants';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { IrisSettingsService } from 'app/iris/manage/settings/shared/iris-settings.service';
import { Course } from 'app/entities/course.model';

/**
 * Component to display the tutor suggestion in the course overview
 * The tutor suggestion is displayed in a thread and is used to suggest answers for a tutor
 */
@Component({
    selector: 'jhi-tutor-suggestion',
    templateUrl: './tutor-suggestion.component.html',
    styleUrl: './tutor-suggestion.component.scss',
    imports: [IrisLogoComponent, AsPipe],
})
export class TutorSuggestionComponent implements OnInit, OnChanges, OnDestroy {
    protected readonly chatService = inject(IrisChatService);
    private profileService = inject(ProfileService);
    private irisSettingsService = inject(IrisSettingsService);

    messagesSubscription: Subscription;
    profileSubscription: Subscription;
    irisSettingsSubscription: Subscription;

    messages: IrisMessage[];
    suggestion: IrisMessage | undefined;

    irisEnabled = false;

    post = input<Post>();
    course = input<Course>();

    ngOnInit(): void {
        const post = this.post();
        const course = this.course();
        this.profileSubscription = this.profileService.getProfileInfo().subscribe((profileInfo) => {
            if (profileInfo?.activeProfiles.includes(PROFILE_IRIS)) {
                if (course?.id && post) {
                    this.irisSettingsSubscription = this.irisSettingsService.getCombinedCourseSettings(course.id).subscribe((settings) => {
                        this.irisEnabled = !!settings?.irisTutorSuggestionSettings?.enabled;
                        if (this.irisEnabled) {
                            this.chatService.switchTo(ChatServiceMode.TUTOR_SUGGESTION, post.id);
                            this.fetchMessages();
                        }
                    });
                }
            }
        });
    }

    ngOnChanges(): void {
        if (this.irisEnabled) {
            const post = this.post();
            if (post) {
                this.chatService.switchTo(ChatServiceMode.TUTOR_SUGGESTION, post.id);
            }
            this.fetchMessages();
        }
    }

    ngOnDestroy() {
        this.messagesSubscription?.unsubscribe();
        this.profileSubscription?.unsubscribe();
        this.irisSettingsSubscription?.unsubscribe();
    }

    sendMessage(): void {
        this.chatService.sendMessage('test').subscribe((m) => {});
    }

    private fetchMessages(): void {
        this.messagesSubscription = this.chatService.currentMessages().subscribe((messages) => {
            if (messages.length !== this.messages?.length) {
                this.suggestion = messages.first();
            }
            this.suggestion = messages.first();
            this.messages = messages;
        });
    }

    protected readonly IrisLogoSize = IrisLogoSize;
    protected readonly IrisTextMessageContent = IrisTextMessageContent;
}
