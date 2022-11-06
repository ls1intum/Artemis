import { Component, Input, OnInit } from '@angular/core';
import { AlertService } from 'app/core/util/alert.service';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { AddUsersFormData } from 'app/overview/course-conversations/conversation-add-users-dialog/add-users-form/add-users-form.component';
import { User } from 'app/core/user/user.model';
import { Course } from 'app/entities/course.model';
import { ConversationDto, MAX_MEMBERS_IN_DIRECT_CONVERSATION } from 'app/entities/metis/conversation/conversation.model';
import { finalize } from 'rxjs/operators';
import { HttpErrorResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { ChannelService } from 'app/shared/metis/conversations/channel.service';
import { getAsChannelDto, isChannelDto } from 'app/entities/metis/conversation/channel.model';
import { getAsGroupChatDto } from 'app/entities/metis/conversation/groupChat.model';
import { getConversationName } from 'app/shared/metis/conversations/conversation.util';
import { MetisConversationService } from 'app/shared/metis/metis-conversation.service';

@Component({
    selector: 'jhi-conversation-add-users-dialog',
    templateUrl: './conversation-add-users-dialog.component.html',
})
export class ConversationAddUsersDialogComponent implements OnInit {
    readonly MAX_MEMBERS_IN_DIRECT_CONVERSATION = MAX_MEMBERS_IN_DIRECT_CONVERSATION;

    @Input()
    set metisConversationService(metisConversationService: MetisConversationService) {
        this._metisConversationService = metisConversationService;
        this.course = this._metisConversationService.course!;
        this._metisConversationService.activeConversation$.subscribe((conversation: ConversationDto) => {
            this.activeConversation = conversation;
        });
    }
    _metisConversationService: MetisConversationService;
    course: Course;

    activeConversation: ConversationDto;

    constructor(private alertService: AlertService, private activeModal: NgbActiveModal, public channelService: ChannelService) {}

    ngOnInit(): void {}

    onFormSubmitted($event: AddUsersFormData) {
        this.addUsers($event.selectedUsers ?? []);
    }

    clear() {
        this.activeModal.dismiss();
    }

    getAsChannel = getAsChannelDto;
    getAsGroupChat = getAsGroupChatDto;

    getConversationName = getConversationName;

    private addUsers(usersToAdd: User[]) {
        const userLogins = usersToAdd.map((user) => user.login!);

        if (isChannelDto(this.activeConversation)) {
            this.channelService.registerUsersToChannel(this.course.id!, this.activeConversation.id!, userLogins).subscribe({
                next: () => {
                    this._metisConversationService.forceRefresh().subscribe({
                        complete: () => {
                            this.activeModal.close();
                        },
                    });
                },
                error: (errorResponse: HttpErrorResponse) => {
                    onError(this.alertService, errorResponse);
                    this.activeModal.close();
                },
            });
        }
    }
}
