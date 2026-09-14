import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { IconDefinition } from '@fortawesome/fontawesome-svg-core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

@Component({
    selector: 'jhi-sidebar-subpage-item',
    imports: [RouterLinkActive, RouterLink, FaIconComponent],
    templateUrl: './sidebar-subpage-item.html',
    styleUrl: './sidebar-subpage-item.scss',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SidebarSubpageItem {
    readonly icon = input.required<IconDefinition>();
    readonly title = input.required<string>();

    readonly subjectId = input<number>();
    readonly subpage = input<string>();
}
