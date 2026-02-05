import { ChangeDetectionStrategy, Component, computed, input, model, output } from '@angular/core';
import { FinishedBuildJob } from 'app/buildagent/shared/entities/build-job.model';
import { faCircleCheck, faExclamationCircle, faExclamationTriangle, faSort } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgClass, SlicePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { SortDirective } from 'app/shared/sort/directive/sort.directive';
import { SortByDirective } from 'app/shared/sort/directive/sort-by.directive';
import { ResultComponent } from 'app/exercise/result/result.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { BuildAgentInformation } from 'app/buildagent/shared/entities/build-agent-information.model';
import { createAddressToAgentInfoMap, getAgentInfoByAddress } from 'app/buildagent/shared/build-agent-address.utils';

@Component({
    selector: 'jhi-finished-jobs-table',
    templateUrl: './finished-jobs-table.component.html',
    styleUrl: './finished-jobs-table.component.scss',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [TranslateDirective, FaIconComponent, NgClass, RouterLink, SortDirective, SortByDirective, ResultComponent, ArtemisDatePipe, SlicePipe],
})
export class FinishedJobsTableComponent {
    // Inputs
    buildJobs = input<FinishedBuildJob[]>([]);
    showCourseId = input<boolean>(false);
    isAdminView = input<boolean>(false);
    /** Course ID for building navigation routes (undefined for admin view) */
    courseId = input<number | undefined>(undefined);
    /** List of online build agents for address-to-name mapping */
    buildAgents = input<BuildAgentInformation[]>([]);

    /**
     * Computed mapping from build agent host to agent info (name and displayName).
     * Uses host-only matching to handle Hazelcast ephemeral ports.
     */
    addressToAgentInfoMap = computed(() => createAddressToAgentInfoMap(this.buildAgents()));

    // Two-way bindings
    predicate = model<string>('buildCompletionDate');
    ascending = model<boolean>(false);

    // Outputs
    jobClick = output<string>();
    viewLogs = output<string>();
    sortChange = output<void>();

    // Font Awesome icons
    readonly faSort = faSort;
    readonly faCircleCheck = faCircleCheck;
    readonly faExclamationCircle = faExclamationCircle;
    readonly faExclamationTriangle = faExclamationTriangle;

    /**
     * Handles row click to navigate to job details
     * @param jobId The ID of the build job
     */
    onJobClick(jobId: string | undefined): void {
        if (jobId) {
            this.jobClick.emit(jobId);
        }
    }

    /**
     * Handles build logs link click
     * @param event The click event
     * @param jobId The ID of the build job
     */
    onViewLogs(event: Event, jobId: string | undefined): void {
        event.stopPropagation();
        if (jobId) {
            this.viewLogs.emit(jobId);
        }
    }

    /**
     * Handles sort change event
     */
    onSortChange(): void {
        this.sortChange.emit();
    }

    /**
     * Builds the router link array for navigating to job details.
     * Uses course-specific route when courseId is provided, otherwise admin route.
     * @param jobId The ID of the build job
     */
    getJobDetailRoute(jobId: string | undefined): string[] {
        if (!jobId) {
            return [];
        }
        const courseId = this.courseId();
        if (courseId) {
            return ['/course-management', String(courseId), 'build-overview', jobId, 'job-details'];
        }
        return ['/admin', 'build-overview', jobId, 'job-details'];
    }

    /**
     * Gets the display name for a build agent.
     * Returns the agent's displayName if online, otherwise returns the address.
     */
    getAgentDisplayName(address: string | undefined): string {
        const agentInfo = getAgentInfoByAddress(address, this.addressToAgentInfoMap());
        return agentInfo?.displayName ?? address ?? '';
    }

    /**
     * Gets the query parameter value for navigating to build agent details.
     * Returns the agent's short name (stable identifier) if available.
     */
    getAgentLinkParam(address: string | undefined): string {
        const agentInfo = getAgentInfoByAddress(address, this.addressToAgentInfoMap());
        return agentInfo?.name ?? address ?? '';
    }
}
