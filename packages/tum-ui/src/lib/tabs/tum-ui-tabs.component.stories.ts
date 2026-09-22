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

/** Panels can align with a containing card or section rather than adding a second inset. */
export const UnpaddedPanels: Story = {
    render: () => ({
        template: `
            <tum-ui-tabs value="overview">
                <tum-ui-tab-list aria-label="Course">
                    <tum-ui-tab value="overview">Overview</tum-ui-tab>
                    <tum-ui-tab value="exercises">Exercises</tum-ui-tab>
                </tum-ui-tab-list>
                <tum-ui-tab-panels [padded]="false">
                    <tum-ui-tab-panel value="overview">Content aligned with its containing surface.</tum-ui-tab-panel>
                    <tum-ui-tab-panel value="exercises">Exercise list</tum-ui-tab-panel>
                </tum-ui-tab-panels>
            </tum-ui-tabs>
        `,
    }),
};
