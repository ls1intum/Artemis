import { moduleMetadata } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';

import { TumUiButtonComponent } from '../lib/button/tum-ui-button.component';
import { TumUiCardComponent } from '../lib/card/tum-ui-card.component';
import { TumUiChipComponent } from '../lib/chip/tum-ui-chip.component';
import { TumUiMessageComponent, TumUiMessageSeverity } from '../lib/message/tum-ui-message.component';
import { TumUiPanelComponent } from '../lib/panel/tum-ui-panel.component';
import { TumUiStatusDotComponent, TumUiStatusDotState } from '../lib/status-dot/tum-ui-status-dot.component';
import { TumUiStepComponent, TumUiStepState } from '../lib/stepper/tum-ui-step.component';
import { TumUiStepperComponent } from '../lib/stepper/tum-ui-stepper.component';

interface RunStage {
    label: string;
    state: TumUiStepState;
    /** Overrides the package state word when the domain has a better one; the ladder falls back to the package word. */
    stateLabel?: string;
    /** Live detail line rendered under the stage; the running stage uses it to report what it is doing right now. */
    detail?: string;
}

interface RunArtifact {
    name: string;
    meta: string;
}

interface RunOutcome {
    severity: TumUiMessageSeverity;
    text: string;
}

interface RunStatusStoryArgs {
    title: string;
    tags: readonly string[];
    status: TumUiStatusDotState;
    statusLabel: string;
    elapsed: string;
    cancellable: boolean;
    stages: readonly RunStage[];
    artifacts: readonly RunArtifact[];
    artifactsEmptyText: string;
    outcome?: RunOutcome;
    primaryAction?: string;
    secondaryAction?: string;
}

const stageNames = ['Prepare workspace', 'Plan', 'Build and test', 'Review and repair', 'Publish'];

function stages(...states: TumUiStepState[]): RunStage[] {
    return stageNames.map((label, index) => ({ label, state: states[index] }));
}

function withDetail(list: RunStage[], label: string, detail: string): RunStage[] {
    return list.map((stage) => (stage.label === label ? { ...stage, detail } : stage));
}

const artifacts: RunArtifact[] = [
    { name: 'Summary', meta: '4.2 kB' },
    { name: 'Generated files', meta: '11 files' },
    { name: 'Check report', meta: '18 cases' },
];

/**
 * Composition of the package's building blocks into the screen a long-running job needs: a run header with its
 * status, the stage ladder, a live detail line, an artifacts panel, and a terminal outcome block.
 *
 * This page adds no new component and no new styling contract. It exists so the states a reviewer has to sign off —
 * queued, running, repairing, and each way the run can end — can be compared side by side in both themes. Everything
 * a story arranges is a `.tum-ui-story-*` class in the Storybook theme, so no story invents component styling.
 */
const meta = {
    title: 'Patterns/Run status',
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
        title: 'Nightly data import',
        tags: ['Batch', 'High priority', 'Started 14:02'],
        status: 'running',
        statusLabel: 'Running',
        elapsed: '01:12',
        cancellable: true,
        stages: withDetail(stages('complete', 'current', 'pending', 'pending', 'pending'), 'Plan', 'Working out the order of the remaining stages.'),
        artifacts: [],
        artifactsEmptyText: 'Artifacts appear here as stages finish.',
    },
    parameters: {
        layout: 'padded',
    },
    render: (args) => ({
        props: { ...args },
        template: `
            <tum-ui-card class="tum-ui-story-run-card">
                <div tumUiCardHeader class="tum-ui-story-run-header">
                    <div class="tum-ui-story-run-identity">
                        <h2 class="tum-ui-story-run-title">{{ title }}</h2>
                        <div class="tum-ui-story-run-tags">
                            @for (tag of tags; track tag) {
                                <tum-ui-chip size="small" [label]="tag" />
                            }
                        </div>
                    </div>
                    <div class="tum-ui-story-run-state">
                        <tum-ui-status-dot [state]="status" [label]="statusLabel" [live]="true" />
                        <span class="tum-ui-story-run-elapsed">{{ elapsed }}</span>
                        @if (cancellable) {
                            <tum-ui-button severity="secondary" variant="outlined" size="small">Cancel</tum-ui-button>
                        }
                    </div>
                </div>

                <div class="tum-ui-story-run-body">
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
                            <ul class="tum-ui-story-run-artifacts">
                                @for (artifact of artifacts; track artifact.name) {
                                    <li class="tum-ui-story-run-artifact">
                                        <span>{{ artifact.name }}</span>
                                        <span class="tum-ui-story-run-artifact-meta">{{ artifact.meta }}</span>
                                    </li>
                                }
                            </ul>
                        } @else {
                            <p class="tum-ui-story-run-note">{{ artifactsEmptyText }}</p>
                        }
                    </tum-ui-panel>

                    @if (outcome) {
                        <tum-ui-message [severity]="outcome.severity" [text]="outcome.text" />
                    }
                </div>

                @if (primaryAction || secondaryAction) {
                    <div tumUiCardFooter class="tum-ui-story-run-footer">
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
} satisfies Meta<RunStatusStoryArgs>;

export default meta;

type Story = StoryObj<RunStatusStoryArgs>;

/** Accepted but not started: the status dot is a ring, nothing claims progress, and the only action is to take the run back. */
export const Queued: Story = {
    args: {
        status: 'queued',
        statusLabel: 'Queued',
        elapsed: '00:00',
        // Nothing has started, so a start time on the header would contradict the state the ladder is showing.
        tags: ['Batch', 'High priority', 'Queued 14:02'],
        stages: stages('pending', 'pending', 'pending', 'pending', 'pending'),
    },
};

/** The one running stage owns the spinner, the live detail line, and `aria-current`; the blue rail stops there. */
export const Running: Story = {};

/** Repair is a stage, not a separate mode: the ladder keeps its shape and the detail line carries the attempt count. */
export const RepairLoop: Story = {
    args: {
        elapsed: '03:47',
        stages: withDetail(stages('complete', 'complete', 'complete', 'current', 'pending'), 'Review and repair', 'Attempt 2 of 3 — re-running the two failing checks.'),
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
        stages: withDetail(stages('complete', 'complete', 'complete', 'complete', 'complete'), 'Publish', '3 of 5 artifacts stored.'),
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

/**
 * Progress could not be read. Every signal says "we cannot tell you" rather than "nothing is happening": the status
 * dot is a broken ring, the whole ladder is dashed and muted, nothing spins anywhere, the elapsed time is a dash, and
 * the outcome block explains the gap and offers the one action that can close it.
 */
export const StatusUnavailable: Story = {
    args: {
        status: 'unknown',
        statusLabel: 'Status unavailable',
        elapsed: '—',
        cancellable: false,
        // `pending` rather than `skipped`: the skip glyph would claim these stages did not run, and the truth is
        // that nothing could be read about them at all. An empty ladder makes no claim; the dot and the message do.
        stages: stageNames.map((label): RunStage => ({ label, state: 'pending', stateLabel: 'Unknown' })),
        artifacts: [],
        artifactsEmptyText: 'Artifacts cannot be listed while the status is unavailable.',
        outcome: {
            severity: 'secondary',
            text: 'The run may still be going. Its progress could not be read, so nothing below is live.',
        },
        primaryAction: 'Retry',
    },
};
