import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { NgClass } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faChevronRight } from '@fortawesome/free-solid-svg-icons';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { TooltipModule } from 'primeng/tooltip';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { RouterLink } from '@angular/router';

@Component({
    selector: 'jhi-entity-group-header',
    templateUrl: './entity-group-header.component.html',
    styleUrls: ['./entity-group-header.component.scss'],
    imports: [FaIconComponent, TooltipModule, ArtemisTranslatePipe, RouterLink, NgClass],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EntityGroupHeaderComponent {
    readonly entityName = input.required<string>();
    readonly icon = input.required<IconProp>();
    readonly tooltipKey = input.required<string>();
    readonly entityRoute = input.required<string>();
    readonly expanded = input<boolean>(false);

    readonly toggleExpanded = output<void>();

    protected readonly faChevronRight = faChevronRight;
}
