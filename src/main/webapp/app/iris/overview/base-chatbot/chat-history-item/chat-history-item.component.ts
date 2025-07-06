import { Component, input, output } from '@angular/core';
import { DatePipe, NgClass } from '@angular/common';
import { IrisSessionDTO } from 'app/iris/shared/entities/iris-session-dto.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faChalkboardUser, faKeyboard } from '@fortawesome/free-solid-svg-icons';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ChatServiceMode } from 'app/iris/overview/services/iris-chat.service';

@Component({
    selector: 'jhi-chat-history-item',
    templateUrl: './chat-history-item.component.html',
    styleUrls: ['./chat-history-item.component.scss'],
    standalone: true,
    imports: [DatePipe, NgClass, FaIconComponent, NgbTooltipModule, ArtemisTranslatePipe],
})
export class ChatHistoryItemComponent {
    session = input<IrisSessionDTO>();
    active = input<boolean>(false);
    sessionClicked = output<IrisSessionDTO>();

    onItemClick(): void {
        this.sessionClicked.emit(this.session()!);
    }

    get iconAndTooltipKey(): { icon: IconProp; tooltipKey: string } | undefined {
        switch (this.session()?.chatMode) {
            case ChatServiceMode.PROGRAMMING_EXERCISE:
                return { icon: faKeyboard, tooltipKey: 'artemisApp.iris.chatHistory.relatedEntityTooltip.programmingExercise' };
            case ChatServiceMode.LECTURE:
                return { icon: faChalkboardUser, tooltipKey: 'artemisApp.iris.chatHistory.relatedEntityTooltip.lecture' };
            default:
                return undefined;
        }
    }
}
