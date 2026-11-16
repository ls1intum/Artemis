import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

import { SecurityYamlDialogComponent } from 'app/programming/manage/update/update-components/security/policies/security-yaml-dialog.component';

type Handlers = { setView(view: 'committed' | 'unsaved'): void; copy(): Promise<void>; export(): void; onFileSelected(event: Event): Promise<void> };

describe('SecurityYamlDialogComponent', () => {
    let fixture: ComponentFixture<SecurityYamlDialogComponent>;
    let comp: SecurityYamlDialogComponent;
    let handlers: Handlers;

    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [{ provide: TranslateService, useClass: MockTranslateService }] });
        fixture = TestBed.createComponent(SecurityYamlDialogComponent);
        comp = fixture.componentInstance;
        handlers = comp as unknown as Handlers;
        fixture.componentRef.setInput('policyPath', 'test/SecurityPolicy.yaml');
        fixture.componentRef.setInput('committedContent', 'a: 1\nb: 2\n');
        fixture.componentRef.setInput('unsavedContent', 'a: 1\nb: 3\nc: 4\n');
        comp.visible.set(true);
        fixture.detectChanges();
    });

    afterEach(() => vi.restoreAllMocks());

    it('shows the unsaved version with its changed lines, and the committed version', () => {
        expect(comp.fileName()).toBe('SecurityPolicy.yaml');
        expect(comp.lines().map((line) => [line.text, line.highlighted])).toEqual([
            ['a: 1', false],
            ['b: 3', true],
            ['c: 4', true],
        ]);
        expect(comp.changedLines()).toBe(2);

        handlers.setView('committed');
        expect(comp.lines().map((line) => line.text)).toEqual(['a: 1', 'b: 2']);
        expect(comp.changedLines()).toBe(0);
    });

    it('asks for the unsaved version when it is not rendered yet', () => {
        const requested = vi.fn();
        comp.unsavedRequested.subscribe(requested);
        fixture.componentRef.setInput('unsavedContent', undefined);

        handlers.setView('unsaved');

        expect(requested).toHaveBeenCalledOnce();
        expect(comp.lines()).toEqual([]);
    });

    it('copies the shown version', async () => {
        const writeText = vi.fn().mockResolvedValue(undefined);
        Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true });

        await handlers.copy();

        expect(writeText).toHaveBeenCalledWith('a: 1\nb: 3\nc: 4\n');
        expect(comp.copied()).toBe(true);
    });

    it('exports the shown version as a file named like the policy', () => {
        const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
        URL.createObjectURL = vi.fn().mockReturnValue('blob:policy');
        URL.revokeObjectURL = vi.fn();

        handlers.export();

        expect(click).toHaveBeenCalledOnce();
        expect((click.mock.contexts[0] as HTMLAnchorElement).download).toBe('SecurityPolicy.yaml');
        expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:policy');
    });

    it('neither copies nor exports while there is nothing to show', async () => {
        fixture.componentRef.setInput('unsavedContent', undefined);
        URL.createObjectURL = vi.fn();

        await handlers.copy();
        handlers.export();

        expect(comp.copied()).toBe(false);
        expect(URL.createObjectURL).not.toHaveBeenCalled();
    });

    it('imports a selected file and refuses a file that is too large', async () => {
        const imported: { content: string; policyPath: string }[] = [];
        comp.imported.subscribe((content) => imported.push(content));
        const select = (file: Partial<File>) => handlers.onFileSelected({ target: { files: [file], value: 'x' } } as unknown as Event);

        await select({ size: 300 * 1024, text: () => Promise.resolve('big') });
        expect(comp.importError()).toBe('artemisApp.programmingExercise.security.advanced.yaml.importTooLarge');

        await select({ size: 10, text: () => Promise.resolve('a: 1\n') });
        expect(imported).toEqual([{ content: 'a: 1\n', policyPath: 'test/SecurityPolicy.yaml' }]);
        expect(comp.importError()).toBeUndefined();
    });

    it('reports while a selected file is being read', async () => {
        const reading: boolean[] = [];
        comp.readingChange.subscribe((value) => reading.push(value));
        let finishReading: (content: string) => void = () => {};

        const read = handlers.onFileSelected({
            target: { files: [{ size: 10, text: () => new Promise<string>((resolve) => (finishReading = resolve)) }], value: 'x' },
        } as unknown as Event);
        expect(reading).toEqual([true]);
        finishReading('a: 1\n');
        await read;

        expect(reading).toEqual([true, false]);
    });

    it('drops a file that is still being read when the parent replaces the unsaved changes', async () => {
        const imported: { content: string; policyPath: string }[] = [];
        comp.imported.subscribe((content) => imported.push(content));
        let finishReading: (content: string) => void = () => {};

        const reading = handlers.onFileSelected({
            target: { files: [{ size: 10, text: () => new Promise<string>((resolve) => (finishReading = resolve)) }], value: 'x' },
        } as unknown as Event);
        fixture.componentRef.setInput('importEpoch', 1);
        finishReading('a: 1\n');
        await reading;

        expect(imported).toEqual([]);
    });

    it('drops a file that is still being read when a later selection is refused', async () => {
        const imported: { content: string; policyPath: string }[] = [];
        comp.imported.subscribe((content) => imported.push(content));
        const select = (file: Partial<File>) => handlers.onFileSelected({ target: { files: [file], value: 'x' } } as unknown as Event);
        let finishReading: (content: string) => void = () => {};

        const reading = select({ size: 10, text: () => new Promise<string>((resolve) => (finishReading = resolve)) });
        await select({ size: 300 * 1024, text: () => Promise.resolve('big') });
        finishReading('a: 1\n');
        await reading;

        expect(imported).toEqual([]);
        expect(comp.importError()).toBe('artemisApp.programmingExercise.security.advanced.yaml.importTooLarge');
    });
});
