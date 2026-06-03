import { Component, inject, input, output } from '@angular/core';
import { DifficultyLevel } from 'app/exercise/shared/entities/exercise/exercise.model';
import { SidebarEventService } from '../service/sidebar-event.service';
import { ActivatedRoute, Router, RouterLink, RouterLinkActive } from '@angular/router';
import { NgClass } from '@angular/common';
import { SidebarCardItemComponent } from '../sidebar-card-item/sidebar-card-item.component';
import { SidebarCardElement, SidebarTypes } from 'app/foundation/types/sidebar';

@Component({
    selector: 'jhi-medium-sidebar-card',
    templateUrl: './sidebar-card-medium.component.html',
    styleUrls: ['./sidebar-card-medium.component.scss'],
    imports: [NgClass, SidebarCardItemComponent, RouterLink, RouterLinkActive],
})
export class SidebarCardMediumComponent {
    private sidebarEventService = inject(SidebarEventService);
    private router = inject(Router);
    private route = inject(ActivatedRoute);

    protected readonly DifficultyLevel = DifficultyLevel;

    readonly sidebarItem = input.required<SidebarCardElement>();
    readonly sidebarType = input<SidebarTypes>();
    readonly itemSelected = input<boolean>();
    readonly pageChange = output<string | number>();
    /** Key used for grouping or categorizing sidebar items */
    readonly groupKey = input<string>();

    onNonExamCardClicked() {
        this.storeTargetComponentSubRoute();
        if (this.itemSelected()) {
            this.refreshChildComponent();
        }
    }

    storeTargetComponentSubRoute() {
        const targetComponentSubRoute = this.sidebarItem().targetComponentSubRoute;
        const sidebarItemId = this.sidebarItem().id;
        const targetComponentRoute = targetComponentSubRoute ? targetComponentSubRoute + '/' + sidebarItemId : sidebarItemId;
        this.sidebarEventService.emitSidebarCardEvent(targetComponentRoute);
    }

    refreshChildComponent(): void {
        const targetComponentSubRoute = this.sidebarItem()?.targetComponentSubRoute;
        const itemId = this.sidebarItem()?.id;
        const pathSegments = targetComponentSubRoute ? ['./', targetComponentSubRoute, itemId] : ['./', itemId];
        this.router.navigate(['../'], { skipLocationChange: true, relativeTo: this.route.firstChild }).then(() => {
            this.router.navigate(pathSegments, { relativeTo: this.route });
        });
    }

    onExamCardClicked() {
        this.pageChange.emit(this.sidebarItem().id);
    }
}
