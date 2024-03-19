import { Component, HostListener, Input, OnChanges, OnDestroy, OnInit } from '@angular/core';
import { Exercise } from 'app/entities/exercise.model';
import { faChevronRight, faFilter, faMagnifyingGlass } from '@fortawesome/free-solid-svg-icons';
import { ActivatedRoute, Params } from '@angular/router';
import { Subscription } from 'rxjs';
import { getExerciseDueDate } from 'app/exercises/shared/exercise/exercise.utils';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ProfileService } from '../layouts/profiles/profile.service';

@Component({
    selector: 'jhi-sidebar',
    templateUrl: './sidebar.component.html',
    styleUrls: ['./sidebar.component.scss'],
})
export class SidebarComponent implements OnDestroy, OnChanges, OnInit {
    @Input() entityData?: Exercise[];
    @Input() searchFieldEnabled: boolean = true;
    // If true accordions are used
    @Input() groupByCategory: boolean = true;

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
        this.profileService.getProfileInfo()?.subscribe((profileInfo) => {
            this.isProduction = profileInfo.inProduction;
            this.isTestServer = profileInfo.testServer ?? false;
        });
    }

    ngOnChanges() {
        this.entityData = this.sortEntityData();
        this.paramSubscription = this.route.params?.subscribe((params) => {
            this.routeParams = params;
        });
    }

    studentParticipation(exercise: Exercise): StudentParticipation | undefined {
        return exercise.studentParticipations?.length ? exercise.studentParticipations[0] : undefined;
    }

    sortEntityData(): Exercise[] | undefined {
        const sortedEntityData = this.entityData?.sort((a, b) => {
            const dueDateA = getExerciseDueDate(a, this.studentParticipation(a))?.valueOf() ?? 0;
            const dueDateB = getExerciseDueDate(b, this.studentParticipation(b))?.valueOf() ?? 0;

            if (dueDateB - dueDateA !== 0) {
                return dueDateB - dueDateA;
            }
            // If Due Date is identical or undefined sort by title
            return a.title && b.title ? a.title.localeCompare(b.title) : 0;
        });

        return sortedEntityData;
    }

    setSearchValue(searchValue: string) {
        this.searchValue = searchValue;
    }

    @HostListener('window:keydown.Control.x', ['$event'])
    onKeyDownControlX(event: KeyboardEvent) {
        event.preventDefault();
        this.isCollapsed = !this.isCollapsed;
    }

    ngOnDestroy() {
        this.paramSubscription?.unsubscribe();
    }
}
