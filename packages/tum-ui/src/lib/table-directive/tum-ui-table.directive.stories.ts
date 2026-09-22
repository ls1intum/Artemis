import { argsToTemplate, moduleMetadata } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';
import { expect, fn } from 'storybook/test';
import { TumUiTableSortableColumnComponent } from './tum-ui-table-sortable-column.component';
import { TumUiTableDirective } from './tum-ui-table.directive';

const meta = {
    title: 'Data Display/Native Table',
    component: TumUiTableDirective,
    subcomponents: {
        SortableColumn: TumUiTableSortableColumnComponent,
    },
    argTypes: {
        size: {
            control: 'inline-radio',
            options: ['small', 'normal', 'large'],
        },
        sortOrder: {
            control: 'inline-radio',
            options: [1, -1],
        },
    },
    decorators: [
        moduleMetadata({
            imports: [TumUiTableSortableColumnComponent],
        }),
    ],
    args: {
        rowHover: true,
        size: 'normal',
        sortChange: fn(),
        sortField: 'name',
        sortOrder: 1,
        striped: true,
    },
    render: (args) => {
        return {
            props: args,
            template: `
                <table
                    tumUiTable
                    ${argsToTemplate(args, { exclude: ['sortChange'] })}
                    (sortChange)="sortField = $event.field; sortOrder = $event.order; sortChange($event)"
                >
                    <thead>
                        <tr>
                            <th tumUiSortableColumn="name">Participant</th>
                            <th tumUiSortableColumn="score" contentAlign="end">Score</th>
                            <th>Status</th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr><td>Ada Lovelace</td><td>98</td><td>Submitted</td></tr>
                        <tr><td>Grace Hopper</td><td>94</td><td>Submitted</td></tr>
                        <tr><td>Margaret Hamilton</td><td>91</td><td>In review</td></tr>
                    </tbody>
                </table>
            `,
        };
    },
    parameters: {
        layout: 'padded',
    },
} satisfies Meta<TumUiTableDirective>;

export default meta;

type Story = StoryObj<TumUiTableDirective>;

export const Default: Story = {
    play: async ({ canvas, userEvent }) => {
        const participantHeader = canvas.getByRole('columnheader', { name: /Participant/ });
        await expect(participantHeader).toHaveAttribute('aria-sort', 'ascending');
        const score = canvas.getByRole('button', { name: /Score/ });
        const scoreHeader = canvas.getByRole('columnheader', { name: /Score/ });
        await userEvent.click(score);
        await expect(scoreHeader).toHaveAttribute('aria-sort', 'ascending');
        await userEvent.keyboard(' ');
        await expect(scoreHeader).toHaveAttribute('aria-sort', 'descending');
    },
};

export const Alignment: Story = {
    render: () => ({
        props: { direction: 'ltr' },
        template: `
            <button type="button" (click)="direction = direction === 'ltr' ? 'rtl' : 'ltr'">Switch direction</button>
            <table tumUiTable [attr.dir]="direction" style="table-layout: fixed;">
                <thead>
                    <tr>
                        <th tumUiSortableColumn="start" contentAlign="start"><span>Start</span></th>
                        <th tumUiSortableColumn="center" contentAlign="center"><span>Center</span></th>
                        <th tumUiSortableColumn="end" contentAlign="end"><span>End</span></th>
                    </tr>
                </thead>
            </table>
        `,
    }),
    play: async ({ canvas, userEvent }) => {
        for (const direction of ['ltr', 'rtl']) {
            for (const alignment of ['start', 'center', 'end']) {
                const button = canvas.getByRole('button', { name: new RegExp(alignment, 'i') });
                const label = button.querySelector('span')!.getBoundingClientRect();
                const icon = button.querySelector('[aria-hidden="true"]')!.getBoundingClientRect();
                const bounds = button.getBoundingClientRect();
                const left = Math.min(label.left, icon.left);
                const right = Math.max(label.right, icon.right);
                if (alignment === 'center') {
                    await expect((left + right) / 2).toBeCloseTo((bounds.left + bounds.right) / 2, 0);
                } else if ((alignment === 'start') === (direction === 'ltr')) {
                    await expect(left).toBeCloseTo(bounds.left, 0);
                } else {
                    await expect(right).toBeCloseTo(bounds.right, 0);
                }
                // Alignment must not shrink the native button's click/focus target to the label.
                await expect(bounds.width).toBeGreaterThan(right - left);
            }
            if (direction === 'ltr') await userEvent.click(canvas.getByRole('button', { name: 'Switch direction' }));
        }
    },
};

export const LongHeading: Story = {
    render: () => ({
        template: `
            <table tumUiTable style="table-layout: fixed; width: 12rem;">
                <thead>
                    <tr>
                        <th tumUiSortableColumn="assessments" contentAlign="end">
                            <span style="min-width: 0; white-space: normal;">Number of accepted assessment complaints</span>
                        </th>
                    </tr>
                </thead>
            </table>
        `,
    }),
    play: async ({ canvas, userEvent }) => {
        const button = canvas.getByRole('button', { name: 'Number of accepted assessment complaints' });
        const label = canvas.getByText('Number of accepted assessment complaints');
        await expect(label.getBoundingClientRect().height).toBeGreaterThan(Number.parseFloat(getComputedStyle(label).lineHeight));
        await expect(button.scrollWidth).toBeLessThanOrEqual(button.clientWidth);
        await userEvent.tab();
        await expect(button).toHaveFocus();
    },
};
