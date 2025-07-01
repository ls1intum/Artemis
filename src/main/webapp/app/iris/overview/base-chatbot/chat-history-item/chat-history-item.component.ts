import { Component, input, output } from '@angular/core';
import { DatePipe, NgClass } from '@angular/common';
import { IrisSessionDTO } from 'app/iris/shared/entities/iris-session-dto.model';

@Component({
    selector: 'jhi-chat-history-item',
    templateUrl: './chat-history-item.component.html',
    styleUrls: ['./chat-history-item.component.scss'],
    standalone: true,
    imports: [DatePipe, NgClass],
})
export class ChatHistoryItemComponent {
    session = input<IrisSessionDTO>();
    active = input<boolean>(false);
    sessionClicked = output<IrisSessionDTO>();

    onItemClick(): void {
        this.sessionClicked.emit(this.session()!);
    }
}
