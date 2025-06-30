import { Component, EventEmitter, Input, Output } from '@angular/core';
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
    @Input() session: IrisSessionDTO;
    @Input() active: boolean = false;
    @Output() sessionClicked = new EventEmitter<IrisSessionDTO>();

    onItemClick(): void {
        this.sessionClicked.emit(this.session);
    }
}
