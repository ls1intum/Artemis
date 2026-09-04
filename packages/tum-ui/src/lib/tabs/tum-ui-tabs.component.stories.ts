import { moduleMetadata } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';
import { expect, fireEvent, fn, waitFor } from 'storybook/test';
import { TumUiTabListComponent } from './tum-ui-tab-list.component';
import { TumUiTabPanelComponent } from './tum-ui-tab-panel.component';
import { TumUiTabPanelsComponent } from './tum-ui-tab-panels.component';
import { TumUiTabComponent } from './tum-ui-tab.component';
import { TumUiTabsComponent } from './tum-ui-tabs.component';

const meta = {
    title: 'Navigation/Tabs',
    component: TumUiTabsComponent,
    subcomponents: {
        TabList: TumUiTabListComponent,
        Tab: TumUiTabComponent,
        TabPanels: TumUiTabPanelsComponent,
        TabPanel: TumUiTabPanelComponent,
    },
    decorators: [
        moduleMetadata({
            imports: [TumUiTabListComponent, TumUiTabPanelComponent, TumUiTabPanelsComponent, TumUiTabComponent],
        }),
    ],
    args: {
        value: 'overview',
        valueChange: fn(),
    },
    argTypes: {
        value: {
            control: 'inline-radio',
            options: ['overview', 'exercises', 'settings'],
        },
        valueChange: { control: false },
    },
    parameters: {
        layout: 'padded',
    },
    render: (args) => {
        return {
            props: { ...args },
            template: `
                <tum-ui-tabs [value]="value" (valueChange)="value = $event; valueChange($event)">
                    <tum-ui-tab-list aria-label="Course">
                        <tum-ui-tab value="overview">Overview</tum-ui-tab>
                        <tum-ui-tab value="exercises">Exercises</tum-ui-tab>
                        <tum-ui-tab value="grading" [disabled]="true">Grading</tum-ui-tab>
                        <tum-ui-tab value="settings">Settings</tum-ui-tab>
                    </tum-ui-tab-list>
                    <tum-ui-tab-panels>
                        <tum-ui-tab-panel value="overview">Course overview</tum-ui-tab-panel>
                        <tum-ui-tab-panel value="exercises">Exercise list</tum-ui-tab-panel>
                        <tum-ui-tab-panel value="grading">Grading configuration</tum-ui-tab-panel>
                        <tum-ui-tab-panel value="settings">Course settings</tum-ui-tab-panel>
                    </tum-ui-tab-panels>
                </tum-ui-tabs>
            `,
        };
    },
} satisfies Meta<TumUiTabsComponent>;

export default meta;

type Story = StoryObj<TumUiTabsComponent>;

export const Default: Story = {};

/**
 * `preserveContent` on the panel whose state the user expects to survive a trip to another tab.
 *
 * The default is to destroy an inactive panel, and that default is right: a tab nobody is looking at should not keep
 * a subscription open or hold a large view alive. But destroying a panel re-runs every child constructor, so a
 * scrolled list comes back at the top and a half-typed filter comes back empty. Here "Draft" is preserved and
 * "Activity" is not — switch away and back, and only one of them still has the text typed into it.
 *
 * A preserved panel is `hidden` **and** `inert`: out of the accessibility tree, out of the tab order, and out of
 * reach of find-in-page. `hidden` alone does not guarantee the last two for content that is still in the DOM.
 */
export const PreservedPanelState: Story = {
    args: { value: 'draft' },
    argTypes: { value: { control: 'inline-radio', options: ['draft', 'activity'] } },
    render: (args) => ({
        props: { ...args },
        template: `
            <tum-ui-tabs [value]="value" (valueChange)="value = $event; valueChange($event)">
                <tum-ui-tab-list aria-label="Exercise">
                    <tum-ui-tab value="draft">Draft</tum-ui-tab>
                    <tum-ui-tab value="activity">Activity</tum-ui-tab>
                </tum-ui-tab-list>
                <tum-ui-tab-panels>
                    <tum-ui-tab-panel value="draft" [preserveContent]="true">
                        <label for="preserved-note">Note (preserved)</label>
                        <input id="preserved-note" data-testid="preserved-note" placeholder="Type, then switch tabs" />
                    </tum-ui-tab-panel>
                    <tum-ui-tab-panel value="activity">
                        <label for="discarded-note">Note (discarded)</label>
                        <input id="discarded-note" data-testid="discarded-note" placeholder="Type, then switch tabs" />
                    </tum-ui-tab-panel>
                </tum-ui-tab-panels>
            </tum-ui-tabs>
        `,
    }),
};

/**
 * Measured proof of the two halves of the contract: the preserved panel keeps its DOM and its typed value across a
 * tab round-trip, and while inactive it is both `hidden` and `inert`. The unpreserved panel is genuinely gone.
 */
export const PreservedPanelSurvivesARoundTrip: Story = {
    ...PreservedPanelState,
    tags: ['!dev', '!autodocs'],
    play: async ({ canvas, canvasElement, userEvent }) => {
        const preserved = canvas.getByTestId('preserved-note');
        await userEvent.type(preserved, 'kept');
        await expect(preserved).toHaveValue('kept');

        await userEvent.click(canvas.getByRole('tab', { name: 'Activity' }));
        await waitFor(() => expect(canvas.getByRole('tabpanel')).toHaveTextContent('Note (discarded)'));

        // Still in the DOM, still holding its value — but taken out of reach while it is not the selected tab.
        const hiddenPanel = canvasElement.querySelector<HTMLElement>("tum-ui-tab-panel[data-state='inactive']")!;
        await expect(hiddenPanel).toHaveAttribute('hidden');
        await expect(hiddenPanel).toHaveAttribute('inert');
        await expect(hiddenPanel.querySelector<HTMLInputElement>("[data-testid='preserved-note']")).toHaveValue('kept');

        // The unpreserved panel was destroyed rather than hidden, which is the default and stays the default.
        await userEvent.click(canvas.getByRole('tab', { name: 'Draft' }));
        await waitFor(() => expect(canvasElement.querySelector("[data-testid='discarded-note']")).toBeNull());
        await expect(canvas.getByTestId('preserved-note')).toHaveValue('kept');
    },
};

export const KeyboardNavigation: Story = {
    tags: ['!dev', '!autodocs'],
    play: async ({ args, canvas, userEvent }) => {
        const overview = canvas.getByRole('tab', { name: 'Overview' });
        await userEvent.tab();
        await fireEvent.keyDown(overview, { key: 'End', keyCode: 35 });

        const settings = canvas.getByRole('tab', { name: 'Settings' });
        await expect(settings).toHaveFocus();
        await waitFor(() => expect(settings).toHaveAttribute('aria-selected', 'true'));
        await expect(canvas.getByRole('tabpanel')).toHaveTextContent('Course settings');
        await expect(args.valueChange).toHaveBeenCalledWith('settings');

        await fireEvent.keyDown(settings, { key: 'ArrowRight', keyCode: 39 });
        await expect(overview).toHaveFocus();
    },
};
