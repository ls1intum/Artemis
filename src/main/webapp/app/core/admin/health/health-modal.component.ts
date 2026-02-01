import { Component, inject, signal } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { HealthDetails, HealthKey } from 'app/core/admin/health/health.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { KeyValuePipe } from '@angular/common';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { CommonModule } from '@angular/common';

/**
 * Represents a formatted build agent for display in the health modal.
 */
interface FormattedBuildAgent {
    displayName: string;
    name: string;
    memberAddress: string;
    status: string;
    currentJobs: number;
    maxJobs: number;
    runningJobNames: string[];
    gitRevision?: string;
    startDate?: string;
}

interface SimplifiedBuildAgent {
    displayName?: string;
    name?: string;
    memberAddress?: string;
    status?: string;
    currentJobs?: number;
    maxJobs?: number;
    runningJobs?: unknown[];
}

interface LegacyBuildAgent {
    buildAgent?: {
        displayName?: string;
        name?: string;
        memberAddress?: string;
    };
    status?: string;
    numberOfCurrentBuildJobs?: number;
    maxNumberOfConcurrentBuildJobs?: number;
    runningBuildJobs?: Array<{ name?: string; id?: string | number }>;
    buildAgentDetails?: {
        gitRevision?: string;
        startDate?: string;
    };
}

type BuildAgentDetail = SimplifiedBuildAgent | LegacyBuildAgent;

/**
 * Modal component for displaying detailed health information of a specific health indicator.
 */
@Component({
    selector: 'jhi-health-modal',
    templateUrl: './health-modal.component.html',
    imports: [TranslateDirective, KeyValuePipe, ArtemisTranslatePipe, CommonModule],
})
export class HealthModalComponent {
    private readonly activeModal = inject(NgbActiveModal);

    readonly health = signal<{ key: HealthKey; value: HealthDetails } | undefined>(undefined);

    readableValue(value: any): string {
        if (this.health()?.key === 'diskSpace') {
            // Should display storage space in a human-readable unit
            const val = value / 1073741824;
            if (val > 1) {
                return `${val.toFixed(2)} GB`;
            }
            return `${(value / 1048576).toFixed(2)} MB`;
        }

        if (typeof value === 'object') {
            return JSON.stringify(value);
        }
        return String(value);
    }

    /**
     * Checks if a value is a LocalCI build agents array.
     * Supports both the simplified format (name, displayName, status, maxJobs, currentJobs, runningJobs)
     * and the legacy format (buildAgent object, maxNumberOfConcurrentBuildJobs, etc.)
     */
    isBuildAgentsArray(value: unknown, detailKey?: string): value is BuildAgentDetail[] {
        if (!Array.isArray(value)) {
            return false;
        }
        if (detailKey === 'buildAgents') {
            return true;
        }
        if (value.length === 0) {
            return false;
        }
        // Check if the first element has the expected build agent structure
        const first = value[0];
        if (!this.isRecord(first)) {
            return false;
        }
        // Check for simplified format (name, maxJobs) or legacy format (buildAgent, maxNumberOfConcurrentBuildJobs)
        return ('name' in first && 'maxJobs' in first) || ('buildAgent' in first && 'maxNumberOfConcurrentBuildJobs' in first);
    }

    /**
     * Formats a build agents array into a structured, readable format.
     * Handles both simplified format from the health endpoint and legacy format.
     */
    formatBuildAgents(value: unknown, detailKey?: string): FormattedBuildAgent[] {
        if (!this.isBuildAgentsArray(value, detailKey)) {
            return [];
        }
        const agents = value as BuildAgentDetail[];
        return agents.map((agent) => (this.isSimplifiedBuildAgent(agent) ? this.formatSimplifiedBuildAgent(agent) : this.formatLegacyBuildAgent(agent)));
    }

    private formatSimplifiedBuildAgent(agent: SimplifiedBuildAgent): FormattedBuildAgent {
        const name = this.coerceString(agent.name, 'Unknown');
        return {
            displayName: this.coerceString(agent.displayName, name),
            name,
            memberAddress: this.coerceString(agent.memberAddress),
            status: this.coerceString(agent.status, 'UNKNOWN'),
            currentJobs: this.coerceNumber(agent.currentJobs),
            maxJobs: this.coerceNumber(agent.maxJobs),
            runningJobNames: this.normalizeRunningJobs(agent.runningJobs),
        };
    }

    private formatLegacyBuildAgent(agent: LegacyBuildAgent): FormattedBuildAgent {
        const name = this.coerceString(agent.buildAgent?.name, 'Unknown');
        const currentJobs = this.coerceNumber(typeof agent.numberOfCurrentBuildJobs === 'number' ? agent.numberOfCurrentBuildJobs : agent.runningBuildJobs?.length);
        return {
            displayName: this.coerceString(agent.buildAgent?.displayName, name),
            name,
            memberAddress: this.coerceString(agent.buildAgent?.memberAddress),
            status: this.coerceString(agent.status, 'UNKNOWN'),
            currentJobs,
            maxJobs: this.coerceNumber(agent.maxNumberOfConcurrentBuildJobs),
            runningJobNames: (agent.runningBuildJobs ?? []).map((job) => this.coerceString(job.name ?? job.id, 'Unknown')),
            gitRevision: this.coerceOptionalString(agent.buildAgentDetails?.gitRevision),
            startDate: this.coerceOptionalString(agent.buildAgentDetails?.startDate),
        };
    }

    private normalizeRunningJobs(value: unknown): string[] {
        if (!Array.isArray(value)) {
            return [];
        }
        return value.map((job) => this.coerceString(job, 'Unknown'));
    }

    private coerceString(value: unknown, fallback = ''): string {
        if (typeof value === 'string') {
            return value;
        }
        if (typeof value === 'number' || typeof value === 'boolean') {
            return String(value);
        }
        return fallback;
    }

    private coerceOptionalString(value: unknown): string | undefined {
        if (typeof value === 'string') {
            return value;
        }
        if (typeof value === 'number' || typeof value === 'boolean') {
            return String(value);
        }
        return undefined;
    }

    private coerceNumber(value: unknown, fallback = 0): number {
        if (typeof value === 'number' && Number.isFinite(value)) {
            return value;
        }
        return fallback;
    }

    private isRecord(value: unknown): value is Record<string, unknown> {
        return value !== null && typeof value === 'object';
    }

    private isSimplifiedBuildAgent(agent: BuildAgentDetail): agent is SimplifiedBuildAgent {
        return 'maxJobs' in agent || 'currentJobs' in agent || 'runningJobs' in agent;
    }

    /**
     * Returns a CSS class for the build agent status badge.
     */
    getStatusBadgeClass(status: string): string {
        switch (status) {
            case 'ACTIVE':
                return 'bg-success';
            case 'IDLE':
                return 'bg-secondary';
            case 'PAUSED':
            case 'SELF_PAUSED':
                return 'bg-warning';
            default:
                return 'bg-secondary';
        }
    }

    dismiss(): void {
        this.activeModal.dismiss();
    }
}
