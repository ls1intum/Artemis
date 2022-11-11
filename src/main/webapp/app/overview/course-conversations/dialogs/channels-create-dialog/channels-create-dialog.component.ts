import { Component, Input } from '@angular/core';
import { ChannelFormData, ChannelType } from 'app/overview/course-conversations/dialogs/channels-create-dialog/channel-form/channel-form.component';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Course } from 'app/entities/course.model';
import { Channel } from 'app/entities/metis/conversation/channel.model';
import { MetisConversationService } from 'app/shared/metis/metis-conversation.service';

@Component({
    selector: 'jhi-channels-create-dialog',
    templateUrl: './channels-create-dialog.component.html',
})
export class ChannelsCreateDialogComponent {
    @Input()
    set metisConversationService(metisConversationService: MetisConversationService) {
        this._metisConversationService = metisConversationService;
        this.course = this._metisConversationService.course!;
    }
    _metisConversationService: MetisConversationService;

    course: Course;
    channelToCreate: Channel = new Channel();
    isPublicChannel = true;
    constructor(private activeModal: NgbActiveModal) {}

    onChannelTypeChanged($event: ChannelType) {
        this.isPublicChannel = $event === 'PUBLIC';
    }

    onFormSubmitted($event: ChannelFormData) {
        this.createChannel($event);
    }

    clear() {
        this.activeModal.dismiss();
    }

    createChannel(formData: ChannelFormData) {
        const { name, description, isPublic } = formData;

        this.channelToCreate.name = name ? name.trim() : undefined;
        this.channelToCreate.description = description ? description.trim() : undefined;
        this.channelToCreate.isPublic = isPublic;
        this.channelToCreate.course = this.course;

        this._metisConversationService.createNewConversation(this.channelToCreate).subscribe({
            complete: () => {
                this.activeModal.close();
            },
        });
    }
}
