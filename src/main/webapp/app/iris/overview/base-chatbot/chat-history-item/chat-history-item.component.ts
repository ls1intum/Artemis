import { Component, EventEmitter, Input, Output } from '@angular/core';
import { IrisSession } from 'app/iris/shared/entities/iris-session.model';
import { DatePipe } from '@angular/common';
import { IrisSessionDto } from 'app/iris/shared/entities/iris-session-dto.model';

@Component({
    selector: 'jhi-chat-history-item',
    templateUrl: './chat-history-item.component.html',
    styleUrls: ['./chat-history-item.component.scss'],
    standalone: true,
    imports: [DatePipe],
})
export class ChatHistoryItemComponent {
    @Input() session: IrisSessionDto;
    @Output() sessionClicked = new EventEmitter<IrisSession>();

    onItemClick(): void {
        this.sessionClicked.emit(this.session);
    }
}
