import { argsToTemplate } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';
import { expect, fireEvent } from 'storybook/test';
import { TumUiTableVirtualScrollComponent } from './tum-ui-table-virtual-scroll.component';

const items = Array.from({ length: 1_000 }, (_, index) => ({
    id: index + 1,
    name: `Participant ${index + 1}`,
    score: 100 - (index % 31),
}));

const meta = {
    title: 'Data Display/Virtual Scroll Table',
    component: TumUiTableVirtualScrollComponent<(typeof items)[number]>,
    args: {
        ariaDescribedBy: 'participant-table-description',
        itemSize: 44,
        items,
        minWidth: '32rem',
        rowHover: true,
        scrollHeight: '264px',
        striped: true,
    },
    argTypes: {
        rowTemplate: {
            control: false,
        },
        size: {
            control: 'inline-radio',
            options: ['small', 'normal', 'large'],
        },
        trackBy: {
            control: false,
        },
    },
    render: (args) => ({
        props: args,
        template: `
            <p id="participant-table-description">Participant scores for the current course</p>
            <tum-ui-table-virtual-scroll
                [rowTemplate]="row"
                ${argsToTemplate(args, { exclude: ['rowTemplate', 'trackBy'] })}
            >
                <div role="columnheader" style="width: 16rem;">Participant</div>
                <div role="columnheader">Score</div>
            </tum-ui-table-virtual-scroll>

            <ng-template #row let-participant>
                <div role="cell" style="width: 16rem;">{{ participant.name }}</div>
                <div role="cell">{{ participant.score }}</div>
            </ng-template>
        `,
    }),
    parameters: {
        layout: 'padded',
    },
} satisfies Meta<TumUiTableVirtualScrollComponent<(typeof items)[number]>>;

export default meta;

type Story = StoryObj<TumUiTableVirtualScrollComponent<(typeof items)[number]>>;

export const Default: Story = {
    play: async ({ canvas }) => {
        const table = canvas.getByRole('table');
        await expect(table).toHaveAccessibleDescription('Participant scores for the current course');

        const viewport = canvas.getByRole('rowgroup');
        await fireEvent.scroll(viewport, { target: { scrollTop: 44 * 900 } });
        await expect(await canvas.findByText('Participant 901')).toBeVisible();
    },
};
