import { Component, Input } from '@angular/core';
import { ChannelFormData, ChannelType } from 'app/overview/course-conversations/dialogs/channels-create-dialog/channel-form/channel-form.component';
import { Course } from 'app/entities/course.model';
import { ChannelDTO } from 'app/entities/metis/conversation/channel.model';
import { AbstractDialogComponent } from 'app/overview/course-conversations/dialogs/abstract-dialog.component';

@Component({
    selector: 'jhi-channels-create-dialog',
    templateUrl: './channels-create-dialog.component.html',
})
export class ChannelsCreateDialogComponent extends AbstractDialogComponent {
    @Input()
    course: Course;

    initialize() {
        super.initialize(['course']);
    }

    channelToCreate: ChannelDTO = new ChannelDTO();
    isPublicChannel = true;
    isAnnouncementChannel = false;
    onChannelTypeChanged($event: ChannelType) {
        this.isPublicChannel = $event === 'PUBLIC';
    }

    onIsAnnouncementChannelChanged($event: boolean) {
        this.isAnnouncementChannel = $event;
    }

    onFormSubmitted($event: ChannelFormData) {
        this.createChannel($event);
    }

    createChannel(formData: ChannelFormData) {
        const { name, description, isPublic, isAnnouncementChannel } = formData;
        this.channelToCreate.name = name ? name.trim() : undefined;
        this.channelToCreate.description = description ? description.trim() : undefined;
        this.channelToCreate.isPublic = isPublic;
        this.channelToCreate.isAnnouncementChannel = isAnnouncementChannel;
        this.close(this.channelToCreate);
    }
}
