import { Component, EventEmitter, Input, Output, inject } from '@angular/core';
import { DifficultyLevel } from 'app/exercise/shared/entities/exercise/exercise.model';
import { SidebarEventService } from '../service/sidebar-event.service';
import { ActivatedRoute, Router, RouterLink, RouterLinkActive } from '@angular/router';
import { Location, NgClass } from '@angular/common';
import { SidebarCardItemComponent } from '../sidebar-card-item/sidebar-card-item.component';
import { SidebarCardElement, SidebarTypes } from 'app/shared/types/sidebar';

@Component({
    selector: 'jhi-medium-sidebar-card',
    templateUrl: './sidebar-card-medium.component.html',
    styleUrls: ['./sidebar-card-medium.component.scss'],
    imports: [NgClass, SidebarCardItemComponent, RouterLink, RouterLinkActive],
})
export class SidebarCardMediumComponent {
    private readonly TUTORIAL_LECTURES_ROUTE_PATTERN = /\/courses\/\d+\/tutorial-groups\/tutorial-lectures(\/|$)/;
    private sidebarEventService = inject(SidebarEventService);
    private router = inject(Router);
    private route = inject(ActivatedRoute);
    private location = inject(Location);

    protected readonly DifficultyLevel = DifficultyLevel;

    @Input({ required: true }) sidebarItem: SidebarCardElement;
    @Input() sidebarType?: SidebarTypes;
    @Input() itemSelected?: boolean;
    @Output() pageChange = new EventEmitter<number>();
    /** Key used for grouping or categorizing sidebar items */
    @Input() groupKey?: string;

    emitStoreAndRefresh() {
        const targetComponentSubRoute = this.sidebarItem.targetComponentSubRoute;
        const sidebarItemId = this.sidebarItem.id;
        const targetComponentRoute = targetComponentSubRoute ? targetComponentSubRoute + '/' + sidebarItemId : sidebarItemId;
        this.sidebarEventService.emitSidebarCardEvent(targetComponentRoute);

        const currentUrl = this.router.url;
        const isNotTutorialLecturesRoute = !this.TUTORIAL_LECTURES_ROUTE_PATTERN.test(currentUrl);
        if (isNotTutorialLecturesRoute) {
            //this.refreshChildComponent();
        }
    }

    emitPageChangeForExam() {
        this.pageChange.emit();
    }

    refreshChildComponent(): void {
        const targetComponentSubRoute = this.sidebarItem?.targetComponentSubRoute;
        const itemId = this.sidebarItem?.id;
        this.router.navigate(['../'], { skipLocationChange: true, relativeTo: this.route.firstChild }).then(() => {
            if (this.itemSelected) {
                const pathSegments = targetComponentSubRoute ? ['./', targetComponentSubRoute, itemId] : ['./', itemId];
                this.router.navigate(pathSegments, { relativeTo: this.route });
            } else {
                const pathSegments = targetComponentSubRoute ? [this.location.path(), targetComponentSubRoute, itemId] : [this.location.path(), itemId];
                this.router.navigate(pathSegments, { replaceUrl: true });
            }
        });
    }
}
