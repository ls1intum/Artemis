import { Component } from '@angular/core';
import { moduleMetadata } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';
import { expect, within } from 'storybook/test';
import { TumUiButtonComponent } from '../button/tum-ui-button.component';
import { TumUiButtonDirective } from '../button/tum-ui-button.directive';
import { TumUiInputDirective } from '../input/tum-ui-input.directive';
import { TumUiSelectComponent } from '../select/tum-ui-select.component';
import { TumUiSelectButtonComponent } from '../select-button/tum-ui-select-button.component';
import { TumUiDensityDirective } from './tum-ui-density.directive';

@Component({
    selector: 'tum-ui-density-story-region',
    imports: [TumUiDensityDirective],
    template: '<div tumUiDensity="compact"><ng-content /></div>',
    styles: ':host { display: block; container-type: inline-size; }',
})
class DensityStoryRegionComponent {}

const meta = {
    title: 'Layout/Control density',
    decorators: [
        moduleMetadata({
            imports: [DensityStoryRegionComponent, TumUiButtonComponent, TumUiButtonDirective, TumUiInputDirective, TumUiSelectComponent, TumUiSelectButtonComponent],
        }),
    ],
    render: () => ({
        props: { options: ['Day', 'Week', 'Month'] },
        template: `
            <tum-ui-density-story-region style="width: 720px" data-testid="wide">
                <tum-ui-button size="small">Wrapped button</tum-ui-button>
                <tum-ui-button size="large">Large button</tum-ui-button>
                <button tumUiButton>Native button</button>
                <input tumUiInput aria-label="Search" />
                <tum-ui-select [options]="options" ariaLabel="Interval" />
                <tum-ui-select-button [options]="options" aria-label="Wide segments" />
                <tum-ui-select-button [options]="['Small']" aria-label="Small segments" size="small" />
                <tum-ui-select-button [options]="['Large']" aria-label="Large segments" size="large" />
                <textarea tumUiTextarea aria-label="Notes" rows="3"></textarea>
            </tum-ui-density-story-region>
            <tum-ui-density-story-region style="width: 282px" data-testid="narrow">
                <tum-ui-select-button [options]="options" aria-label="Narrow segments" />
            </tum-ui-density-story-region>
            <tum-ui-button size="small">Outside region</tum-ui-button>
        `,
    }),
} satisfies Meta;

export default meta;
type Story = StoryObj<typeof meta>;

export const ProjectedControls: Story = {
    play: async ({ canvasElement }) => {
        const canvas = within(canvasElement);
        const wide = within(canvas.getByTestId('wide'));
        const narrow = within(canvas.getByTestId('narrow'));
        const height = (element: HTMLElement) => element.getBoundingClientRect().height;
        for (const control of [...wide.getAllByRole('button'), wide.getByRole('textbox', { name: 'Search' }), wide.getByRole('combobox')]) {
            await expect(height(control)).toBeCloseTo(28, 1);
        }
        const textarea = wide.getByRole('textbox', { name: 'Notes' });
        await expect(textarea).toHaveClass('tum-ui-input');
        await expect(height(textarea)).toBeGreaterThan(28);
        await expect(height(canvas.getByRole('button', { name: 'Outside region' }))).toBeGreaterThan(28);
        const wideOption = wide.getByRole('button', { name: 'Day' });
        const narrowOption = narrow.getByRole('button', { name: 'Day' });
        await expect(height(narrowOption)).toBeCloseTo(28, 1);
        await expect(wideOption.getBoundingClientRect().width - narrowOption.getBoundingClientRect().width).toBeCloseTo(16, 1);
        const region = canvas.getByTestId('narrow').getBoundingClientRect();
        await expect(narrow.getByRole('group').getBoundingClientRect().right).toBeLessThanOrEqual(region.right);
    },
};
