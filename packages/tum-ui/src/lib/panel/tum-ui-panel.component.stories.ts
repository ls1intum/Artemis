import { expect } from 'storybook/test';
import { argsToTemplate, moduleMetadata } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';
import { TumUiButtonDirective } from '../button/tum-ui-button.directive';
import { TumUiPanelComponent } from './tum-ui-panel.component';

interface PanelStoryArgs {
    header: string;
    content: string;
    toggleable: boolean;
    collapsed: boolean;
    density: 'default' | 'compact';
    contentPadding: boolean;
    showHeader: boolean;
}

const meta = {
    title: 'Data Display/Panel',
    component: TumUiPanelComponent,
    args: {
        header: 'Exercise details',
        content: 'Review the problem statement, due date, and grading criteria.',
        toggleable: false,
        collapsed: false,
        density: 'default',
        contentPadding: true,
        showHeader: true,
    },
    render: ({ content, ...args }) => {
        return {
            props: {
                ...args,
                content,
            },
            template: `
                <tum-ui-panel
                    [(collapsed)]="collapsed"
                    ${argsToTemplate(args, { exclude: ['collapsed'] })}
                    style="display: block; width: min(28rem, 100%);"
                >
                    <p style="margin: 0;">{{ content }}</p>
                </tum-ui-panel>
            `,
        };
    },
} satisfies Meta<PanelStoryArgs>;

export default meta;

type Story = StoryObj<PanelStoryArgs>;

export const Default: Story = {};

export const Toggleable: Story = {
    args: {
        toggleable: true,
    },
};

export const Collapsed: Story = {
    args: {
        collapsed: true,
        toggleable: true,
    },
};

export const Compact: Story = {
    args: { density: 'compact', toggleable: true },
    render: (args) => ({
        props: args,
        template: `
            <tum-ui-panel header="Compact panel" [density]="density" [toggleable]="toggleable">{{ content }}</tum-ui-panel>
            <tum-ui-panel header="Default panel" [toggleable]="toggleable">{{ content }}</tum-ui-panel>
        `,
    }),
    play: async ({ canvas }) => {
        const compact = canvas.getByRole('button', { name: 'Compact panel' });
        const normal = canvas.getByRole('button', { name: 'Default panel' });
        await expect(Number.parseFloat(getComputedStyle(compact.parentElement!).paddingBlockStart)).toBeLessThan(
            Number.parseFloat(getComputedStyle(normal.parentElement!).paddingBlockStart),
        );
    },
};

export const Unpadded: Story = {
    args: { density: 'compact', contentPadding: false, showHeader: false },
    play: async ({ canvas }) => {
        await expect(canvas.getByText('Exercise details')).not.toBeVisible();
        const content = canvas.getByText('Review the problem statement, due date, and grading criteria.');
        await expect(content).toBeVisible();
        await expect(getComputedStyle(content.parentElement!).padding).toBe('0px');
    },
};

export const ScrollableContent: Story = {
    render: () => ({
        template: `
            <div data-testid="panel-container" style="display: flex; width: 20rem; max-width: 100%;">
                <tum-ui-panel header="Wide table" [contentPadding]="false" style="flex: 1;" data-testid="panel">
                    <div data-testid="table-scroll" style="overflow-x: auto;" tabindex="0" role="region" aria-label="Scrollable results">
                        <table style="width: 60rem;">
                            <caption>Results</caption>
                            <thead><tr><th>Participant</th><th>Score</th></tr></thead>
                            <tbody><tr><td>Ada</td><td>42</td></tr></tbody>
                        </table>
                    </div>
                </tum-ui-panel>
            </div>
        `,
    }),
    play: async ({ canvas }) => {
        const container = canvas.getByTestId('panel-container');
        const panel = canvas.getByTestId('panel');
        const scroll = canvas.getByTestId('table-scroll');
        await expect(panel.getBoundingClientRect().width).toBeLessThanOrEqual(container.getBoundingClientRect().width);
        await expect(container.scrollWidth).toBeLessThanOrEqual(container.clientWidth);
        await expect(scroll.scrollWidth).toBeGreaterThan(scroll.clientWidth);
        scroll.scrollLeft = scroll.scrollWidth;
        await expect(scroll.scrollLeft).toBeGreaterThan(0);
    },
};

export const ProjectedHeader: Story = {
    decorators: [moduleMetadata({ imports: [TumUiButtonDirective] })],
    render: () => ({
        props: { collapsed: false },
        template: `
            <tum-ui-panel density="compact" [toggleable]="true" [(collapsed)]="collapsed" toggleAriaLabel="Toggle exam" style="width: 16rem;" data-testid="panel">
                <button tumUiPanelHeader tumUiButton type="button" size="small" variant="text" severity="secondary"
                    class="tum:min-w-0 tum:mr-2 tum:flex-1 tum:justify-start" data-testid="title-button"
                    [attr.aria-expanded]="!collapsed" aria-controls="exam-navigation-story" (click)="collapsed = !collapsed">
                    <span class="tum:truncate">An unusually long examination title that must fit the sidebar</span>
                </button>
                <div id="exam-navigation-story">Exam navigation</div>
            </tum-ui-panel>
        `,
    }),
    play: async ({ canvas, userEvent }) => {
        const title = canvas.getByTestId('title-button');
        const toggle = canvas.getByRole('button', { name: 'Toggle exam' });
        await expect(title.getBoundingClientRect().right).toBeLessThanOrEqual(toggle.getBoundingClientRect().left);
        const panel = canvas.getByTestId('panel');
        await expect(panel.scrollWidth).toBeLessThanOrEqual(panel.clientWidth);
        await userEvent.tab();
        await expect(title).toHaveFocus();
        await userEvent.keyboard('{Enter}');
        await expect(title).toHaveAttribute('aria-expanded', 'false');
        await expect(toggle).toHaveAttribute('aria-expanded', 'false');
    },
};
