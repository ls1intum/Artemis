import { ChangeDetectionStrategy, Component, Signal, computed, inject, input, output, viewChild } from '@angular/core';
import { getCurrentLocaleSignal } from 'app/shared/util/global.utils';
import { DatePipe, NgClass } from '@angular/common';
import { RouterLink } from '@angular/router';
import { IrisSessionDTO } from 'app/iris/shared/entities/iris-session-dto.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faChalkboardUser, faEllipsisVertical, faKeyboard, faPlus, faTrash } from '@fortawesome/free-solid-svg-icons';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ChatServiceMode } from 'app/iris/overview/services/iris-chat.service';
import { Menu, MenuModule } from 'primeng/menu';
import { MenuItem } from 'primeng/api';
import { TooltipModule } from 'primeng/tooltip';

@Component({
    selector: 'jhi-chat-history-item',
    templateUrl: './chat-history-item.component.html',
    styleUrl: './chat-history-item.component.scss',
    standalone: true,
    imports: [DatePipe, NgClass, FaIconComponent, ArtemisTranslatePipe, MenuModule, RouterLink, TooltipModule],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ChatHistoryItemComponent {
    private readonly translateService = inject(TranslateService);
    private readonly currentLocale = getCurrentLocaleSignal(this.translateService);

    private static readonly NEW_CHAT_TITLES = new Set(['new chat', 'neuer chat']);

    session = input.required<IrisSessionDTO>();
    active = input<boolean>(false);
    icon: Signal<IconProp | undefined> = computed(() => this.computeIcon(this.session()));
    tooltipText: Signal<string | undefined> = computed(() => {
        this.currentLocale();
        return this.computeTooltipText(this.session());
    });
    ariaLabelText: Signal<string | undefined> = computed(() => this.tooltipText());
    entityRoute: Signal<string | undefined> = computed(() => this.computeEntityRoute(this.session()));
    readonly isNewChat = computed(() => {
        const title = this.session().title?.trim().toLowerCase();
        if (!title) {
            return false;
        }
        return ChatHistoryItemComponent.NEW_CHAT_TITLES.has(title);
    });
    readonly faPlus = faPlus;
    readonly faEllipsisVertical = faEllipsisVertical;
    readonly faTrash = faTrash;
    sessionClicked = output<IrisSessionDTO>();
    deleteSession = output<IrisSessionDTO>();

    readonly contextMenu = viewChild<Menu>('menu');

    // Built fresh on each toggle so the label always reflects the current language,
    // and the PrimeNG popup Menu (appendTo="body") receives up-to-date items.
    menuItems: MenuItem[] = [];

    onItemClick(): void {
        this.sessionClicked.emit(this.session());
    }

    onEntityIconClick(event: Event): void {
        event.stopPropagation();
    }

    onMenuToggle(event: Event): void {
        event.stopPropagation();
        this.menuItems = [
            {
                label: this.translateService.instant('artemisApp.iris.chatHistory.deleteSession'),
                styleClass: 'danger',
                command: () => this.onDeleteClick(),
            },
        ];
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

    private computeTooltipText(session: IrisSessionDTO): string | undefined {
        let key: string | undefined;
        switch (session.chatMode) {
            case ChatServiceMode.PROGRAMMING_EXERCISE:
                key = 'artemisApp.iris.chatHistory.relatedEntityTooltip.programmingExercise';
                break;
            case ChatServiceMode.LECTURE:
                key = 'artemisApp.iris.chatHistory.relatedEntityTooltip.lecture';
                break;
            default:
                return undefined;
        }
        return this.translateService.instant(key, { name: session.entityName });
    }

    private computeEntityRoute(session: IrisSessionDTO): string | undefined {
        if (!session.chatMode || !session.entityId) {
            return undefined;
        }
        switch (session.chatMode) {
            case ChatServiceMode.PROGRAMMING_EXERCISE:
                return `../exercises/${session.entityId}`;
            case ChatServiceMode.LECTURE:
                return `../lectures/${session.entityId}`;
            default:
                return undefined;
        }
    }
}
