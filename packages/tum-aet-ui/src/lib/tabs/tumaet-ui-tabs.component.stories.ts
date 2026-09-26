import { moduleMetadata } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';
import { expect, fireEvent, fn, waitFor } from 'storybook/test';
import { TumAetUiTabListComponent } from './tumaet-ui-tab-list.component';
import { TumAetUiTabPanelComponent } from './tumaet-ui-tab-panel.component';
import { TumAetUiTabPanelsComponent } from './tumaet-ui-tab-panels.component';
import { TumAetUiTabComponent } from './tumaet-ui-tab.component';
import { TumAetUiTabsComponent } from './tumaet-ui-tabs.component';

const meta = {
    title: 'Navigation/Tabs',
    component: TumAetUiTabsComponent,
    subcomponents: {
        TabList: TumAetUiTabListComponent,
        Tab: TumAetUiTabComponent,
        TabPanels: TumAetUiTabPanelsComponent,
        TabPanel: TumAetUiTabPanelComponent,
    },
    decorators: [
        moduleMetadata({
            imports: [TumAetUiTabListComponent, TumAetUiTabPanelComponent, TumAetUiTabPanelsComponent, TumAetUiTabComponent],
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
                <tumaet-ui-tabs [value]="value" (valueChange)="value = $event; valueChange($event)">
                    <tumaet-ui-tab-list aria-label="Course">
                        <tumaet-ui-tab value="overview">Overview</tumaet-ui-tab>
                        <tumaet-ui-tab value="exercises">Exercises</tumaet-ui-tab>
                        <tumaet-ui-tab value="grading" [disabled]="true">Grading</tumaet-ui-tab>
                        <tumaet-ui-tab value="settings">Settings</tumaet-ui-tab>
                    </tumaet-ui-tab-list>
                    <tumaet-ui-tab-panels>
                        <tumaet-ui-tab-panel value="overview">Course overview</tumaet-ui-tab-panel>
                        <tumaet-ui-tab-panel value="exercises">Exercise list</tumaet-ui-tab-panel>
                        <tumaet-ui-tab-panel value="grading">Grading configuration</tumaet-ui-tab-panel>
                        <tumaet-ui-tab-panel value="settings">Course settings</tumaet-ui-tab-panel>
                    </tumaet-ui-tab-panels>
                </tumaet-ui-tabs>
            `,
        };
    },
} satisfies Meta<TumAetUiTabsComponent>;

export default meta;

type Story = StoryObj<TumAetUiTabsComponent>;

export const Default: Story = {};

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

        // A disabled tab takes focus, so it can be discovered and is announced as unavailable, but is not selected.
        await fireEvent.keyDown(overview, { key: 'ArrowLeft', keyCode: 37 });
        await fireEvent.keyDown(settings, { key: 'ArrowLeft', keyCode: 37 });
        const grading = canvas.getByRole('tab', { name: 'Grading' });
        await expect(grading).toHaveFocus();
        await expect(grading).toHaveAttribute('aria-disabled', 'true');
        await expect(grading).toHaveAttribute('aria-selected', 'false');
        await expect(canvas.getByRole('tabpanel')).toHaveTextContent('Course settings');
    },
};

/** Panels can align with a containing card or section rather than adding a second inset. */
export const UnpaddedPanels: Story = {
    render: () => ({
        template: `
            <tumaet-ui-tabs value="overview">
                <tumaet-ui-tab-list aria-label="Course">
                    <tumaet-ui-tab value="overview">Overview</tumaet-ui-tab>
                    <tumaet-ui-tab value="exercises">Exercises</tumaet-ui-tab>
                </tumaet-ui-tab-list>
                <tumaet-ui-tab-panels [padded]="false">
                    <tumaet-ui-tab-panel value="overview">Content aligned with its containing surface.</tumaet-ui-tab-panel>
                    <tumaet-ui-tab-panel value="exercises">Exercise list</tumaet-ui-tab-panel>
                </tumaet-ui-tab-panels>
            </tumaet-ui-tabs>
        `,
    }),
};
