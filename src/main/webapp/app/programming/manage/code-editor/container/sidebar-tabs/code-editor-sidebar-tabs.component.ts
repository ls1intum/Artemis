import { ChangeDetectionStrategy, Component, input, output, signal } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faChevronLeft, faComments, faEye, faEyeSlash, faFilter, faFolder, faListAlt } from '@fortawesome/free-solid-svg-icons';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

type SidebarTab = 'FILES' | 'PROBLEM_STATEMENT' | 'COMMENTS';

@Component({
    selector: 'jhi-code-editor-sidebar-tabs',
    templateUrl: './code-editor-sidebar-tabs.component.html',
    styleUrls: ['./code-editor-sidebar-tabs.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    standalone: true,
    imports: [ArtemisTranslatePipe, FaIconComponent],
})
export class CodeEditorSidebarTabsComponent {
    readonly isCollapsed = input<boolean>(false);
    readonly toggleCollapsed = output<void>();
    readonly leftSidebarTab = signal<SidebarTab>('FILES');
    readonly commentsVisible = signal<boolean>(true);
    readonly faFolder = faFolder;
    readonly faListAlt = faListAlt;
    readonly faComments = faComments;
    readonly faChevronLeft = faChevronLeft;
    readonly faFilter = faFilter;
    readonly faEye = faEye;
    readonly faEyeSlash = faEyeSlash;

    selectTab(tab: SidebarTab) {
        const isSameTab = this.leftSidebarTab() === tab;
        if (this.isCollapsed()) {
            this.toggleCollapsed.emit();
            this.leftSidebarTab.set(tab);
            return;
        }
        if (isSameTab) {
            this.toggleCollapsed.emit();
            return;
        }
        this.leftSidebarTab.set(tab);
    }

    onToggleCollapseClick(event: MouseEvent) {
        event.stopPropagation();
        this.toggleCollapsed.emit();
    }

    toggleCommentsVisibility(event: MouseEvent) {
        event.stopPropagation();
        const next = !this.commentsVisible();
        this.commentsVisible.set(next);
        this.commentsVisibilityChange.emit(next);
    }

    readonly commentsVisibilityChange = output<boolean>();
}
