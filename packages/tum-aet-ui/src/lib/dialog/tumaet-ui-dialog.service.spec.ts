import { ApplicationRef, Component, inject, input } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { TumAetUiDialogService } from './tumaet-ui-dialog.service';
import { TumAetUiDialogConfig, TumAetUiDialogRef } from './tumaet-ui-dialog.types';

@Component({
    template: `<p>{{ label() }}</p>
        <button (click)="ref.close('saved')">Save</button>`,
})
class DialogContentComponent {
    readonly ref = inject(TumAetUiDialogRef<string>);
    readonly config = inject(TumAetUiDialogConfig<{ course: number }>);
    readonly label = input.required<string>();
}

@Component({ template: `<button (click)="ref.dismiss('back')">Back</button>` })
class DialogFooterComponent {
    readonly ref = inject(TumAetUiDialogRef);
}

describe('TumAetUiDialogService', () => {
    let service: TumAetUiDialogService;
    let app: ApplicationRef;

    beforeEach(() => {
        service = TestBed.inject(TumAetUiDialogService);
        app = TestBed.inject(ApplicationRef);
    });

    afterEach(() => service.closeAll());

    async function render(): Promise<void> {
        app.tick();
        await app.whenStable();
        app.tick();
    }

    it('rejects unnamed content before creating a view or opening an overlay', () => {
        expect(() => service.open(DialogContentComponent, { showHeader: false, inputValues: { label: 'Unnamed' } })).toThrow('accessible name');
        expect(service.hasOpenDialogs()).toBe(false);
    });

    it('renders bound inputs in a named TUM dialog and provides typed data to the content', async () => {
        const ref = service.open<DialogContentComponent, string, { course: number }>(DialogContentComponent, {
            header: 'Exercise import',
            inputValues: { label: 'Choose an exercise' },
            data: { course: 42 },
        });
        await render();
        const dialog = document.querySelector('[role="dialog"]');
        expect(dialog?.textContent).toContain('Exercise import');
        expect(dialog?.textContent).toContain('Choose an exercise');
        expect(ref.componentInstance.config.data?.course).toBe(42);
        expect(service.hasOpenDialogs()).toBe(true);
        ref.setInputs({ label: 'Updated exercise' });
        await render();
        expect(dialog?.textContent).toContain('Updated exercise');
    });

    it('emits one result and destroys the view when content saves', async () => {
        const ref = service.open<DialogContentComponent, string>(DialogContentComponent, { header: 'Result', inputValues: { label: 'Review' } });
        const values: (string | undefined)[] = [];
        ref.onClose.subscribe((value) => values.push(value));
        await render();
        const save = Array.from(document.querySelectorAll('button')).find((button) => button.textContent === 'Save');
        save?.click();
        await render();
        expect(values).toEqual(['saved']);
        await expect(ref.result).resolves.toBe('saved');
        expect(document.querySelector('[role="dialog"]')).toBeNull();
        expect(service.hasOpenDialogs()).toBe(false);
        ref.close('again');
        expect(values).toEqual(['saved']);
    });

    it('dismisses on Escape and reports cancellation to both observable and promise consumers', async () => {
        const ref = service.open(DialogContentComponent, { header: 'Cancel', inputValues: { label: 'Review' } });
        const dismissed: unknown[] = [];
        ref.dismissed.subscribe((reason) => dismissed.push(reason));
        const result = ref.result.catch((reason: unknown) => reason);
        await render();
        document.querySelector('[role="dialog"]')?.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
        await render();
        expect(dismissed).toHaveLength(1);
        expect(await result).toBe('dismissed');
        expect(service.hasOpenDialogs()).toBe(false);
    });

    it('keeps protected dialogs open on Escape and backdrop click', async () => {
        service.open(DialogContentComponent, { header: 'Protected', closeOnEscape: false, dismissableMask: false, inputValues: { label: 'Review' } });
        await render();
        document.querySelector('[role="dialog"]')?.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
        document.querySelector('.cdk-overlay-backdrop')?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
        await render();
        expect(service.hasOpenDialogs()).toBe(true);
    });

    it('renders a custom footer using the same dialog reference', async () => {
        const ref = service.open(DialogContentComponent, { header: 'Footer', inputValues: { label: 'Review' }, templates: { footer: DialogFooterComponent } });
        const result = ref.result.catch((reason: unknown) => reason);
        await render();
        Array.from(document.querySelectorAll('button'))
            .find((button) => button.textContent === 'Back')
            ?.click();
        await render();
        expect(await result).toBe('back');
        expect(service.hasOpenDialogs()).toBe(false);
    });

    it('supports nested dialogs and closes all of them without leaving views or scroll locks', async () => {
        service.open(DialogContentComponent, { header: 'Parent', inputValues: { label: 'Parent body' } });
        service.open(DialogContentComponent, { header: 'Child', inputValues: { label: 'Child body' } });
        await render();
        expect(document.querySelectorAll('[role="dialog"]')).toHaveLength(2);
        service.closeAll();
        await render();
        expect(document.querySelectorAll('[role="dialog"]')).toHaveLength(0);
        expect(document.documentElement.style.overflow).not.toBe('hidden');
    });
});
