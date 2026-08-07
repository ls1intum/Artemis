import { ChangeDetectionStrategy, Component, input, model } from '@angular/core';
import { HealthDetails, HealthKey } from 'app/admin/health/health.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { KeyValuePipe } from '@angular/common';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TumUiButtonComponent, TumUiDialogComponent, TumUiTagComponent } from '@tumaet/ui-angular';
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
    reservedGenerationSandboxSlots?: number;
    maxGenerationSandboxSlots?: number;
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
    reservedGenerationSandboxSlots?: number;
    maxGenerationSandboxSlots?: number;
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
    reservedGenerationSandboxSlots?: number;
    maxGenerationSandboxSlots?: number;
    runningBuildJobs?: Array<{ name?: string; id?: string | number }>;
    buildAgentDetails?: {
        gitRevision?: string;
        startDate?: string;
    };
}

type BuildAgentDetail = SimplifiedBuildAgent | LegacyBuildAgent;

@Component({
    selector: 'jhi-health-modal',
    templateUrl: './health-modal.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [TranslateDirective, KeyValuePipe, ArtemisTranslatePipe, TumUiDialogComponent, TumUiTagComponent, TumUiButtonComponent],
})
export class HealthModalComponent {
    readonly visible = model<boolean>(false);

    readonly health = input<{ key: HealthKey; value: HealthDetails } | undefined>(undefined);

    readableValue(value: unknown): string {
        if (this.health()?.key === 'diskSpace') {
            // Should display storage space in a human-readable unit
            const bytes = value as number;
            const val = bytes / 1073741824;
            if (val > 1) {
                return `${val.toFixed(2)} GB`;
            }
            return `${(bytes / 1048576).toFixed(2)} MB`;
        }

        if (typeof value === 'object') {
            return JSON.stringify(value);
        }
        // primitives keep the exact String() output (e.g. 'NaN', 'Infinity', 'undefined'); narrowing positively
        // avoids no-base-to-string (String() is only ever applied to number/boolean/bigint here).
        if (typeof value === 'number' || typeof value === 'boolean' || typeof value === 'bigint') {
            return String(value);
        }
        if (typeof value === 'string') {
            return value;
        }
        return 'undefined';
    }

    isBuildAgentsArray(value: unknown, detailKey?: string): value is BuildAgentDetail[] {
        if (!Array.isArray(value)) {
            return false;
        }
        if (detailKey === 'buildAgents') {
            return value.length === 0 || value.every((entry) => this.isBuildAgentDetail(entry));
        }
        if (value.length === 0) {
            return false;
        }
        return value.every((entry) => this.isBuildAgentDetail(entry));
    }

    formatBuildAgents(value: unknown, detailKey?: string): FormattedBuildAgent[] {
        if (!this.isBuildAgentsArray(value, detailKey)) {
            return [];
        }
        const agents = value;
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
            reservedGenerationSandboxSlots: this.coerceOptionalNumber(agent.reservedGenerationSandboxSlots),
            maxGenerationSandboxSlots: this.coerceOptionalNumber(agent.maxGenerationSandboxSlots),
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
            reservedGenerationSandboxSlots: this.coerceOptionalNumber(agent.reservedGenerationSandboxSlots),
            maxGenerationSandboxSlots: this.coerceOptionalNumber(agent.maxGenerationSandboxSlots),
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

    private coerceOptionalNumber(value: unknown): number | undefined {
        if (typeof value === 'number' && Number.isFinite(value)) {
            return value;
        }
        return undefined;
    }

    private isRecord(value: unknown): value is Record<string, unknown> {
        return value !== null && typeof value === 'object' && !Array.isArray(value);
    }

    private isBuildAgentDetail(value: unknown): value is BuildAgentDetail {
        if (!this.isRecord(value)) {
            return false;
        }
        return this.isSimplifiedBuildAgent(value) || this.isLegacyBuildAgent(value);
    }

    private isSimplifiedBuildAgent(agent: BuildAgentDetail): agent is SimplifiedBuildAgent {
        return 'maxJobs' in agent || 'currentJobs' in agent || 'runningJobs' in agent;
    }

    private isLegacyBuildAgent(agent: BuildAgentDetail): agent is LegacyBuildAgent {
        return 'buildAgent' in agent || 'maxNumberOfConcurrentBuildJobs' in agent || 'runningBuildJobs' in agent;
    }

    getStatusBadgeSeverity(status: string): 'success' | 'secondary' | 'warn' {
        switch (status) {
            case 'ACTIVE':
                return 'success';
            case 'PAUSED':
            case 'SELF_PAUSED':
                return 'warn';
            default:
                return 'secondary';
        }
    }

    hasHyperionSandboxCapacity(agent: FormattedBuildAgent): boolean {
        return agent.reservedGenerationSandboxSlots !== undefined && agent.maxGenerationSandboxSlots !== undefined;
    }

    dismiss(): void {
        this.visible.set(false);
    }
}
