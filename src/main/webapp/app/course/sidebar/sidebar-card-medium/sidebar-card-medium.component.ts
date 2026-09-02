import { Component, computed, inject, input, output } from '@angular/core';
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
    /** Id of the entity the detail route currently shows, set by {@link SidebarCardDirective}. */
    readonly activeItemId = input<number>();

    /**
     * True when this card stands for a variant group rather than a single exercise. A group has no difficulty of its
     * own, so the left stripe that would carry the difficulty colour marks it as a group instead.
     */
    protected readonly isVariantGroup = computed<boolean>(() => !!this.sidebarItem().groupedItems?.length);

    /**
     * True when the open detail page belongs to one of this card's grouped members. A variant group is a single card
     * whose members have no card of their own, so `routerLinkActive` cannot highlight it while a variant is open.
     */
    protected readonly containsActiveVariant = computed<boolean>(() => {
        const activeItemId = this.activeItemId();
        return activeItemId !== undefined && !!this.sidebarItem().groupedItems?.some((member) => member.id === activeItemId);
    });

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
        void this.router.navigate(['../'], { skipLocationChange: true, relativeTo: this.route.firstChild }).then(() => {
            void this.router.navigate(pathSegments, { relativeTo: this.route });
        });
    }

    onExamCardClicked() {
        this.pageChange.emit(this.sidebarItem().id);
    }
}
