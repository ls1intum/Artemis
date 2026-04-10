import { Component, OnDestroy, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { ConversationDTO } from 'app/communication/shared/entities/conversation/conversation.model';
import { ChannelDTO, getAsChannelDTO, isChannelDTO } from 'app/communication/shared/entities/conversation/channel.model';
import { getUserLabel } from 'app/communication/course-conversations-components/other/conversation.util';
import { Course } from 'app/core/course/shared/entities/course.model';
import { Subject, debounceTime, distinctUntilChanged, filter, map, takeUntil } from 'rxjs';
import { onError } from 'app/shared/util/global.utils';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { AlertService } from 'app/shared/service/alert.service';
import { channelRegex } from 'app/communication/course-conversations-components/dialogs/channels-create-dialog/channel-form/channel-form.component';
import { GroupChatDTO, getAsGroupChatDTO, isGroupChatDTO } from 'app/communication/shared/entities/conversation/group-chat.model';
import { ConversationUserDTO } from 'app/communication/shared/entities/conversation/conversation-user-dto.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { canChangeChannelProperties, canChangeGroupChatProperties } from 'app/communication/conversations/conversation-permissions.utils';
import { ChannelService } from 'app/communication/conversations/service/channel.service';
import { GroupChatService } from 'app/communication/conversations/service/group-chat.service';
import { ConversationService } from 'app/communication/conversations/service/conversation.service';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCalendarPlus, faUser, faVolumeXmark } from '@fortawesome/free-solid-svg-icons';
import { SelectButton } from 'primeng/selectbutton';
import { TranslateService } from '@ngx-translate/core';
import { CourseNotificationSettingService } from 'app/communication/course-notification/course-notification-setting.service';
import { CourseNotificationSettingInfo } from 'app/communication/shared/entities/course-notification/course-notification-setting-info';
import { RouterLink } from '@angular/router';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';

@Component({
    selector: 'jhi-conversation-info',
    templateUrl: './conversation-info.component.html',
    styleUrls: ['./conversation-info.component.scss'],
    imports: [TranslateDirective, ArtemisDatePipe, ArtemisTranslatePipe, CommonModule, FormsModule, FaIconComponent, RouterLink, SelectButton, InputTextModule, TextareaModule],
})
export class ConversationInfoComponent implements OnInit, OnDestroy {
    private ngUnsubscribe = new Subject<void>();
    private mute$ = new Subject<boolean>();
    private nameChange$ = new Subject<string>();
    private topicChange$ = new Subject<string>();
    private descriptionChange$ = new Subject<string>();

    isGroupChat = isGroupChatDTO;
    isChannel = isChannelDTO;
    getAsChannel = getAsChannelDTO;
    getUserLabel = getUserLabel;
    canChangeChannelProperties = canChangeChannelProperties;
    canChangeGroupChatProperties = canChangeGroupChatProperties;

    // Inline editing state
    editableName = signal('');
    editableTopic = signal('');
    editableDescription = signal('');
    saveStatus = signal<'idle' | 'unsaved' | 'saving' | 'saved'>('idle');

    nameError = computed(() => {
        const name = this.editableName();
        if (!name || name.trim().length === 0) {
            return 'required';
        }
        if (name.length > 20) {
            return 'maxLength';
        }
        if (!channelRegex.test(name)) {
            return 'pattern';
        }
        return undefined;
    });

    canEditName(): boolean {
        if (this.readOnlyMode) return false;
        const channelOrGroupChat = this.getAsChannelOrGroupChat(this.activeConversation());
        if (!channelOrGroupChat) return false;
        return isChannelDTO(channelOrGroupChat) ? this.canChangeChannelProperties(channelOrGroupChat) : this.canChangeGroupChatProperties(channelOrGroupChat);
    }

    getAsChannelOrGroupChat(conversation: ConversationDTO): ChannelDTO | GroupChatDTO | undefined {
        return getAsChannelDTO(conversation) || getAsGroupChatDTO(conversation);
    }

    getCreator(): ConversationUserDTO | null {
        return this.activeConversation()?.creator as ConversationUserDTO | null;
    }

    activeConversation = input.required<ConversationDTO>();
    course = input<Course>();
    changesPerformed = output<void>();

    private channelService = inject(ChannelService);
    private groupChatService = inject(GroupChatService);
    private alertService = inject(AlertService);
    private conversationService = inject(ConversationService);
    private courseNotificationSettingService = inject(CourseNotificationSettingService);
    private translateService = inject(TranslateService);

    readOnlyMode = false;
    notificationSettings?: CourseNotificationSettingInfo;
    isNotificationsEnabled = false;

    muteOptions: { label: string; value: boolean }[] = [];

    protected readonly faVolumeXmark = faVolumeXmark;
    protected readonly faCalendarPlus = faCalendarPlus;
    protected readonly faUser = faUser;

    ngOnInit(): void {
        this.muteOptions = [
            { label: this.translateService.instant('artemisApp.dialogs.conversationDetail.infoTab.unmuted'), value: false },
            { label: this.translateService.instant('artemisApp.dialogs.conversationDetail.infoTab.muted'), value: true },
        ];
        if (this.activeConversation()) {
            if (getAsChannelDTO(this.activeConversation())) {
                this.readOnlyMode = !!getAsChannelDTO(this.activeConversation())?.isArchived;
            }
        }
        this.initEditableValues();
        this.loadNotificationSettings();
        this.updateConversationIsMuted();
        this.setupAutoSave();
    }

    private initEditableValues() {
        const channelOrGroupChat = this.getAsChannelOrGroupChat(this.activeConversation());
        if (channelOrGroupChat) {
            this.editableName.set(channelOrGroupChat.name ?? '');
        }
        const channel = getAsChannelDTO(this.activeConversation());
        if (channel) {
            this.editableTopic.set(channel.topic ?? '');
            this.editableDescription.set(channel.description ?? '');
        }
    }

    ngOnDestroy() {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
    }

    onChangePerformed() {
        this.changesPerformed.emit();
    }

    onNameInput(value: string) {
        this.editableName.set(value);
        this.saveStatus.set('unsaved');
        this.nameChange$.next(value);
    }

    onTopicInput(value: string) {
        this.editableTopic.set(value);
        this.saveStatus.set('unsaved');
        this.topicChange$.next(value);
    }

    onDescriptionInput(value: string) {
        this.editableDescription.set(value);
        this.saveStatus.set('unsaved');
        this.descriptionChange$.next(value);
    }

    private setupAutoSave() {
        this.nameChange$.pipe(debounceTime(1000), distinctUntilChanged(), takeUntil(this.ngUnsubscribe)).subscribe((value) => {
            this.autoSaveProperty('name', value);
        });

        this.topicChange$.pipe(debounceTime(1000), distinctUntilChanged(), takeUntil(this.ngUnsubscribe)).subscribe((value) => {
            this.autoSaveProperty('topic', value);
        });

        this.descriptionChange$.pipe(debounceTime(1000), distinctUntilChanged(), takeUntil(this.ngUnsubscribe)).subscribe((value) => {
            this.autoSaveProperty('description', value);
        });
    }

    autoSaveProperty(propertyName: string, value: string) {
        if (propertyName === 'name' && this.nameError()) {
            return;
        }

        const trimmedValue = value.trim();
        const channelOrGroupChat = this.getAsChannelOrGroupChat(this.activeConversation()!);
        if (!channelOrGroupChat) return;

        this.saveStatus.set('saving');

        if (isChannelDTO(channelOrGroupChat)) {
            this.updateChannel(channelOrGroupChat, propertyName as keyof ChannelDTO, trimmedValue);
        } else {
            this.updateGroupChat(channelOrGroupChat, propertyName as keyof GroupChatDTO, trimmedValue);
        }
    }

    private updateGroupChat<K extends keyof GroupChatDTO>(groupChat: GroupChatDTO, propertyName: K, updateValue: GroupChatDTO[K]) {
        const courseId = this.course()?.id;
        if (!courseId) {
            return;
        }
        const updateDTO = new GroupChatDTO();
        updateDTO[propertyName] = updateValue;

        this.groupChatService
            .update(courseId, groupChat.id!, updateDTO)
            .pipe(
                map((res: HttpResponse<GroupChatDTO>) => res.body),
                takeUntil(this.ngUnsubscribe),
            )
            .subscribe({
                next: (updatedGroupChat: GroupChatDTO) => {
                    groupChat[propertyName] = updatedGroupChat[propertyName];
                    this.markSaved();
                    this.onChangePerformed();
                },
                error: (errorResponse: HttpErrorResponse) => {
                    this.saveStatus.set('unsaved');
                    onError(this.alertService, errorResponse);
                },
            });
    }

    private updateChannel<K extends keyof ChannelDTO>(channel: ChannelDTO, propertyName: K, updateValue: ChannelDTO[K]) {
        const courseId = this.course()?.id;
        if (!courseId) {
            return;
        }
        const updateDTO = new ChannelDTO();
        updateDTO[propertyName] = updateValue;
        this.channelService
            .update(courseId, channel.id!, updateDTO)
            .pipe(
                map((res: HttpResponse<ChannelDTO>) => res.body),
                takeUntil(this.ngUnsubscribe),
            )
            .subscribe({
                next: (updatedChannel: ChannelDTO) => {
                    channel[propertyName] = updatedChannel[propertyName];
                    this.markSaved();
                    this.onChangePerformed();
                },
                error: (errorResponse: HttpErrorResponse) => {
                    this.saveStatus.set('unsaved');
                    if (errorResponse.error?.skipAlert) {
                        onError(this.alertService, errorResponse);
                    }
                },
            });
    }

    private markSaved() {
        this.saveStatus.set('saved');
        setTimeout(() => {
            if (this.saveStatus() === 'saved') {
                this.saveStatus.set('idle');
            }
        }, 2000);
    }

    onMuteOptionChange(value: boolean): void {
        this.mute$.next(value);
    }

    private updateConversationIsMuted() {
        this.mute$.pipe(debounceTime(100), distinctUntilChanged(), takeUntil(this.ngUnsubscribe)).subscribe((isMuted) => {
            const courseId = this.course()?.id;
            const conversationId = this.activeConversation()?.id;

            if (!courseId || !conversationId) return;

            this.conversationService.updateIsMuted(courseId, conversationId, isMuted).subscribe({
                next: () => {
                    const conversation = this.activeConversation();
                    if (conversation) {
                        conversation.isMuted = isMuted;
                    }
                    this.onChangePerformed();
                },
                error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
            });
        });
    }

    private loadNotificationSettings() {
        const courseId = this.course()?.id;
        if (!courseId) {
            return;
        }

        this.courseNotificationSettingService
            .getSettingInfo(courseId)
            .pipe(filter((settings): settings is CourseNotificationSettingInfo => !!settings))
            .subscribe({
                next: (settings) => {
                    this.notificationSettings = settings;
                    this.checkNotificationStatus();
                },
                error: (error: HttpErrorResponse) => onError(this.alertService, error),
            });
    }

    private checkNotificationStatus() {
        this.isNotificationsEnabled = this.notificationSettings?.selectedPreset !== 3;
    }

    protected readonly ConversationDTO = ConversationDTO;
    protected readonly ConversationUserDTO = ConversationUserDTO;
}
