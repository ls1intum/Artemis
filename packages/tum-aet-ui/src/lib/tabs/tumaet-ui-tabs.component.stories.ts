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

/** Height-bound editors and chats keep their own scroll area and a visible composer. */
export const BoundedContent: Story = {
    render: () => ({
        props: { value: 'chat', messages: Array.from({ length: 50 }, (_, index) => `Message ${index + 1}`) },
        template: `
            <div data-testid="bounded-tabs" style="height: 20rem; width: min(36rem, 100%);">
                <tumaet-ui-tabs [value]="value" (valueChange)="value = $event" style="height: 100%; min-height: 0;">
                    <tumaet-ui-tab-list aria-label="Workspace" style="flex-shrink: 0;">
                        <tumaet-ui-tab value="chat">Chat</tumaet-ui-tab>
                        <tumaet-ui-tab value="notes">Notes</tumaet-ui-tab>
                    </tumaet-ui-tab-list>
                    <tumaet-ui-tab-panels style="flex: 1; min-height: 0; padding: 0; overflow: auto;">
                        <tumaet-ui-tab-panel value="chat" preserveContent fill>
                            <section aria-label="Chat conversation" style="height: 100%; min-height: 0; display: flex; flex-direction: column;">
                                <div role="log" aria-label="Messages" tabindex="0" style="flex: 1; min-height: 0; overflow: auto;">
                                    @for (message of messages; track message) { <p>{{ message }}</p> }
                                </div>
                                <label style="flex-shrink: 0;">Message<textarea aria-label="Message" style="display: block;"></textarea></label>
                            </section>
                        </tumaet-ui-tab-panel>
                        <tumaet-ui-tab-panel value="notes">Workspace notes</tumaet-ui-tab-panel>
                    </tumaet-ui-tab-panels>
                </tumaet-ui-tabs>
            </div>
        `,
    }),
    play: async ({ canvas, userEvent }) => {
        const bounds = canvas.getByTestId('bounded-tabs');
        const chat = canvas.getByRole('region', { name: 'Chat conversation' });
        const messages = canvas.getByRole('log', { name: 'Messages' });
        const composer = canvas.getByRole('textbox', { name: 'Message' });

        await waitFor(async () => {
            await expect(chat.getBoundingClientRect().bottom).toBeLessThanOrEqual(bounds.getBoundingClientRect().bottom + 0.5);
            await expect(chat.getBoundingClientRect().height).toBeGreaterThan(0);
            await expect(chat.getBoundingClientRect().height).toBeLessThan(bounds.getBoundingClientRect().height);
            await expect(composer.getBoundingClientRect().bottom).toBeLessThanOrEqual(bounds.getBoundingClientRect().bottom + 0.5);
            await expect(messages.scrollHeight).toBeGreaterThan(messages.clientHeight);
            await expect(messages.clientHeight).toBeGreaterThan(0);
        });
        await userEvent.type(composer, 'Unsaved draft');
        const composerTop = composer.getBoundingClientRect().top;
        messages.scrollTop = messages.scrollHeight;
        await fireEvent.scroll(messages);
        await expect(composer.getBoundingClientRect().top).toBeCloseTo(composerTop, 1);

        await userEvent.click(canvas.getByRole('tab', { name: 'Notes' }));
        await waitFor(() => expect(chat).not.toBeVisible());
        await userEvent.click(canvas.getByRole('tab', { name: 'Chat' }));
        await waitFor(() => expect(chat).toBeVisible());
        await expect(canvas.getByRole('textbox', { name: 'Message' })).toBe(composer);
        await expect(composer).toHaveValue('Unsaved draft');
    },
};
