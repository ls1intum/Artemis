import { Component, EventEmitter, Input, Output } from '@angular/core';
import { IrisSession } from 'app/iris/shared/entities/iris-session.model';
import { DatePipe, NgClass } from '@angular/common';

@Component({
    selector: 'jhi-chat-history-item',
    templateUrl: './chat-history-item.component.html',
    styleUrls: ['./chat-history-item.component.scss'],
    standalone: true,
    imports: [DatePipe, NgClass],
})
export class ChatHistoryItemComponent {
    @Input() session: IrisSession;
    @Input() active: boolean = false;
    @Output() sessionClicked = new EventEmitter<IrisSession>();

    onItemClick(): void {
        this.sessionClicked.emit(this.session);
    }
}
