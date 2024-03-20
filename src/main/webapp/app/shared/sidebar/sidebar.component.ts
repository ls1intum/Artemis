import { Component, HostListener, Input, OnChanges, OnDestroy, OnInit } from '@angular/core';
import { faChevronRight, faFilter, faMagnifyingGlass } from '@fortawesome/free-solid-svg-icons';
import { ActivatedRoute, Params } from '@angular/router';
import { Subscription } from 'rxjs';
import { ProfileService } from '../layouts/profiles/profile.service';
import { SidebarData } from 'app/types/sidebar';

@Component({
    selector: 'jhi-sidebar',
    templateUrl: './sidebar.component.html',
    styleUrls: ['./sidebar.component.scss'],
})
export class SidebarComponent implements OnDestroy, OnChanges, OnInit {
    @Input() searchFieldEnabled: boolean = true;
    @Input() sidebarData: SidebarData;

    searchValue = '';
    isCollapsed: boolean = false;

    exerciseId: string;

    paramSubscription?: Subscription;
    routeParams: Params;
    isProduction = true;
    isTestServer = false;

    // icons
    faMagnifyingGlass = faMagnifyingGlass;
    faChevronRight = faChevronRight;
    faFilter = faFilter;

    constructor(
        private route: ActivatedRoute,
        private profileService: ProfileService,
    ) {}

    ngOnInit(): void {
        this.getCollapseStateFromStorage();
        this.profileService.getProfileInfo()?.subscribe((profileInfo) => {
            this.isProduction = profileInfo.inProduction;
            this.isTestServer = profileInfo.testServer ?? false;
        });
    }

    getCollapseStateFromStorage() {
        const storedCollapseState: string | null = localStorage.getItem('sidebar.collapseState.' + this.sidebarData.storageId);
        if (storedCollapseState) this.isCollapsed = JSON.parse(storedCollapseState);
    }
    ngOnChanges() {
        this.paramSubscription = this.route.params?.subscribe((params) => {
            this.routeParams = params;
        });
    }

    setSearchValue(searchValue: string) {
        this.searchValue = searchValue;
    }

    @HostListener('window:keydown.Control.x', ['$event'])
    onKeyDownControlX(event: KeyboardEvent) {
        event.preventDefault();
        this.toggleCollapseState();
    }

    toggleCollapseState() {
        this.isCollapsed = !this.isCollapsed;
        localStorage.setItem('sidebar.collapseState.' + this.sidebarData.storageId, JSON.stringify(this.isCollapsed));
    }
    ngOnDestroy() {
        this.paramSubscription?.unsubscribe();
    }
}
