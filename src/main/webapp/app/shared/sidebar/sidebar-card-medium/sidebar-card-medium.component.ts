import { Component, EventEmitter, Input, Output } from '@angular/core';
import { DifficultyLevel } from 'app/entities/exercise.model';
import { SidebarCardElement, SidebarTypes } from 'app/types/sidebar';
import { Subscription } from 'rxjs';
import { SidebarEventService } from '../sidebar-event.service';
import { ActivatedRoute, Router } from '@angular/router';
import { Location } from '@angular/common';

@Component({
    selector: 'jhi-medium-sidebar-card',
    templateUrl: './sidebar-card-medium.component.html',
    styleUrls: ['./sidebar-card-medium.component.scss'],
})
export class SidebarCardMediumComponent {
    DifficultyLevel = DifficultyLevel;
    @Input({ required: true }) sidebarItem: SidebarCardElement;
    @Input() sidebarType?: SidebarTypes;
    @Input() itemSelected?: boolean;
    @Output() pageChange = new EventEmitter<number>();

    isSelected: boolean = false;

    paramSubscription?: Subscription;
    noItemSelected: boolean = false;

    constructor(
        private sidebarEventService: SidebarEventService,
        private router: Router,
        private route: ActivatedRoute,
        private location: Location,
    ) {}

    async emitStoreAndRefresh(itemId: number | string) {
        this.sidebarEventService.emitSidebarCardEvent(itemId);
        await this.refreshChildComponent();
    }

    emitPageChangeForExam() {
        this.pageChange.emit();
    }

    async refreshChildComponent(): Promise<void> {
        await this.router.navigate(['../'], { skipLocationChange: true, relativeTo: this.route.firstChild });
        if (this.sidebarItem.routerLink) {
            console.log('routerLink', this.sidebarItem.routerLink);
            await this.router.navigate(['./', this.sidebarItem.routerLink], { relativeTo: this.route.firstChild, replaceUrl: true });
        } else {
            await this.router.navigate(['./' + this.sidebarItem?.id], { relativeTo: this.route });
        }
        // this.router.navigate(['../'], { skipLocationChange: true, relativeTo: this.route.firstChild }).then(() => {
        //     this.itemSelected
        //         ? this.router.navigate(['./' + this.sidebarItem?.id], { relativeTo: this.route })
        //         : this.router.navigate([this.location.path(), this.sidebarItem?.id], { replaceUrl: true });
        // });
    }
}
