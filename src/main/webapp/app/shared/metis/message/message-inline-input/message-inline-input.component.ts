import { Component, OnDestroy, OnInit, ViewEncapsulation, inject, input } from '@angular/core';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { Post } from 'app/entities/metis/post.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { PostContentValidationPattern } from 'app/shared/metis/metis.util';
import { PostingButtonComponent } from 'app/shared/metis/posting-button/posting-button.component';
import { PostingCreateEditDirective } from 'app/shared/metis/posting-create-edit.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { LocalStorageService } from 'ngx-webstorage';
import { PostingMarkdownEditorComponent } from '../../posting-markdown-editor/posting-markdown-editor.component';
import { MetisService } from 'app/shared/metis/metis.service';
import { Course } from 'app/entities/course.model';
import { MetisConversationService } from 'app/shared/metis/metis-conversation.service';
import { ChannelDTO, ChannelSubType, getAsChannelDTO } from 'app/entities/metis/conversation/channel.model';
import { IrisSettingsService } from 'app/iris/settings/shared/iris-settings.service';
import { ButtonType } from 'app/shared/components/button.component';
import { Observable, Subscription, catchError, of } from 'rxjs';
import { IrisSettings } from 'app/entities/iris/settings/iris-settings.model';

@Component({
    selector: 'jhi-message-inline-input',
    templateUrl: './message-inline-input.component.html',
    styleUrls: ['./message-inline-input.component.scss'],
    encapsulation: ViewEncapsulation.None,
    imports: [FormsModule, ReactiveFormsModule, PostingMarkdownEditorComponent, TranslateDirective, PostingButtonComponent, ArtemisTranslatePipe],
})
export class MessageInlineInputComponent extends PostingCreateEditDirective<Post | AnswerPost> implements OnInit, OnDestroy {
    protected localStorageService = inject(LocalStorageService);
    protected metisService = inject(MetisService);
    metisConversationService = inject(MetisConversationService);
    irisSettingsService = inject(IrisSettingsService);
    private irisSettings: IrisSettings | undefined;

    course = input<Course>();

    warningDismissed = false;
    irisEnabled = false;
    channelSubTypeReferenceRouterLink = '';

    private conversationServiceSubscription: Subscription;

    ngOnInit(): void {
        super.ngOnInit();
        this.conversationServiceSubscription = this.metisConversationService.activeConversation$.subscribe((conversation) => {
            this.checkIrisSettings(getAsChannelDTO(conversation));
        });
        this.warningDismissed = !!this.localStorageService.retrieve('chatWarningDismissed');
    }

    ngOnDestroy(): void {
        this.conversationServiceSubscription?.unsubscribe();
    }

    /**
     * resets the answer post content
     */
    resetFormGroup(): void {
        this.formGroup = this.formBuilder.group({
            // the pattern ensures that the content must include at least one non-whitespace character
            content: [this.posting.content, [Validators.required, Validators.maxLength(this.maxContentLength), PostContentValidationPattern]],
        });
    }

    /**
     * invokes the metis service after setting the title and current date as creation date of the new answer post,
     * ends the process successfully by closing the modal and stopping the button's loading animation
     */
    createPosting(): void {
        this.posting.content = this.formGroup.get('content')?.value;
        this.metisService.createPost(this.posting).subscribe({
            next: (post: Post) => {
                this.isLoading = false;
                this.onCreate.emit(post);
            },
            error: () => {
                this.isLoading = false;
            },
        });
    }

    /**
     * invokes the metis service with the updated answer post
     * ends the process successfully by closing the modal and stopping the button's loading animation
     */
    updatePosting(): void {
        this.posting.content = this.formGroup.get('content')?.value;
        this.metisService.updatePost(this.posting).subscribe({
            next: () => {
                this.isLoading = false;
                this.isModalOpen.emit();
            },
            error: () => {
                this.isLoading = false;
            },
        });
    }

    closeAlert() {
        this.warningDismissed = true;
        this.localStorageService.store('chatWarningDismissed', true);
    }

    /**
     * Helper method to check if iris is activated in the settings
     * @param channelDTO channel to be checked for
     * @private
     */
    private checkIrisSettings(channelDTO?: ChannelDTO): void {
        const handleIrisSettings = (
            settings: IrisSettings | undefined,
            getSettings: () => Observable<IrisSettings | undefined>,
            getEnabled: (settings: IrisSettings | undefined) => boolean | undefined,
        ) => {
            if (settings) {
                this.setIrisStatus(getEnabled(settings), channelDTO);
            } else {
                getSettings()
                    .pipe(
                        catchError(() => {
                            this.setIrisStatus();
                            return of(undefined);
                        }),
                    )
                    .subscribe((newSettings) => {
                        this.irisSettings = newSettings;
                        this.setIrisStatus(getEnabled(newSettings), channelDTO);
                    });
            }
        };
        switch (channelDTO?.subType) {
            case ChannelSubType.GENERAL: {
                const course = this.course();
                if (course?.studentCourseAnalyticsDashboardEnabled && course.id) {
                    handleIrisSettings(
                        this.irisSettings,
                        () => this.irisSettingsService.getCombinedCourseSettings(course.id!),
                        (settings) => settings?.irisCourseChatSettings?.enabled,
                    );
                } else {
                    this.setIrisStatus();
                }
                break;
            }
            case ChannelSubType.LECTURE:
                if (channelDTO.subTypeReferenceId) {
                    handleIrisSettings(
                        this.irisSettings,
                        () => this.irisSettingsService.getCombinedCourseSettings(channelDTO.subTypeReferenceId!),
                        (settings) => settings?.irisLectureChatSettings?.enabled,
                    );
                } else {
                    this.setIrisStatus();
                }
                break;
            case ChannelSubType.EXERCISE:
                if (channelDTO.subTypeReferenceId) {
                    handleIrisSettings(
                        this.irisSettings,
                        () => this.irisSettingsService.getCombinedExerciseSettings(channelDTO.subTypeReferenceId!),
                        (settings) => settings?.irisChatSettings?.enabled,
                    );
                } else {
                    this.setIrisStatus();
                }
                break;
            default:
                this.setIrisStatus();
                break;
        }
    }

    /**
     * Helper method to set the Iris status
     * @param enabled isIrisButton enabled
     * @param channelDTO channelDTO of channel the link is needed
     * @private
     */
    private setIrisStatus(enabled?: boolean, channelDTO?: ChannelDTO): void {
        this.irisEnabled = enabled ?? false;
        if (enabled) {
            this.channelSubTypeReferenceRouterLink = this.metisService.getLinkForChannelSubType(channelDTO) ?? '';
        } else {
            this.channelSubTypeReferenceRouterLink = '';
        }
    }

    protected readonly ButtonType = ButtonType;
}
