import { Component, OnDestroy, OnInit, inject, input, output } from '@angular/core';
import { ConversationDTO } from 'app/communication/shared/entities/conversation/conversation.model';
import { ChannelDTO, getAsChannelDTO, isChannelDTO } from 'app/communication/shared/entities/conversation/channel.model';
import { defaultSecondLayerDialogOptions, getUserLabel } from 'app/communication/course-conversations-components/other/conversation.util';
import { Course } from 'app/core/course/shared/entities/course.model';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { get } from 'lodash-es';
import {
    GenericUpdateTextPropertyDialogComponent,
    GenericUpdateTextPropertyTranslationKeys,
} from 'app/communication/course-conversations-components/generic-update-text-property-dialog/generic-update-text-property-dialog.component';
import { EMPTY, Subject, debounceTime, distinctUntilChanged, from, map, takeUntil } from 'rxjs';
import { onError } from 'app/shared/util/global.utils';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { AlertService } from 'app/shared/service/alert.service';
import { channelRegex } from 'app/communication/course-conversations-components/dialogs/channels-create-dialog/channel-form/channel-form.component';
import { GroupChatDTO, getAsGroupChatDTO, isGroupChatDTO } from 'app/communication/shared/entities/conversation/group-chat.model';
import { catchError } from 'rxjs/operators';
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
import { faVolumeUp, faVolumeXmark } from '@fortawesome/free-solid-svg-icons';
import { CourseNotificationSettingService } from 'app/communication/course-notification/course-notification-setting.service';
import { CourseNotificationSettingInfo } from 'app/communication/shared/entities/course-notification/course-notification-setting-info';
import { RouterLink } from '@angular/router';

@Component({
    selector: 'jhi-conversation-info',
    templateUrl: './conversation-info.component.html',
    styleUrls: ['./conversation-info.component.scss'],
    imports: [TranslateDirective, ArtemisDatePipe, ArtemisTranslatePipe, CommonModule, FormsModule, FaIconComponent, RouterLink],
})
export class ConversationInfoComponent implements OnInit, OnDestroy {
    private ngUnsubscribe = new Subject<void>();
    private mute$ = new Subject<boolean>();

    isGroupChat = isGroupChatDTO;
    isChannel = isChannelDTO;
    getAsChannel = getAsChannelDTO;
    getUserLabel = getUserLabel;
    canChangeChannelProperties = canChangeChannelProperties;
    canChangeGroupChatProperties = canChangeGroupChatProperties;

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
    private modalService = inject(NgbModal);
    private alertService = inject(AlertService);
    private conversationService = inject(ConversationService);
    private courseNotificationSettingService = inject(CourseNotificationSettingService);

    readOnlyMode = false;
    notificationSettings?: CourseNotificationSettingInfo;
    isNotificationsEnabled = false;

    // Icons
    faVolumeUp = faVolumeUp;
    faVolumeXmark = faVolumeXmark;

    ngOnInit(): void {
        if (this.activeConversation()) {
            if (getAsChannelDTO(this.activeConversation())) {
                this.readOnlyMode = !!getAsChannelDTO(this.activeConversation())?.isArchived;
            }
        }
        this.loadNotificationSettings();
        this.updateConversationIsMuted();
    }

    ngOnDestroy() {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
    }

    onChangePerformed() {
        this.changesPerformed.emit();
    }

    openEditNameModal(event: MouseEvent) {
        const channelOrGroupChat = this.getAsChannelOrGroupChat(this.activeConversation()!);
        if (!channelOrGroupChat) {
            return;
        }

        const keys = {
            labelKey: 'artemisApp.dialogs.createChannel.channelForm.nameInput.label',
            titleKey: 'artemisApp.dialogs.createChannel.channelForm.nameInput.label',
            helpKey: '',
            maxLengthErrorKey: 'artemisApp.dialogs.createChannel.channelForm.nameInput.maxLengthValidationError',
            requiredErrorKey: 'artemisApp.dialogs.createChannel.channelForm.nameInput.requiredValidationError',
            regexErrorKey: 'artemisApp.dialogs.createChannel.channelForm.nameInput.regexValidationError',
        };

        event.stopPropagation();
        this.openEditPropertyDialog(channelOrGroupChat, 'name', 30, true, channelRegex, keys);
    }

    openEditTopicModal(event: MouseEvent) {
        const channel = getAsChannelDTO(this.activeConversation());
        if (!channel) {
            return;
        }

        const keys = {
            labelKey: 'artemisApp.dialogs.editChannelTopic.topicInput.label',
            titleKey: 'artemisApp.dialogs.editChannelTopic.topicInput.topicHelp',
            helpKey: 'artemisApp.dialogs.editChannelTopic.topicInput.topicHelp',
            maxLengthErrorKey: 'artemisApp.dialogs.editChannelTopic.topicInput.maxLengthValidationError',
            requiredErrorKey: '',
            regexErrorKey: '',
        };

        event.stopPropagation();
        this.openEditPropertyDialog(channel, 'topic', 250, false, undefined, keys);
    }

    openDescriptionTopicModal(event: MouseEvent) {
        const channel = getAsChannelDTO(this.activeConversation());
        if (!channel) {
            return;
        }

        const keys = {
            labelKey: 'artemisApp.dialogs.createChannel.channelForm.descriptionInput.label',
            titleKey: 'artemisApp.dialogs.createChannel.channelForm.descriptionInput.label',
            helpKey: 'artemisApp.dialogs.createChannel.channelForm.descriptionInput.descriptionHelp',
            maxLengthErrorKey: 'artemisApp.dialogs.createChannel.channelForm.descriptionInput.maxLengthValidationError',
            requiredErrorKey: '',
            regexErrorKey: '',
        };

        event.stopPropagation();
        this.openEditPropertyDialog(channel, 'description', 250, false, undefined, keys);
    }

    private openEditPropertyDialog(
        channelOrGroupChat: ChannelDTO | GroupChatDTO,
        propertyName: string,
        maxLength: number,
        isRequired: boolean,
        regexPattern: RegExp | undefined,
        translationKeys: GenericUpdateTextPropertyTranslationKeys,
    ) {
        const modalRef: NgbModalRef = this.modalService.open(GenericUpdateTextPropertyDialogComponent, defaultSecondLayerDialogOptions);
        modalRef.componentInstance.propertyName = propertyName;
        modalRef.componentInstance.maxPropertyLength = maxLength;
        modalRef.componentInstance.translationKeys = translationKeys;
        modalRef.componentInstance.isRequired = isRequired;
        modalRef.componentInstance.regexPattern = regexPattern;
        const property = get(channelOrGroupChat, propertyName);
        if (property && typeof property === 'string' && property.length > 0) {
            modalRef.componentInstance.initialValue = property;
        }
        modalRef.componentInstance.initialize();
        from(modalRef.result)
            .pipe(
                catchError(() => EMPTY),
                takeUntil(this.ngUnsubscribe),
            )
            .subscribe((newValue: string) => {
                let updateValue: string;
                if (newValue && newValue.trim().length > 0) {
                    updateValue = newValue.trim();
                } else {
                    updateValue = '';
                }
                if (isChannelDTO(channelOrGroupChat)) {
                    this.updateChannel(channelOrGroupChat, propertyName as keyof ChannelDTO, updateValue);
                } else {
                    this.updateGroupChat(channelOrGroupChat, propertyName as keyof GroupChatDTO, updateValue);
                }
            });
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
                    this.onChangePerformed();
                },
                error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
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
                    this.onChangePerformed();
                },
                error: (errorResponse: HttpErrorResponse) => {
                    if (errorResponse.error?.skipAlert) {
                        onError(this.alertService, errorResponse);
                    }
                },
            });
    }

    onMuteToggle(): void {
        const currentMuted = this.activeConversation()?.isMuted ?? false;
        this.mute$.next(!currentMuted);
    }

    private updateConversationIsMuted() {
        this.mute$.pipe(debounceTime(100), distinctUntilChanged(), takeUntil(this.ngUnsubscribe)).subscribe((isMuted) => {
            const courseId = this.course()?.id;
            const conversationId = this.activeConversation()?.id;

            if (!courseId || !conversationId) return;

            this.conversationService.updateIsMuted(courseId, conversationId, isMuted).subscribe({
                next: () => {
                    this.activeConversation()!.isMuted = isMuted;
                    this.onChangePerformed();
                    this.changesPerformed.emit();
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

        this.courseNotificationSettingService.getSettingInfo(courseId).subscribe({
            next: (response) => {
                if (response.body) {
                    this.notificationSettings = response.body;
                    this.checkNotificationStatus();
                }
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
