import { moduleMetadata } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';
import { expect, fireEvent, waitFor } from 'storybook/test';
import { TumAetUiAccordionPanelComponent } from './tumaet-ui-accordion-panel.component';
import { TumAetUiAccordionComponent } from './tumaet-ui-accordion.component';

interface AccordionStoryArgs {
    multiExpandable: boolean;
    disabled: boolean;
    wrap: boolean;
}

const meta = {
    title: 'Data Display/Accordion',
    component: TumAetUiAccordionComponent,
    subcomponents: {
        AccordionPanel: TumAetUiAccordionPanelComponent,
    },
    decorators: [
        moduleMetadata({
            imports: [TumAetUiAccordionPanelComponent],
        }),
    ],
    args: {
        multiExpandable: false,
        disabled: false,
        wrap: false,
    },
    parameters: {
        layout: 'padded',
    },
    render: (args) => {
        return {
            props: { ...args },
            template: `
                <tumaet-ui-accordion [multiExpandable]="multiExpandable" [disabled]="disabled" [wrap]="wrap" style="max-width: 40rem;">
                    <tumaet-ui-accordion-panel header="Which programming languages are supported?">
                        Java, Python, C, Swift, Kotlin and more than ten further languages.
                    </tumaet-ui-accordion-panel>
                    <tumaet-ui-accordion-panel header="How does automatic grading work?">
                        Every submission is built and tested, and the result appears within seconds.
                    </tumaet-ui-accordion-panel>
                    <tumaet-ui-accordion-panel header="Can exams be archived?" [disabled]="true">
                        Archiving is not available for this course.
                    </tumaet-ui-accordion-panel>
                </tumaet-ui-accordion>
            `,
        };
    },
} satisfies Meta<AccordionStoryArgs>;

export default meta;

type Story = StoryObj<AccordionStoryArgs>;

export const Default: Story = {};

export const MultiExpandable: Story = {
    args: {
        multiExpandable: true,
    },
};

export const KeyboardNavigation: Story = {
    tags: ['!dev', '!autodocs'],
    play: async ({ canvas, userEvent }) => {
        const languages = canvas.getByRole('button', { name: 'Which programming languages are supported?' });
        const grading = canvas.getByRole('button', { name: 'How does automatic grading work?' });
        const archive = canvas.getByRole('button', { name: 'Can exams be archived?' });

        await userEvent.click(languages);
        await waitFor(() => expect(languages).toHaveAttribute('aria-expanded', 'true'));
        await waitFor(() => expect(canvas.getByRole('region', { name: 'Which programming languages are supported?' })).toBeVisible());

        await fireEvent.keyDown(languages, { key: 'ArrowDown', keyCode: 40 });
        await expect(grading).toHaveFocus();
        await fireEvent.keyDown(grading, { key: 'Enter', keyCode: 13 });

        // Opening a panel closes the one that was open.
        await waitFor(() => expect(grading).toHaveAttribute('aria-expanded', 'true'));
        await expect(languages).toHaveAttribute('aria-expanded', 'false');

        // A disabled panel takes focus, so it can be discovered and is announced as unavailable, but does not open.
        await fireEvent.keyDown(grading, { key: 'ArrowDown', keyCode: 40 });
        await expect(archive).toHaveFocus();
        await expect(archive).toHaveAttribute('aria-disabled', 'true');
        await fireEvent.keyDown(archive, { key: 'Enter', keyCode: 13 });
        await expect(archive).toHaveAttribute('aria-expanded', 'false');
    },
};
