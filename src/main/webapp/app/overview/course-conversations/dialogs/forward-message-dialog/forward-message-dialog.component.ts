import { Component, Input, OnInit } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { ChannelDTO } from 'app/entities/metis/conversation/channel.model';
import { OneToOneChatDTO } from 'app/entities/metis/conversation/one-to-one-chat.model';

interface OneToOneChatWithName extends OneToOneChatDTO {
    otherUserName: string;
}

@Component({
    selector: 'jhi-forward-message-dialog',
    templateUrl: './forward-message-dialog.component.html',
    styleUrls: ['./forward-message-dialog.component.scss'],
})
export class ForwardMessageDialogComponent implements OnInit {
    @Input() channels: ChannelDTO[] = [];
    @Input() chats: OneToOneChatDTO[] = [];
    filteredChannels: ChannelDTO[] = [];
    filteredChats: OneToOneChatWithName[] = [];

    constructor(public activeModal: NgbActiveModal) {}

    ngOnInit(): void {
        this.filteredChannels = this.channels;
        this.filteredChats = this.chats.map((chat) => {
            const otherUser = chat.members?.find((user) => !user.isRequestingUser);
            return {
                ...chat,
                otherUserName: otherUser?.name || '',
            };
        });
    }

    filterItems(event: Event): void {
        const searchTerm = (event.target as HTMLInputElement).value.toLowerCase();

        this.filteredChannels = this.channels.filter((channel) => channel.name?.toLowerCase().includes(searchTerm));
        this.filteredChats = this.chats
            .map((chat) => {
                const otherUser = chat.members?.find((user) => !user.isRequestingUser);
                return {
                    ...chat,
                    otherUserName: otherUser?.name || '',
                };
            })
            .filter((chat) => chat.otherUserName.toLowerCase().includes(searchTerm));
    }

    selectChannel(channel: ChannelDTO): void {
        this.activeModal.close(channel);
    }

    selectChat(chat: OneToOneChatDTO): void {
        this.activeModal.close(chat);
    }
}
