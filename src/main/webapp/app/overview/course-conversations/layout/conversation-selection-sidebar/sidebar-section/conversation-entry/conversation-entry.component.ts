import { ChangeDetectorRef, Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { ConversationDto } from 'app/entities/metis/conversation/conversation.model';
import { getAsChannelDto } from 'app/entities/metis/conversation/channel.model';
import { ConversationService } from 'app/shared/metis/conversations/conversation.service';
import { faEllipsis, faMessage, faPeopleGroup } from '@fortawesome/free-solid-svg-icons';
import { debounceTime, distinctUntilChanged, Subject } from 'rxjs';
import { Course } from 'app/entities/course.model';
import { AlertService } from 'app/core/util/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { HttpErrorResponse } from '@angular/common/http';
import { getAsGroupChatDto } from 'app/entities/metis/conversation/group-chat.model';

@Component({
    selector: '[jhi-conversation-entry]',
    templateUrl: './conversation-entry.component.html',
    styleUrls: ['./conversation-entry.component.scss'],
})
export class ConversationEntryComponent implements OnInit {
    hide$ = new Subject<boolean>();
    favorite$ = new Subject<boolean>();

    @Input()
    course: Course;

    @Input()
    conversation: ConversationDto;

    @Input()
    isConversationUnread = false;

    @Input()
    isActive: boolean | undefined = false;

    @Output()
    settingsChanged = new EventEmitter<void>();

    faEllipsis = faEllipsis;
    faMessage = faMessage;
    constructor(public conversationService: ConversationService, private alertService: AlertService) {}

    getAsChannel = getAsChannelDto;
    getAsGroupChat = getAsGroupChatDto;
    getConversationName = this.conversationService.getConversationName;

    onHiddenClicked(event: MouseEvent) {
        event.stopPropagation();
        this.hide$.next(!this.conversation.isHidden);
    }

    onFavoriteClicked($event: MouseEvent) {
        $event.stopPropagation();
        this.favorite$.next(!this.conversation.isFavorite);
    }

    ngOnInit(): void {
        this.hide$.pipe(debounceTime(500), distinctUntilChanged()).subscribe((shouldHide) => {
            this.conversationService.changeHiddenStatus(this.course.id!, this.conversation.id!, shouldHide).subscribe({
                next: () => {
                    this.conversation.isHidden = shouldHide;
                    this.settingsChanged.emit();
                },
                error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
            });
        });
        this.favorite$.pipe(debounceTime(500), distinctUntilChanged()).subscribe((shouldFavorite) => {
            this.conversationService.changeFavoriteStatus(this.course.id!, this.conversation.id!, shouldFavorite).subscribe({
                next: () => {
                    this.conversation.isFavorite = shouldFavorite;
                    this.settingsChanged.emit();
                },
                error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
            });
        });
    }
}
