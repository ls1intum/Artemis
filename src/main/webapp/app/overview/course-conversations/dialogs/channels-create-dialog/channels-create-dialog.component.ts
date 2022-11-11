import { Component, Input } from '@angular/core';
import { ChannelFormData, ChannelType } from 'app/overview/course-conversations/dialogs/channels-create-dialog/channel-form/channel-form.component';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Course } from 'app/entities/course.model';
import { Channel } from 'app/entities/metis/conversation/channel.model';

@Component({
    selector: 'jhi-channels-create-dialog',
    templateUrl: './channels-create-dialog.component.html',
})
export class ChannelsCreateDialogComponent {
    @Input()
    course: Course;

    isInitialized = false;

    initialize() {
        if (!this.course) {
            console.error('Error: Dialog not fully configured');
        } else {
            this.isInitialized = true;
        }
    }

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

        this.activeModal.close(this.channelToCreate);
    }
}
