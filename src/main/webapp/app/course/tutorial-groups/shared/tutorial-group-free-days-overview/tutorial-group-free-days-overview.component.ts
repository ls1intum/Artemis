import { ChangeDetectionStrategy, Component, DoCheck, Input, IterableDiffer, IterableDiffers, OnInit, inject } from '@angular/core';
import { TutorialGroupFreePeriod } from 'app/entities/tutorial-group/tutorial-group-free-day.model';
import { SortService } from 'app/shared/service/sort.service';
import dayjs from 'dayjs/esm';
import { SidePanelComponent } from 'app/shared/side-panel/side-panel.component';
import { NgClass } from '@angular/common';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgbPopover } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-tutorial-group-free-days-overview',
    templateUrl: './tutorial-group-free-days-overview.component.html',
    styleUrls: ['./tutorial-group-free-days-overview.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [SidePanelComponent, NgClass, TranslateDirective, NgbPopover, ArtemisDatePipe, ArtemisTranslatePipe],
})
export class TutorialGroupFreeDaysOverviewComponent implements OnInit, DoCheck {
    private sortService = inject(SortService);
    private iterableDiffers = inject(IterableDiffers);

    @Input()
    tutorialGroupFreeDays: TutorialGroupFreePeriod[] = [];

    @Input()
    timeZone?: string = undefined;

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
