import { moduleMetadata } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';

import { TumUiCardContentComponent, TumUiCardHeaderComponent, TumUiCardTitleComponent } from '../lib/card/tum-ui-card-parts.component';
import { TumUiCardComponent } from '../lib/card/tum-ui-card.component';
import { TumUiMessageComponent, TumUiMessageSeverity } from '../lib/message/tum-ui-message.component';
import { TumUiProgressBarComponent, TumUiProgressBarSeverity } from '../lib/progress-bar/tum-ui-progress-bar.component';
import { TumUiTagComponent, TumUiTagSeverity } from '../lib/tag/tum-ui-tag.component';

/**
 * The ceiling a job is allowed to spend against, and how far into it the job is.
 *
 * `share` and `budget` are pre-rendered strings rather than numbers: the surface that owns the figure owns its
 * locale, its grouping and its unit, and a design-system story must not quietly adopt one.
 */
interface MeterLine {
    /** The consumed figure, on its own, beside its unit. Never welded into a sentence with the other two. */
    used: string;
    share: string;
    budget: string;
    percent: number;
    severity: TumUiProgressBarSeverity;
    /** The word for the threshold that has been crossed. Without it the meter states its level by colour alone. */
    levelLabel: string;
    /**
     * What a screen reader hears instead of a bare "62 percent". The unit, the ceiling and the threshold word are
     * otherwise only in the text beside the bar, which the meter role does not include in its own reading.
     */
    valueText: string;
}

interface MeterFigure {
    label: string;
    value: string;
    /** The provider did not report this for every response, so the count is a floor rather than a total. */
    lowerBound?: boolean;
}

interface MeteringStoryArgs {
    title: string;
    /** The accounting state as a word: a running total, a sealed total and a permanent lower bound look alike otherwise. */
    accountingLabel: string;
    accountingSeverity: TumUiTagSeverity;
    /** Present once the figure has stopped moving, so a number read a week later is not read as live. */
    sealedAt?: string;
    /** Sits above the figure, because a total that could not be closed must be qualified before it is read. */
    notice?: { severity: TumUiMessageSeverity; text: string };
    /** `true` renders the "at least" qualifier in front of the hero figure. */
    lowerBound: boolean;
    /** Whichever unit is actually measured — money when money is known, billable tokens when it is not. */
    heroValue: string;
    heroUnit?: string;
    /** The unit that is *not* the hero, demoted to a sentence at body size. Never a hero-sized word. */
    caption: string;
    /** The consumed-against-ceiling meter. Absent when no ceiling is configured — then there is no proportion to draw. */
    meter?: MeterLine;
    meterLabel: string;
    /** The unit beside the meter's left figure. */
    meterUnit: string;
    meterOfLabel: string;
    meterAriaLabel: string;
    /** Replaces the meter when no ceiling exists: the spend reported on its own, with no bar. */
    uncappedLine?: string;
    figures: readonly MeterFigure[];
}

const figures: MeterFigure[] = [
    { label: 'Requests', value: '48' },
    { label: 'Input', value: '1,204,882' },
    { label: 'Cached input', value: '812,004' },
    { label: 'Output', value: '96,318' },
    { label: 'Billable', value: '1,301,200' },
    { label: 'Longest wait', value: '41 s' },
];

/**
 * Composition of the kit's building blocks into the surface that reports **what a long-running job spent**.
 *
 * The pattern exists because a spend figure is the one number on a run screen that is read once and trusted forever,
 * and almost every way of getting it wrong is invisible in a screenshot. The four rules the states below exercise:
 *
 * 1. **The hero is whichever unit is actually measured.** Money when money is known, the raw consumed unit when it is
 *    not. Never a hero-sized word — "Not priced" in the slot where a number belongs reads as a value.
 * 2. **An unknown is never a zero.** `€0.00` and "we could not price this" are different claims, and only one of them
 *    is about the job.
 * 3. **A total that could not be closed is a floor.** It is prefixed with "at least", tagged `warning`, and explained
 *    in a message — a lower bound presented as a total is the failure this whole surface exists to prevent.
 * 4. **No ceiling means no bar.** A meter with an invented maximum draws a proportion nobody configured.
 *
 * Everything a story arranges is a `.tum-ui-story-*` class in the Storybook theme, so no story invents component
 * styling. The threshold word beside the meter is not decoration: without it the bar states its level by colour alone.
 */
const meta = {
    title: 'Patterns/Metering',
    decorators: [
        moduleMetadata({
            imports: [
                TumUiCardComponent,
                TumUiCardContentComponent,
                TumUiCardHeaderComponent,
                TumUiCardTitleComponent,
                TumUiMessageComponent,
                TumUiProgressBarComponent,
                TumUiTagComponent,
            ],
        }),
    ],
    args: {
        title: 'What this run spent',
        accountingLabel: 'Final',
        accountingSeverity: 'success',
        sealedAt: 'Sealed 14:09',
        lowerBound: false,
        heroValue: '€0.83',
        caption: '1,301,200 billable tokens across 48 requests.',
        meterLabel: 'Against the budget',
        meterUnit: 'billable tokens',
        meterOfLabel: 'of',
        meterAriaLabel: 'Budget used',
        meter: {
            used: '1,301,200',
            share: '62%',
            budget: '2,100,000',
            percent: 62,
            severity: 'primary',
            levelLabel: 'Comfortably inside',
            valueText: '1,301,200 of 2,100,000 billable tokens — 62%, comfortably inside the budget',
        },
        figures,
    },
    parameters: {
        layout: 'padded',
    },
    render: (args) => ({
        props: { ...args },
        template: `
            <tum-ui-card class="tum-ui-story-meter-card">
                <tum-ui-card-header>
                    <tum-ui-card-title [level]="2">{{ title }}</tum-ui-card-title>
                    <div class="tum-ui-story-meter-accounting">
                        <tum-ui-tag [severity]="accountingSeverity" variant="quiet" size="small">{{ accountingLabel }}</tum-ui-tag>
                        @if (sealedAt) {
                            <span class="tum-ui-story-meter-sealed">{{ sealedAt }}</span>
                        }
                    </div>
                </tum-ui-card-header>

                <tum-ui-card-content class="tum-ui-story-meter-body">
                    @if (notice) {
                        <tum-ui-message class="tum-ui-story-meter-notice" [severity]="notice.severity" [text]="notice.text" />
                    }

                    <!-- Not announced: on a live run this figure changes with every event, and must not be read out each time. -->
                    <div class="tum-ui-story-meter-hero" aria-live="off">
                        <p class="tum-ui-story-meter-hero-line">
                            @if (lowerBound) {
                                <span class="tum-ui-story-meter-qualifier">at least</span>
                            }
                            <span class="tum-ui-story-meter-amount">{{ heroValue }}</span>
                            @if (heroUnit) {
                                <span class="tum-ui-story-meter-unit">{{ heroUnit }}</span>
                            }
                        </p>
                        <p class="tum-ui-story-meter-caption">{{ caption }}</p>
                    </div>

                    @if (meter) {
                        <div class="tum-ui-story-meter-budget">
                            <div class="tum-ui-story-meter-row">
                                <span class="tum-ui-story-meter-muted">{{ meterLabel }}</span>
                                <span class="tum-ui-story-meter-row-figure">
                                    <span class="tum-ui-story-meter-number">{{ meter.used }}</span>
                                    <span class="tum-ui-story-meter-muted">{{ meterUnit }}</span>
                                </span>
                            </div>
                            <tum-ui-progress-bar
                                class="tum-ui-story-meter-bar"
                                [value]="meter.percent"
                                [severity]="meter.severity"
                                [showValue]="false"
                                [valueText]="meter.valueText"
                                [ariaLabel]="meterAriaLabel"
                            />
                            <p class="tum-ui-story-meter-row tum-ui-story-meter-muted" aria-live="off">
                                <span class="tum-ui-story-meter-row-figure">
                                    <span class="tum-ui-story-meter-number">{{ meter.share }}</span>
                                    <span>{{ meterOfLabel }}</span>
                                    <span class="tum-ui-story-meter-number">{{ meter.budget }}</span>
                                </span>
                                <span>{{ meter.levelLabel }}</span>
                            </p>
                        </div>
                    } @else if (uncappedLine) {
                        <p class="tum-ui-story-meter-muted tum-ui-story-meter-uncapped" aria-live="off">{{ uncappedLine }}</p>
                    }

                    <dl class="tum-ui-story-meter-figures" aria-live="off">
                        @for (figure of figures; track figure.label) {
                            <div class="tum-ui-story-meter-figure">
                                <dt class="tum-ui-story-meter-muted">{{ figure.label }}</dt>
                                <dd class="tum-ui-story-meter-figure-value">
                                    @if (figure.lowerBound) {
                                        <span class="tum-ui-story-meter-muted">at least</span>
                                    }
                                    <span class="tum-ui-story-meter-number">{{ figure.value }}</span>
                                </dd>
                            </div>
                        }
                    </dl>
                </tum-ui-card-content>
            </tum-ui-card>
        `,
    }),
} satisfies Meta<MeteringStoryArgs>;

export default meta;

type Story = StoryObj<MeteringStoryArgs>;

/**
 * The account is closed and every response was priced. Money is the hero, the tag says the figure has stopped moving,
 * and the moment it stopped is printed beside it so the number is not read as live a week later.
 */
export const PricedSealed: Story = {};

/**
 * The job is still working. The same figures, but the tag says so and no seal time is printed — the one honest
 * difference between a running total and a final one, since the numbers themselves look identical.
 */
export const Running: Story = {
    args: {
        accountingLabel: 'Running total',
        accountingSeverity: 'info',
        sealedAt: undefined,
        heroValue: '€0.41',
        caption: '648,900 billable tokens across 23 requests so far.',
        meter: {
            used: '648,900',
            share: '31%',
            budget: '2,100,000',
            percent: 31,
            severity: 'primary',
            levelLabel: 'Comfortably inside',
            valueText: '648,900 of 2,100,000 billable tokens — 31%, comfortably inside the budget',
        },
        figures: [
            { label: 'Requests', value: '23' },
            { label: 'Input', value: '601,204' },
            { label: 'Cached input', value: '402,880' },
            { label: 'Output', value: '47,696' },
            { label: 'Billable', value: '648,900' },
            { label: 'Longest wait', value: '38 s' },
        ],
    },
};

/**
 * No price is configured for the model this job used. The hero becomes the unit that *was* measured, and the missing
 * price is a sentence underneath — never `€0.00`, and never the words "Not priced" sitting in the figure's slot.
 */
export const Unpriced: Story = {
    args: {
        accountingLabel: 'Final',
        accountingSeverity: 'secondary',
        heroValue: '1,301,200',
        heroUnit: 'billable tokens',
        caption: 'No price is configured for this model on this server, so the run has no cost figure.',
    },
};

/**
 * The provider did not report usage for every response, so the total can never be closed. Three signals say so at
 * once — the "at least" in front of the figure, the `warning` tag, and a message that explains why — because a floor
 * mistaken for a total is the one error on this surface that survives review.
 */
export const IncompleteLowerBound: Story = {
    args: {
        accountingLabel: 'Incomplete',
        accountingSeverity: 'warning',
        sealedAt: undefined,
        notice: {
            severity: 'warning',
            text: 'Usage was missing from 6 of 48 responses. Every figure below is a lower bound, not a total.',
        },
        lowerBound: true,
        heroValue: '€0.83',
        caption: 'At least 1,301,200 billable tokens across 48 requests.',
        meter: {
            used: '1,301,200',
            share: '62%',
            budget: '2,100,000',
            percent: 62,
            severity: 'warning',
            levelLabel: 'At least this much used',
            valueText: 'at least 1,301,200 of 2,100,000 billable tokens — at least 62% of the budget',
        },
        figures: figures.map((figure) => (figure.label === 'Longest wait' ? figure : { ...figure, lowerBound: true })),
    },
};

/** Past the warning threshold. The bar changes colour *and* the word beside it changes, so neither carries the level alone. */
export const NearTheCeiling: Story = {
    args: {
        accountingLabel: 'Running total',
        accountingSeverity: 'info',
        sealedAt: undefined,
        heroValue: '€1.24',
        caption: '1,932,400 billable tokens across 71 requests so far.',
        meter: {
            used: '1,932,400',
            share: '92%',
            budget: '2,100,000',
            percent: 92,
            severity: 'warning',
            levelLabel: 'Close to the budget',
            valueText: '1,932,400 of 2,100,000 billable tokens — 92%, close to the budget',
        },
        figures: [
            { label: 'Requests', value: '71' },
            { label: 'Input', value: '1,788,110' },
            { label: 'Cached input', value: '1,204,660' },
            { label: 'Output', value: '144,290' },
            { label: 'Billable', value: '1,932,400' },
            { label: 'Longest wait', value: '52 s' },
        ],
    },
};

/**
 * The ceiling was reached and the job was stopped by it. The bar is full and `danger`, but the *reason* the job ended
 * is a message — a full red bar on its own says "expensive", not "this is why it stopped".
 */
export const CeilingReached: Story = {
    args: {
        accountingLabel: 'Final',
        accountingSeverity: 'danger',
        sealedAt: 'Sealed 14:31',
        notice: {
            severity: 'danger',
            text: 'The run stopped because it reached its budget. What it had already produced is kept.',
        },
        heroValue: '€1.35',
        caption: '2,100,000 billable tokens across 78 requests.',
        meter: {
            used: '2,100,000',
            share: '100%',
            budget: '2,100,000',
            percent: 100,
            severity: 'danger',
            levelLabel: 'Budget reached',
            valueText: '2,100,000 of 2,100,000 billable tokens — 100%, budget reached',
        },
        figures: [
            { label: 'Requests', value: '78' },
            { label: 'Input', value: '1,940,220' },
            { label: 'Cached input', value: '1,301,440' },
            { label: 'Output', value: '159,780' },
            { label: 'Billable', value: '2,100,000' },
            { label: 'Longest wait', value: '58 s' },
        ],
    },
};

/**
 * No ceiling is configured, so there is no proportion to draw and no bar is rendered. The spend is reported on its
 * own instead. An empty or invented meter here would be the surface asserting a limit the server never set.
 */
export const NoCeiling: Story = {
    args: {
        accountingLabel: 'Final',
        accountingSeverity: 'success',
        heroValue: '€0.83',
        caption: '1,301,200 billable tokens across 48 requests.',
        meter: undefined,
        uncappedLine: '1,301,200 billable tokens · no budget is configured for this server',
    },
};
