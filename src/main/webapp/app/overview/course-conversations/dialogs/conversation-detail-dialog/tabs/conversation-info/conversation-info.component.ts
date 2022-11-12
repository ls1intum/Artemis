import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { ConversationDto } from 'app/entities/metis/conversation/conversation.model';
import { ChannelDTO, getAsChannelDto } from 'app/entities/metis/conversation/channel.model';
import { getUserLabel } from 'app/overview/course-conversations/other/conversation.util';
import { ChannelService } from 'app/shared/metis/conversations/channel.service';
import { Course } from 'app/entities/course.model';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { get } from 'lodash-es';
import {
    ChannelUpdatePropertyDialogComponent,
    PropertyTranslationKeys,
} from 'app/overview/course-conversations/dialogs/channel-update-property-dialog/channel-update-property-dialog.component';
import { from, map } from 'rxjs';
import { onError } from 'app/shared/util/global.utils';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { AlertService } from 'app/core/util/alert.service';
import { channelRegex } from 'app/overview/course-conversations/dialogs/channels-create-dialog/channel-form/channel-form.component';

@Component({
    selector: 'jhi-conversation-info',
    templateUrl: './conversation-info.component.html',
    styleUrls: ['./conversation-info.component.scss'],
})
export class ConversationInfoComponent implements OnInit {
    getAsChannel = getAsChannelDto;
    getUserLabel = getUserLabel;

    @Input()
    activeConversation: ConversationDto;

    @Input()
    course: Course;

    @Output()
    channelLeave: EventEmitter<void> = new EventEmitter<void>();

    @Output()
    changesPerformed = new EventEmitter<void>();

    constructor(private channelService: ChannelService, private modalService: NgbModal, private alertService: AlertService) {}

    ngOnInit(): void {}

    leaveChannel($event: MouseEvent) {
        $event.stopPropagation();
        this.channelService.deregisterUsersFromChannel(this.course?.id!, this.activeConversation.id!).subscribe(() => {
            this.channelLeave.emit();
        });
    }

    onChangePerformed() {
        this.changesPerformed.emit();
    }

    openEditNameModal(event: MouseEvent) {
        const asChannel = getAsChannelDto(this.activeConversation);
        if (!asChannel) {
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
        this.openEditPropertyDialog(asChannel, 'name', 20, true, channelRegex, keys);
    }

    openEditTopicModal(event: MouseEvent) {
        const asChannel = getAsChannelDto(this.activeConversation);
        if (!asChannel) {
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
        this.openEditPropertyDialog(asChannel, 'topic', 250, false, undefined, keys);
    }

    openDescriptionTopicModal(event: MouseEvent) {
        const asChannel = getAsChannelDto(this.activeConversation);
        if (!asChannel) {
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
        this.openEditPropertyDialog(asChannel, 'description', 250, false, undefined, keys);
    }

    private openEditPropertyDialog(
        channel: ChannelDTO,
        propertyName: string,
        maxLength: number,
        isRequired: boolean,
        regexPattern: RegExp | undefined,
        translationKeys: PropertyTranslationKeys,
    ) {
        const modalRef: NgbModalRef = this.modalService.open(ChannelUpdatePropertyDialogComponent, {
            size: 'lg',
            scrollable: false,
            backdrop: 'static',
        });
        modalRef.componentInstance.propertyName = propertyName;
        modalRef.componentInstance.maxPropertyLength = maxLength;
        modalRef.componentInstance.translationKeys = translationKeys;
        modalRef.componentInstance.isRequired = isRequired;
        modalRef.componentInstance.regexPattern = regexPattern;

        if (get(channel, propertyName) && get(channel, propertyName).length > 0) {
            modalRef.componentInstance.initialValue = get(channel, propertyName);
        }
        modalRef.componentInstance.initialize();
        from(modalRef.result).subscribe((newValue: string) => {
            let updateValue = null;
            if (newValue && newValue.trim().length > 0) {
                updateValue = newValue.trim();
            } else {
                updateValue = '';
            }

            this.channelService
                .update(this.course?.id!, channel.id!, { [propertyName]: updateValue })
                .pipe(map((res: HttpResponse<ChannelDTO>) => res.body))
                .subscribe({
                    next: (updatedChannel: ChannelDTO) => {
                        channel[propertyName] = updatedChannel[propertyName];
                        this.onChangePerformed();
                    },
                    error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
                });
        });
    }
}
