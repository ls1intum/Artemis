import { moduleMetadata } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';

import { TumUiButtonComponent } from '../button/tum-ui-button.component';
import { TumUiCardComponent } from '../card/tum-ui-card.component';
import { TumUiChipComponent } from '../chip/tum-ui-chip.component';
import { TumUiMessageComponent, TumUiMessageSeverity } from '../message/tum-ui-message.component';
import { TumUiPanelComponent } from '../panel/tum-ui-panel.component';
import { TumUiStatusDotComponent, TumUiStatusDotState } from '../status-dot/tum-ui-status-dot.component';
import { TumUiStepComponent, TumUiStepState } from './tum-ui-step.component';
import { TumUiStepperComponent } from './tum-ui-stepper.component';

interface AgentRunStage {
    label: string;
    state: TumUiStepState;
    stateLabel: string;
    /** Live detail line rendered under the stage; the running stage uses it to report what it is doing right now. */
    detail?: string;
}

interface AgentRunArtifact {
    name: string;
    meta: string;
}

interface AgentRunOutcome {
    severity: TumUiMessageSeverity;
    text: string;
}

interface AgentRunStoryArgs {
    title: string;
    meta: readonly string[];
    status: TumUiStatusDotState;
    statusLabel: string;
    elapsed: string;
    cancellable: boolean;
    stages: readonly AgentRunStage[];
    artifacts: readonly AgentRunArtifact[];
    artifactsEmptyText: string;
    outcome?: AgentRunOutcome;
    primaryAction?: string;
    secondaryAction?: string;
}

const stageNames = ['Prepare workspace', 'Design', 'Build and test', 'Review and repair', 'Save'];

const stateWord: Record<TumUiStepState, string> = {
    pending: 'Pending',
    current: 'Running',
    complete: 'Complete',
    failed: 'Failed',
    skipped: 'Skipped',
};

function stages(...states: TumUiStepState[]): AgentRunStage[] {
    return stageNames.map((label, index) => ({ label, state: states[index], stateLabel: stateWord[states[index]] }));
}

function withDetail(list: AgentRunStage[], label: string, detail: string): AgentRunStage[] {
    return list.map((stage) => (stage.label === label ? { ...stage, detail } : stage));
}

const artifacts: AgentRunArtifact[] = [
    { name: 'Problem statement', meta: '4.2 kB' },
    { name: 'Reference solution', meta: '11 files' },
    { name: 'Test suite', meta: '18 cases' },
];

/**
 * Composition of the package's building blocks into the screen a long-running job needs: a run header with its
 * status, the stage ladder, a live detail line, an artifacts panel, and a terminal outcome block.
 *
 * This page adds no new component and no new styling contract. It exists so the states a reviewer has to sign off —
 * queued, running, repairing, and each way the run can end — can be compared side by side in both themes.
 */
const meta = {
    title: 'Patterns/Agent Run',
    decorators: [
        moduleMetadata({
            imports: [
                TumUiButtonComponent,
                TumUiCardComponent,
                TumUiChipComponent,
                TumUiMessageComponent,
                TumUiPanelComponent,
                TumUiStatusDotComponent,
                TumUiStepComponent,
                TumUiStepperComponent,
            ],
        }),
    ],
    args: {
        title: 'Generate exercise from outline',
        meta: ['Programming', 'Java', 'Started 14:02'],
        status: 'running',
        statusLabel: 'Running',
        elapsed: '01:12',
        cancellable: true,
        stages: withDetail(stages('complete', 'current', 'pending', 'pending', 'pending'), 'Design', 'Drafting the task description and the grading criteria.'),
        artifacts: [],
        artifactsEmptyText: 'Artifacts appear here as stages finish.',
    },
    parameters: {
        layout: 'padded',
    },
    render: (args) => ({
        props: { ...args },
        template: `
            <tum-ui-card style="display: block; width: min(46rem, 100%);">
                <div
                    tumUiCardHeader
                    style="display: flex; flex-wrap: wrap; align-items: flex-start; justify-content: space-between; gap: 1rem; padding: 1.25rem 1.25rem 0;"
                >
                    <div style="display: flex; min-width: 0; flex-direction: column; gap: 0.5rem;">
                        <h2 style="margin: 0; font-size: var(--tumaet-ui-font-size-lg); line-height: var(--tumaet-ui-line-height-lg); font-weight: 600;">{{ title }}</h2>
                        <div style="display: flex; flex-wrap: wrap; gap: 0.5rem;">
                            @for (chip of meta; track chip) {
                                <tum-ui-chip size="small" [label]="chip" />
                            }
                        </div>
                    </div>
                    <div style="display: flex; flex-wrap: wrap; align-items: center; gap: 1rem;">
                        <tum-ui-status-dot [state]="status" [label]="statusLabel" [live]="true" />
                        <span style="color: var(--tumaet-ui-muted-color); font-variant-numeric: tabular-nums;">{{ elapsed }}</span>
                        @if (cancellable) {
                            <tum-ui-button severity="secondary" variant="outlined" size="small">Cancel</tum-ui-button>
                        }
                    </div>
                </div>

                <div style="display: flex; flex-direction: column; gap: 1.25rem;">
                    <tum-ui-stepper ariaLabel="Run stages">
                        @for (stage of stages; track stage.label) {
                            <tum-ui-step [state]="stage.state" [label]="stage.label" [stateLabel]="stage.stateLabel">
                                @if (stage.detail) {
                                    {{ stage.detail }}
                                }
                            </tum-ui-step>
                        }
                    </tum-ui-stepper>

                    <tum-ui-panel header="Artifacts" [toggleable]="true">
                        @if (artifacts.length) {
                            <ul style="display: flex; flex-direction: column; gap: 0.5rem; margin: 0; padding: 0; list-style: none;">
                                @for (artifact of artifacts; track artifact.name) {
                                    <li style="display: flex; justify-content: space-between; gap: 1rem;">
                                        <span>{{ artifact.name }}</span>
                                        <span style="color: var(--tumaet-ui-muted-color);">{{ artifact.meta }}</span>
                                    </li>
                                }
                            </ul>
                        } @else {
                            <p style="margin: 0; color: var(--tumaet-ui-muted-color);">{{ artifactsEmptyText }}</p>
                        }
                    </tum-ui-panel>

                    @if (outcome) {
                        <tum-ui-message [severity]="outcome.severity" [text]="outcome.text" />
                    }
                </div>

                @if (primaryAction || secondaryAction) {
                    <div tumUiCardFooter style="display: flex; justify-content: flex-end; gap: 0.5rem;">
                        @if (secondaryAction) {
                            <tum-ui-button severity="secondary" variant="text">{{ secondaryAction }}</tum-ui-button>
                        }
                        @if (primaryAction) {
                            <tum-ui-button>{{ primaryAction }}</tum-ui-button>
                        }
                    </div>
                }
            </tum-ui-card>
        `,
    }),
} satisfies Meta<AgentRunStoryArgs>;

export default meta;

type Story = StoryObj<AgentRunStoryArgs>;

/** Accepted but not started: nothing claims progress, and the only action is to take the run back. */
export const Queued: Story = {
    args: {
        status: 'queued',
        statusLabel: 'Queued',
        elapsed: '00:00',
        stages: stages('pending', 'pending', 'pending', 'pending', 'pending'),
    },
};

/** The one running stage owns the spinner, the live detail line, and `aria-current`. */
export const Running: Story = {};

/** Repair is a stage, not a separate mode: the ladder keeps its shape and the detail line carries the attempt count. */
export const RepairLoop: Story = {
    args: {
        elapsed: '03:47',
        stages: withDetail(stages('complete', 'complete', 'complete', 'current', 'pending'), 'Review and repair', 'Attempt 2 of 3 — re-running the two failing test cases.'),
        artifacts,
    },
};

export const Succeeded: Story = {
    args: {
        status: 'success',
        statusLabel: 'Succeeded',
        elapsed: '05:21',
        cancellable: false,
        stages: stages('complete', 'complete', 'complete', 'complete', 'complete'),
        artifacts,
        outcome: {
            severity: 'success',
            text: 'The run finished. All artifacts were saved.',
        },
        primaryAction: 'Open result',
        secondaryAction: 'Run again',
    },
};

/** Finished, but a person has to decide before the result can be used — a success dot would overstate it. */
export const NeedsReview: Story = {
    args: {
        status: 'warning',
        statusLabel: 'Needs review',
        elapsed: '04:58',
        cancellable: false,
        stages: withDetail(stages('complete', 'complete', 'complete', 'complete', 'complete'), 'Review and repair', 'Two checks could not be resolved automatically.'),
        artifacts,
        outcome: {
            severity: 'warn',
            text: 'Two checks need a decision before this result can be used.',
        },
        primaryAction: 'Review findings',
        secondaryAction: 'Discard run',
    },
};

/** A partial save is not a success: the ladder is complete, and the outcome block names what is missing. */
export const PartiallySaved: Story = {
    args: {
        status: 'warning',
        statusLabel: 'Partially saved',
        elapsed: '06:03',
        cancellable: false,
        stages: withDetail(stages('complete', 'complete', 'complete', 'complete', 'complete'), 'Save', '3 of 5 artifacts stored.'),
        artifacts,
        outcome: {
            severity: 'warn',
            text: '3 of 5 artifacts were saved. The remaining 2 were rejected and are still only in this run.',
        },
        primaryAction: 'Retry saving',
        secondaryAction: 'Download artifacts',
    },
};

/** The failing stage keeps its place, so the ladder shows how far the run got before it stopped. */
export const Failed: Story = {
    args: {
        status: 'error',
        statusLabel: 'Failed',
        elapsed: '02:36',
        cancellable: false,
        stages: withDetail(stages('complete', 'complete', 'failed', 'pending', 'pending'), 'Build and test', 'The build did not produce a runnable artifact.'),
        artifacts: [artifacts[0]],
        outcome: {
            severity: 'error',
            text: 'The run stopped in "Build and test". Nothing after that stage ran.',
        },
        primaryAction: 'Retry run',
        secondaryAction: 'View log',
    },
};

/** Cancelled stages are skipped, not failed, and never spin. Work already finished stays visible. */
export const Cancelled: Story = {
    args: {
        status: 'neutral',
        statusLabel: 'Cancelled',
        elapsed: '01:44',
        cancellable: false,
        stages: stages('complete', 'complete', 'skipped', 'skipped', 'skipped'),
        artifacts: [artifacts[0]],
        outcome: {
            severity: 'secondary',
            text: 'Cancelled after 1:44. Artifacts from the finished stages are kept.',
        },
        primaryAction: 'Start a new run',
    },
};

/** Progress could not be read. The screen says so instead of showing a stale ladder as if it were live. */
export const StatusUnavailable: Story = {
    args: {
        status: 'neutral',
        statusLabel: 'Status unavailable',
        elapsed: '—',
        cancellable: false,
        stages: stageNames.map((label) => ({ label, state: 'pending', stateLabel: 'Unknown' })),
        artifacts: [],
        artifactsEmptyText: 'Artifacts cannot be listed while the status is unavailable.',
        outcome: {
            severity: 'secondary',
            text: 'The run may still be going. Its progress could not be read — reload to try again.',
        },
        primaryAction: 'Reload status',
    },
};
