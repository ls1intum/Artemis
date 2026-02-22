import { ChangeDetectionStrategy, Component, Signal, computed, inject, input, output, viewChild } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { DatePipe, NgClass } from '@angular/common';
import { IrisSessionDTO } from 'app/iris/shared/entities/iris-session-dto.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faChalkboardUser, faEllipsisVertical, faKeyboard, faPlus } from '@fortawesome/free-solid-svg-icons';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ChatServiceMode } from 'app/iris/overview/services/iris-chat.service';
import { Menu, MenuModule } from 'primeng/menu';
import { MenuItem } from 'primeng/api';

@Component({
    selector: 'jhi-chat-history-item',
    templateUrl: './chat-history-item.component.html',
    styleUrls: ['./chat-history-item.component.scss'],
    standalone: true,
    imports: [DatePipe, NgClass, FaIconComponent, NgbTooltipModule, ArtemisTranslatePipe, MenuModule],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ChatHistoryItemComponent {
    private readonly translateService = inject(TranslateService);
    // Known "new chat" titles from all languages (server-side: messages*.properties, client-side: iris.json).
    // Must match the values in src/main/resources/i18n/messages*.properties (iris.chat.session.newChatTitle)
    // and src/main/webapp/i18n/*/iris.json (artemisApp.iris.chatHistory.newChat).
    private static readonly NEW_CHAT_TITLES = new Set(['new chat', 'neuer chat']);
    private readonly deleteLabel = toSignal(this.translateService.stream('artemisApp.iris.chatHistory.deleteSession'), { initialValue: '' });

    session = input.required<IrisSessionDTO>();
    active = input<boolean>(false);
    icon: Signal<IconProp | undefined> = computed(() => this.computeIcon(this.session()));
    tooltipKey: Signal<string | undefined> = computed(() => this.computeTooltipKey(this.session()));
    relatedEntityName: Signal<string | undefined> = computed(() => this.session().entityName);
    readonly isNewChat = computed(() => {
        const title = this.session().title?.trim().toLowerCase();
        if (!title) {
            return false;
        }
        return ChatHistoryItemComponent.NEW_CHAT_TITLES.has(title);
    });
    readonly faPlus = faPlus;
    readonly faEllipsisVertical = faEllipsisVertical;
    sessionClicked = output<IrisSessionDTO>();
    deleteSession = output<IrisSessionDTO>();

    readonly contextMenu = viewChild<Menu>('menu');

    readonly menuItems = computed<MenuItem[]>(() => [
        {
            label: this.deleteLabel(),
            icon: 'pi pi-trash',
            styleClass: 'danger',
            command: () => this.onDeleteClick(),
        },
    ]);

    onItemClick(): void {
        this.sessionClicked.emit(this.session());
    }

    onMenuToggle(event: Event): void {
        event.stopPropagation();
        this.contextMenu()?.toggle(event);
    }

    onDeleteClick(): void {
        this.deleteSession.emit(this.session());
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
