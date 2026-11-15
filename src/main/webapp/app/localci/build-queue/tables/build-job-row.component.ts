import { ChangeDetectionStrategy, Component, ViewEncapsulation, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { SlicePipe } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faClock, faExclamationCircle, faSpinner, faTimes } from '@fortawesome/free-solid-svg-icons';
import { TumAetUiButtonComponent, TumAetUiTooltipDirective } from '@tumaet/ui-angular';
import { BuildJob } from 'app/localci/shared/entities/build-job.model';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ArtemisDurationFromSecondsPipe } from 'app/foundation/pipes/artemis-duration-from-seconds.pipe';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { BuildJobsTableDirective } from './build-jobs-table.directive';
import dayjs from 'dayjs/esm';
import { buildJobWaitingTime } from './build-job-waiting-time.util';

@Component({
    selector: 'tr[jhi-build-job-row]',
    templateUrl: './build-job-row.component.html',
    styleUrls: ['./build-jobs-table.scss'],
    encapsulation: ViewEncapsulation.None,
    imports: [RouterLink, SlicePipe, FaIconComponent, TumAetUiButtonComponent, TumAetUiTooltipDirective, ArtemisDatePipe, ArtemisDurationFromSecondsPipe, ArtemisTranslatePipe],
    changeDetection: ChangeDetectionStrategy.OnPush,
    host: {
        class: 'clickable-row',
        tabindex: '0',
        '(click)': 'onJobClick(buildJob().id)',
        '(keydown.enter)': '$event.target === $event.currentTarget && onJobClick(buildJob().id)',
        '(keydown.space)': '$event.preventDefault(); $event.target === $event.currentTarget && onJobClick(buildJob().id)',
    },
})
export class BuildJobRowComponent extends BuildJobsTableDirective {
    readonly buildJob = input.required<BuildJob>();
    readonly running = input(false);
    readonly displayTime = input<dayjs.Dayjs>();
    readonly isAdminView = input(false);
    readonly calculateWaitingTime = buildJobWaitingTime;
    protected readonly faClock = faClock;
    protected readonly faExclamationCircle = faExclamationCircle;
    protected readonly faSpinner = faSpinner;
    protected readonly faTimes = faTimes;
}
