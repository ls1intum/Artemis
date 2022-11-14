import { Component, DoCheck, Input, IterableDiffer, IterableDiffers, OnInit } from '@angular/core';
import { faInfo } from '@fortawesome/free-solid-svg-icons';
import { TutorialGroupFreePeriod } from 'app/entities/tutorial-group/tutorial-group-free-day.model';
import { SortService } from 'app/shared/service/sort.service';
import dayjs from 'dayjs/esm';

@Component({
    selector: 'jhi-tutorial-group-free-days-overview',
    templateUrl: './tutorial-group-free-days-overview.component.html',
    styleUrls: ['./tutorial-group-free-days-overview.component.scss'],
})
export class TutorialGroupFreeDaysOverviewComponent implements OnInit, DoCheck {
    constructor(private sortService: SortService, private iterableDiffers: IterableDiffers) {}

    @Input()
    tutorialGroupFreeDays: TutorialGroupFreePeriod[] = [];

    @Input()
    timeZone?: string = undefined;

    public faInfo = faInfo;

    public isInThePast(tutorialGroupFreeDay: TutorialGroupFreePeriod): boolean {
        return tutorialGroupFreeDay.start!.isBefore(this.getCurrentDate());
    }

    private diff: IterableDiffer<TutorialGroupFreePeriod>;

    public getCurrentDate(): dayjs.Dayjs {
        return dayjs();
    }

    public ngOnInit(): void {
        this.diff = this.iterableDiffers.find(this.tutorialGroupFreeDays).create();
        this.sort();
    }

    public ngDoCheck(): void {
        const changes = this.diff.diff(this.tutorialGroupFreeDays);
        if (changes) {
            this.sort();
        }
    }

    private sort() {
        this.sortService.sortByProperty(this.tutorialGroupFreeDays, 'start', false);
    }
}
