import { Component, input, output } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faChevronRight } from '@fortawesome/free-solid-svg-icons';
import { TumAetUiButtonDirective, TumAetUiTooltipDirective } from '@tumaet/ui-angular';
import { facSidebar } from 'app/foundation/icons/icons';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-course-sidebar-toggle-button',
    imports: [FaIconComponent, TumAetUiButtonDirective, TumAetUiTooltipDirective, ArtemisTranslatePipe],
    templateUrl: './course-sidebar-toggle-button.component.html',
    styleUrl: './course-sidebar-toggle-button.component.scss',
})
export class CourseSidebarToggleButtonComponent {
    readonly isCollapsed = input<boolean>(false);
    readonly isCommunicationModule = input<boolean>(false);
    readonly toggleSidebar = output<void>();

    protected readonly facSidebar = facSidebar;
    protected readonly faChevronRight = faChevronRight;
}
