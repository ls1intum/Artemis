import { ChangeDetectionStrategy, Component, Signal, computed, input, output } from '@angular/core';
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
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ChatHistoryItemComponent {
    session = input.required<IrisSessionDTO>();
    active = input<boolean>(false);
    icon: Signal<IconProp | undefined> = computed(() => this.computeIcon(this.session()));
    tooltipKey: Signal<string | undefined> = computed(() => this.computeTooltipKey(this.session()));
    relatedEntityName: Signal<string | undefined> = computed(() => this.session().entityName);
    sessionClicked = output<IrisSessionDTO>();

    onItemClick(): void {
        this.sessionClicked.emit(this.session());
    }

    private computeIcon(session: IrisSessionDTO): IconProp | undefined {
        switch (session.chatMode) {
            case ChatServiceMode.PROGRAMMING_EXERCISE:
                return faKeyboard;
            case ChatServiceMode.LECTURE:
                return faChalkboardUser;
            default:
                return undefined;
        }
    }

    private computeTooltipKey(session: IrisSessionDTO): string | undefined {
        switch (session.chatMode) {
            case ChatServiceMode.PROGRAMMING_EXERCISE:
                return 'artemisApp.iris.chatHistory.relatedEntityTooltip.programmingExercise';
            case ChatServiceMode.LECTURE:
                return 'artemisApp.iris.chatHistory.relatedEntityTooltip.lecture';
            default:
                return undefined;
        }
    }
}
