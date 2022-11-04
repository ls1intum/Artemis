import { Component, Input, OnInit } from '@angular/core';
import { ChannelFormData, ChannelType } from 'app/overview/course-messages/channels/channel-form/channel-form.component';
import { ChannelService } from 'app/shared/metis/conversations/channel.service';
import { AlertService } from 'app/core/util/alert.service';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Course } from 'app/entities/course.model';
import { Channel } from 'app/entities/metis/conversation/channel.model';

@Component({
    selector: 'jhi-channels-create-dialog',
    templateUrl: './channels-create-dialog.component.html',
    styleUrls: ['./channels-create-dialog.component.scss'],
})
export class ChannelsCreateDialogComponent implements OnInit {
    @Input()
    course: Course;

    channelToCreate: Channel = new Channel();
    isPublicChannel = true;
    isLoading = false;

    constructor(private channelService: ChannelService, private alertService: AlertService, private activeModal: NgbActiveModal) {}

    ngOnInit(): void {
        this.channelToCreate = new Channel();
    }

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
