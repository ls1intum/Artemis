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

@Component({
    selector: 'tum-ui-density-layout-story',
    imports: [TumUiButtonDirective, TumUiInputDirective],
    template: `
        <div data-testid="layout-reference" style="height: 3rem;"></div>
        <button tumUiButton type="button" class="fixed-height">Consumer height</button>
        <input tumUiInput class="fixed-height" aria-label="Consumer height" />
    `,
    styles: '@layer utilities { .fixed-height { height: 3rem; min-height: 3rem; } }',
})
class DensityLayoutStoryComponent {}

const meta = {
    title: 'Layout/Control density',
    decorators: [
        moduleMetadata({
            imports: [
                DensityStoryRegionComponent,
                DensityLayoutStoryComponent,
                TumUiDensityDirective,
                TumUiButtonComponent,
                TumUiButtonDirective,
                TumUiInputDirective,
                TumUiSelectComponent,
                TumUiSelectButtonComponent,
            ],
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

const controls = (density = '') => `
    <tum-ui-button ${density} size="small">Small button</tum-ui-button>
    <tum-ui-button ${density} size="large">Large button</tum-ui-button>
    <button ${density} tumUiButton size="small">Native button</button>
    <input ${density} tumUiInput tumUiInputSize="small" aria-label="Small input" />
    <input ${density} tumUiInput tumUiInputSize="large" aria-label="Large input" />
    <tum-ui-select ${density} size="large" [options]="['One']" ariaLabel="Select" />
    <tum-ui-select-button ${density} [options]="['Small']" aria-label="Small segments" size="small" />
    <tum-ui-select-button ${density} [options]="['Default']" aria-label="Default segments" />
    <tum-ui-select-button ${density} [options]="['Large']" aria-label="Large segments" size="large" />
    <textarea ${density} tumUiTextarea aria-label="Notes" rows="3"></textarea>
`;

export const NestedAndSameHost: Story = {
    render: () => ({
        template: `
            <div style="container-type: inline-size; width: 282px;">
                <div data-testid="outside">${controls()}</div>
                <div tumUiDensity="compact">
                    <div data-testid="compact">${controls()}</div>
                    <div tumUiDensity="default">
                        <div data-testid="reset">${controls()}</div>
                        <div data-testid="compact-again" tumUiDensity="compact">${controls()}</div>
                    </div>
                    <div data-testid="same-host-reset">${controls('tumUiDensity="default"')}</div>
                </div>
                <div data-testid="same-host">${controls('tumUiDensity="compact"')}</div>
            </div>
        `,
    }),
    play: async ({ canvas }) => {
        const fields = (region: string) => Array.from(canvas.getByTestId(region).querySelectorAll<HTMLElement>('button, input'));
        const normal = fields('outside');
        for (const region of ['reset', 'same-host-reset']) {
            for (const [index, field] of fields(region).entries()) {
                await expect(field.getBoundingClientRect().height).toBeCloseTo(normal[index].getBoundingClientRect().height, 1);
                await expect(getComputedStyle(field).padding).toBe(getComputedStyle(normal[index]).padding);
            }
        }
        for (const region of ['compact', 'compact-again', 'same-host']) {
            for (const [index, field] of fields(region).entries()) {
                await expect(field.getBoundingClientRect().height).toBeCloseTo(28, 1);
                await expect(getComputedStyle(field).fontSize).toBe(getComputedStyle(normal[index]).fontSize);
            }
            const notes = within(canvas.getByTestId(region)).getByRole('textbox', { name: 'Notes' });
            await expect(notes.getBoundingClientRect().height).toBeCloseTo(
                within(canvas.getByTestId('outside')).getByRole('textbox', { name: 'Notes' }).getBoundingClientRect().height,
                1,
            );
        }
    },
};

export const ConsumerHostLayout: Story = {
    render: () => ({ template: '<tum-ui-density-layout-story />' }),
    play: async ({ canvas }) => {
        const height = canvas.getByTestId('layout-reference').getBoundingClientRect().height;
        await expect(canvas.getByRole('button', { name: 'Consumer height' }).getBoundingClientRect().height).toBeCloseTo(height, 1);
        await expect(canvas.getByRole('textbox', { name: 'Consumer height' }).getBoundingClientRect().height).toBeCloseTo(height, 1);
    },
};
