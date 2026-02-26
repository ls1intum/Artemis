import { ChangeDetectionStrategy, Component, computed, inject, input, output, viewChild } from '@angular/core';
import { DatePipe, NgClass } from '@angular/common';
import { IrisSessionDTO } from 'app/iris/shared/entities/iris-session-dto.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faEllipsisVertical, faPlus, faTrash } from '@fortawesome/free-solid-svg-icons';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { Menu, MenuModule } from 'primeng/menu';
import { MenuItem } from 'primeng/api';
import { NEW_CHAT_TITLES } from 'app/iris/overview/shared/iris-session.utils';

@Component({
    selector: 'jhi-chat-history-item',
    templateUrl: './chat-history-item.component.html',
    styleUrls: ['./chat-history-item.component.scss'],
    standalone: true,
    imports: [DatePipe, NgClass, FaIconComponent, ArtemisTranslatePipe, MenuModule],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ChatHistoryItemComponent {
    private readonly translateService = inject(TranslateService);

    session = input.required<IrisSessionDTO>();
    active = input<boolean>(false);
    readonly isNewChat = computed(() => {
        const title = this.session().title?.trim().toLowerCase();
        if (!title) {
            return false;
        }
        return NEW_CHAT_TITLES.has(title);
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
}
