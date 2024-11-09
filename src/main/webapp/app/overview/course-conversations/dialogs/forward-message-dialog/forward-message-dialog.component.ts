import { Component, ElementRef, Input, OnInit, ViewChild } from '@angular/core';
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
    selectedChannels: ChannelDTO[] = [];
    selectedChats: OneToOneChatWithName[] = [];
    searchTerm: string = '';
    isInputFocused: boolean = false;

    @ViewChild('searchInput') searchInput!: ElementRef;

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
    get shouldShowPlaceholder(): boolean {
        return this.selectedChannels.length === 0 && this.selectedChats.length === 0;
    }

    filterItems(event: Event): void {
        this.searchTerm = (event.target as HTMLInputElement).value.toLowerCase();

        this.filteredChannels = this.channels.filter((channel) => channel.name?.toLowerCase().includes(this.searchTerm));
        this.filteredChats = this.chats
            .map((chat) => {
                const otherUser = chat.members?.find((user) => !user.isRequestingUser);
                return {
                    ...chat,
                    otherUserName: otherUser?.name || '',
                };
            })
            .filter((chat) => chat.otherUserName.toLowerCase().includes(this.searchTerm));
    }

    toggleChannelSelection(channel: ChannelDTO): void {
        const index = this.selectedChannels.findIndex((c) => c.id === channel.id);
        if (index > -1) {
            this.selectedChannels.splice(index, 1);
        } else {
            this.selectedChannels.push(channel);
        }
        this.searchTerm = '';
        this.filteredChannels = this.channels;
        this.focusInput();
    }

    toggleChatSelection(chat: OneToOneChatWithName): void {
        const index = this.selectedChats.findIndex((c) => c.id === chat.id);
        if (index > -1) {
            this.selectedChats.splice(index, 1);
        } else {
            this.selectedChats.push(chat);
        }
        this.searchTerm = '';
        this.filteredChats = this.chats.map((chat) => {
            const otherUser = chat.members?.find((user) => !user.isRequestingUser);
            return {
                ...chat,
                otherUserName: otherUser?.name || '',
            };
        });
        this.focusInput();
    }

    removeSelectedChannel(channel: ChannelDTO): void {
        // Kanalı seçimden kaldırır
        const index = this.selectedChannels.findIndex((c) => c.id === channel.id);
        if (index > -1) {
            this.selectedChannels.splice(index, 1);
        }
        this.focusInput();
    }

    removeSelectedChat(chat: OneToOneChatWithName): void {
        // Sohbeti seçimden kaldırır
        const index = this.selectedChats.findIndex((c) => c.id === chat.id);
        if (index > -1) {
            this.selectedChats.splice(index, 1);
        }
        this.focusInput();
    }

    send(): void {
        const selectedItems = {
            channels: this.selectedChannels,
            chats: this.selectedChats,
        };
        this.activeModal.close(selectedItems);
    }

    hasSelections(): boolean {
        return this.selectedChannels.length > 0 || this.selectedChats.length > 0;
    }

    isChannelSelected(channel: ChannelDTO): boolean {
        return this.selectedChannels.some((c) => c.id === channel.id);
    }

    isChatSelected(chat: OneToOneChatWithName): boolean {
        return this.selectedChats.some((c) => c.id === chat.id);
    }

    onInputFocus(): void {
        this.isInputFocused = true;
    }

    onInputBlur(): void {
        this.isInputFocused = false;
    }

    focusInput(): void {
        this.searchInput.nativeElement.focus();
    }
}
