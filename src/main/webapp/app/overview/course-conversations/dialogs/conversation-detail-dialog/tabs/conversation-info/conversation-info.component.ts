import { Component, EventEmitter, Input, OnDestroy, OnInit, Output } from '@angular/core';
import { ConversationDto } from 'app/entities/metis/conversation/conversation.model';
import { ChannelDTO, getAsChannelDto, isChannelDto } from 'app/entities/metis/conversation/channel.model';
import { getUserLabel } from 'app/overview/course-conversations/other/conversation.util';
import { ChannelService } from 'app/shared/metis/conversations/channel.service';
import { Course } from 'app/entities/course.model';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { get } from 'lodash-es';
import {
    GenericUpdateTextPropertyDialog,
    GenericUpdateTextPropertyTranslationKeys,
} from 'app/overview/course-conversations/dialogs/generic-update-text-property-dialog/generic-update-text-property-dialog.component';
import { Subject, from, map, takeUntil } from 'rxjs';
import { onError } from 'app/shared/util/global.utils';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { AlertService } from 'app/core/util/alert.service';
import { channelRegex } from 'app/overview/course-conversations/dialogs/channels-create-dialog/channel-form/channel-form.component';
import { canChangeChannelProperties, canChangeGroupChatProperties } from 'app/shared/metis/conversations/conversation-permissions.utils';
import { GroupChatDto, getAsGroupChatDto, isGroupChatDto } from 'app/entities/metis/conversation/group-chat.model';
import { GroupChatService } from 'app/shared/metis/conversations/group-chat.service';

@Component({
    selector: 'jhi-conversation-info',
    templateUrl: './conversation-info.component.html',
    styleUrls: ['./conversation-info.component.scss'],
})
export class ConversationInfoComponent implements OnInit, OnDestroy {
    private ngUnsubscribe = new Subject<void>();

    isGroupChat = isGroupChatDto;
    isChannel = isChannelDto;
    getAsGroupChat = getAsGroupChatDto;
    getAsChannel = getAsChannelDto;
    getUserLabel = getUserLabel;
    canChangeChannelProperties = canChangeChannelProperties;
    canChangeGroupChatProperties = canChangeGroupChatProperties;

    getAsChannelOrGroupChat(conversation: ConversationDto): ChannelDTO | GroupChatDto | undefined {
        return getAsChannelDto(conversation) || getAsGroupChatDto(conversation);
    }

    @Input()
    activeConversation: ConversationDto;

    @Input()
    course: Course;

    @Output()
    changesPerformed = new EventEmitter<void>();

    readOnlyMode = false;
    constructor(private channelService: ChannelService, private groupChatService: GroupChatService, private modalService: NgbModal, private alertService: AlertService) {}

    ngOnInit(): void {
        if (this.activeConversation) {
            if (getAsChannelDto(this.activeConversation)) {
                this.readOnlyMode = !!getAsChannelDto(this.activeConversation)?.isArchived;
            }
        }
    }

    ngOnDestroy() {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
    }

    onChangePerformed() {
        this.changesPerformed.emit();
    }

    openEditNameModal(event: MouseEvent) {
        const channelOrGroupChat = this.getAsChannelOrGroupChat(this.activeConversation);
        if (!channelOrGroupChat) {
            return;
        }

        const keys = {
            labelKey: 'artemisApp.forms.channelForm.nameInput.label',
            titleKey: 'artemisApp.forms.channelForm.nameInput.label',
            helpKey: '',
            maxLengthErrorKey: 'artemisApp.forms.channelForm.nameInput.maxLengthValidationError',
            requiredErrorKey: 'artemisApp.forms.channelForm.nameInput.requiredValidationError',
            regexErrorKey: 'artemisApp.forms.channelForm.nameInput.regexValidationError',
        };

        event.stopPropagation();
        this.openEditPropertyDialog(channelOrGroupChat, 'name', 20, true, channelRegex, keys);
    }

    openEditTopicModal(event: MouseEvent) {
        const channel = getAsChannelDto(this.activeConversation);
        if (!channel) {
            return;
        }

        const keys = {
            labelKey: 'artemisApp.forms.channelForm.topicInput.label',
            titleKey: 'artemisApp.forms.channelForm.topicInput.label',
            helpKey: 'artemisApp.forms.channelForm.topicInput.topicHelp',
            maxLengthErrorKey: 'artemisApp.forms.channelForm.topicInput.maxLengthValidationError',
            requiredErrorKey: '',
            regexErrorKey: '',
        };

        event.stopPropagation();
        this.openEditPropertyDialog(channel, 'topic', 250, false, undefined, keys);
    }

    openDescriptionTopicModal(event: MouseEvent) {
        const channel = getAsChannelDto(this.activeConversation);
        if (!channel) {
            return;
        }

        const keys = {
            labelKey: 'artemisApp.forms.channelForm.descriptionInput.label',
            titleKey: 'artemisApp.forms.channelForm.descriptionInput.label',
            helpKey: 'artemisApp.forms.channelForm.descriptionInput.descriptionHelp',
            maxLengthErrorKey: 'artemisApp.forms.channelForm.descriptionInput.maxLengthValidationError',
            requiredErrorKey: '',
            regexErrorKey: '',
        };

        event.stopPropagation();
        this.openEditPropertyDialog(channel, 'description', 250, false, undefined, keys);
    }

    private openEditPropertyDialog(
        channelOrGroupChat: ChannelDTO | GroupChatDto,
        propertyName: string,
        maxLength: number,
        isRequired: boolean,
        regexPattern: RegExp | undefined,
        translationKeys: GenericUpdateTextPropertyTranslationKeys,
    ) {
        const modalRef: NgbModalRef = this.modalService.open(GenericUpdateTextPropertyDialog, {
            size: 'lg',
            scrollable: false,
            backdrop: 'static',
            backdropClass: 'second-layer-modal-bg',
            centered: true,
        });
        modalRef.componentInstance.propertyName = propertyName;
        modalRef.componentInstance.maxPropertyLength = maxLength;
        modalRef.componentInstance.translationKeys = translationKeys;
        modalRef.componentInstance.isRequired = isRequired;
        modalRef.componentInstance.regexPattern = regexPattern;

        if (get(channelOrGroupChat, propertyName) && get(channelOrGroupChat, propertyName).length > 0) {
            modalRef.componentInstance.initialValue = get(channelOrGroupChat, propertyName);
        }
        modalRef.componentInstance.initialize();
        from(modalRef.result)
            .pipe(takeUntil(this.ngUnsubscribe))
            .subscribe((newValue: string) => {
                let updateValue = null;
                if (newValue && newValue.trim().length > 0) {
                    updateValue = newValue.trim();
                } else {
                    updateValue = '';
                }
                if (isChannelDto(channelOrGroupChat)) {
                    this.updateChannel(channelOrGroupChat, propertyName, updateValue);
                } else {
                    this.updateGroupChat(channelOrGroupChat, propertyName, updateValue);
                }
            });
    }

    private updateGroupChat(groupChat: GroupChatDto, propertyName: string, updateValue: string) {
        this.groupChatService
            .update(this.course?.id!, groupChat.id!, { [propertyName]: updateValue })
            .pipe(
                map((res: HttpResponse<GroupChatDto>) => res.body),
                takeUntil(this.ngUnsubscribe),
            )
            .subscribe({
                next: (updatedGroupChat: GroupChatDto) => {
                    groupChat[propertyName] = updatedGroupChat[propertyName];
                    this.onChangePerformed();
                },
                error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
            });
    }

    private updateChannel(channel: ChannelDTO, propertyName: string, updateValue: string) {
        this.channelService
            .update(this.course?.id!, channel.id!, { [propertyName]: updateValue })
            .pipe(
                map((res: HttpResponse<ChannelDTO>) => res.body),
                takeUntil(this.ngUnsubscribe),
            )
            .subscribe({
                next: (updatedChannel: ChannelDTO) => {
                    channel[propertyName] = updatedChannel[propertyName];
                    this.onChangePerformed();
                },
                error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
            });
    }
}
