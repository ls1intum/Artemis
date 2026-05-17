import { ComponentFixture, TestBed } from '@angular/core/testing';
import { describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ReclaimDiskDialogComponent, ReclaimDiskDialogInput, ReclaimDiskDialogResult } from './reclaim-disk-dialog.component';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('ReclaimDiskDialogComponent', () => {
    setupTestBed({ zoneless: true });

    let component: ReclaimDiskDialogComponent;
    let fixture: ComponentFixture<ReclaimDiskDialogComponent>;
    let dialogRefCloseSpy: ReturnType<typeof vi.fn>;

    async function configureWithData(data: ReclaimDiskDialogInput) {
        dialogRefCloseSpy = vi.fn();
        await TestBed.configureTestingModule({
            imports: [ReclaimDiskDialogComponent],
            providers: [
                { provide: DynamicDialogRef, useValue: { close: dialogRefCloseSpy } },
                { provide: DynamicDialogConfig, useValue: { data } },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(ReclaimDiskDialogComponent);
        component = fixture.componentInstance;
        await fixture.whenStable();
    }

    it('renders sizes from the injected data', async () => {
        await configureWithData({
            agentName: 'agent01',
            diskUsableBytes: 1024,
            diskTotalBytes: 4096,
            mavenCacheBytes: 256,
            gradleCacheBytes: 512,
            dockerUnusedImageBytes: 0,
            dockerUnusedImageCount: 0,
        });
        fixture.detectChanges();
        const html = fixture.nativeElement.textContent as string;
        expect(html).toContain('1.0 KB'); // diskUsable
        expect(html).toContain('4.0 KB'); // diskTotal
        expect(html).toContain('256 B');
        expect(html).toContain('512 B');
    });

    it('disables Confirm by default', async () => {
        await configureWithData({ agentName: 'agent01' });
        expect(component.canConfirm()).toBe(false);
    });

    it('keeps Confirm disabled if options are picked but confirm word is wrong', async () => {
        await configureWithData({ agentName: 'agent01' });
        component.wipeMaven.set(true);
        component.typedConfirm.set('NOPE');
        expect(component.canConfirm()).toBe(false);
    });

    it('keeps Confirm disabled if confirm word is typed but no option is selected', async () => {
        await configureWithData({ agentName: 'agent01' });
        component.typedConfirm.set('RECLAIM');
        expect(component.canConfirm()).toBe(false);
    });

    it('enables Confirm when an option is selected AND the confirm word matches exactly', async () => {
        await configureWithData({ agentName: 'agent01' });
        component.wipeGradle.set(true);
        component.typedConfirm.set('RECLAIM');
        expect(component.canConfirm()).toBe(true);
    });

    it('emits the selected options on confirm', async () => {
        await configureWithData({ agentName: 'agent01' });
        component.wipeMaven.set(true);
        component.clearDocker.set(true);
        component.typedConfirm.set('RECLAIM');

        component.confirm();

        const expected: ReclaimDiskDialogResult = { wipeMaven: true, wipeGradle: false, clearDocker: true };
        expect(dialogRefCloseSpy).toHaveBeenCalledWith(expected);
    });

    it('does not close (and emits nothing) if Confirm is invoked while disabled', async () => {
        await configureWithData({ agentName: 'agent01' });
        component.typedConfirm.set('RECLAIM'); // no option picked

        component.confirm();

        expect(dialogRefCloseSpy).not.toHaveBeenCalled();
    });

    it('closes with undefined on cancel', async () => {
        await configureWithData({ agentName: 'agent01' });
        component.cancel();
        expect(dialogRefCloseSpy).toHaveBeenCalledWith(undefined);
    });
});
