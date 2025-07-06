import { Component, input, output } from '@angular/core';
import { DatePipe, NgClass } from '@angular/common';
import { IrisSessionDTO } from 'app/iris/shared/entities/iris-session-dto.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faChalkboardUser, faKeyboard } from '@fortawesome/free-solid-svg-icons';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { ChatServiceMode } from 'app/iris/overview/services/iris-chat.service';

@Component({
    selector: 'jhi-chat-history-item',
    templateUrl: './chat-history-item.component.html',
    styleUrls: ['./chat-history-item.component.scss'],
    standalone: true,
    imports: [DatePipe, NgClass, FaIconComponent],
})
export class ChatHistoryItemComponent {
    session = input<IrisSessionDTO>();
    active = input<boolean>(false);
    sessionClicked = output<IrisSessionDTO>();

    onItemClick(): void {
        this.sessionClicked.emit(this.session()!);
    }

    get icon(): IconProp | undefined {
        switch (this.session()?.chatMode) {
            case ChatServiceMode.PROGRAMMING_EXERCISE:
                return faKeyboard;
            case ChatServiceMode.LECTURE:
                return faChalkboardUser;
            default:
                return undefined;
        }
    }
}
