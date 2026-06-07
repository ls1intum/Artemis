import { Component, inject, input, output } from '@angular/core';
import { DifficultyLevel } from 'app/exercise/shared/entities/exercise/exercise.model';
import { SidebarEventService } from '../service/sidebar-event.service';
import { ActivatedRoute, Router, RouterLink, RouterLinkActive } from '@angular/router';
import { Location, NgClass } from '@angular/common';
import { SidebarCardItemComponent } from '../sidebar-card-item/sidebar-card-item.component';
import { ConversationOptionsComponent } from '../conversation-options/conversation-options.component';
import { SidebarCardElement, SidebarTypes } from 'app/foundation/types/sidebar';

@Component({
    selector: 'jhi-small-sidebar-card',
    templateUrl: './sidebar-card-small.component.html',
    styleUrls: ['./sidebar-card-small.component.scss'],
    imports: [NgClass, RouterLink, RouterLinkActive, SidebarCardItemComponent, ConversationOptionsComponent],
})
export class SidebarCardSmallComponent {
    private sidebarEventService = inject(SidebarEventService);
    private router = inject(Router);
    private route = inject(ActivatedRoute);
    private location = inject(Location);

    DifficultyLevel = DifficultyLevel;
    readonly onUpdateSidebar = output<void>();
    readonly sidebarItem = input.required<SidebarCardElement>();
    readonly sidebarType = input<SidebarTypes>();
    readonly itemSelected = input<boolean>();
    /** Key used for grouping or categorizing sidebar items */
    readonly groupKey = input<string>();

    emitStoreAndRefresh(itemId: number | string) {
        this.sidebarEventService.emitSidebarCardEvent(itemId);
        if (this.sidebarType() !== 'conversation') {
            this.refreshChildComponent();
        }
    }

    refreshChildComponent(): void {
        this.router.navigate(['../'], { skipLocationChange: true, relativeTo: this.route.firstChild }).then(() => {
            if (this.itemSelected()) {
                this.router.navigate(['./' + this.sidebarItem()?.id], { relativeTo: this.route });
            } else {
                this.router.navigate([this.location.path(), this.sidebarItem()?.id], { replaceUrl: true });
            }
        });
    }
}
