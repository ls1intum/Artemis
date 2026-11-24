import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

import { SecurityActivationCardComponent } from 'app/programming/manage/update/update-components/security/policies/security-activation-card.component';
import { SecurityActivationDialogComponent } from 'app/programming/manage/update/update-components/security/policies/security-activation-dialog.component';

describe('Security activation', () => {
    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: TranslateService, useClass: MockTranslateService }] });
    });

    describe('SecurityActivationCardComponent', () => {
        it('shows the state and the last commit and forwards the toggles and the version', () => {
            const fixture = TestBed.createComponent(SecurityActivationCardComponent);
            const comp = fixture.componentInstance;
            const toggled = vi.fn();
            const scanned = vi.fn();
            const versions = vi.fn();
            comp.toggleRequested.subscribe(toggled);
            comp.scanRequested.subscribe(scanned);
            comp.versionChange.subscribe(versions);
            fixture.componentRef.setInput('state', 'configured');
            fixture.componentRef.setInput('toggleOn', true);
            fixture.componentRef.setInput('scanAvailable', true);
            fixture.componentRef.setInput('lastCommit', { hash: 'abcdef1234567', authorName: 'Instructor', date: '2026-11-15T10:00:00Z' });
            fixture.componentRef.setInput('versionOptions', [{ version: '2.2.1', label: '2.2.1' }]);
            fixture.componentRef.setInput('version', '2.2.1');
            fixture.detectChanges();

            const element: HTMLElement = fixture.nativeElement;
            expect(element.querySelector('[data-testid="security-advanced-configured"]')).not.toBeNull();
            // The saved version needs no explanation while the sandbox is active; only an inactive one explains what its builds do.
            const sync = element.querySelector('[data-testid="security-advanced-sync"]');
            expect(sync?.textContent).toContain('abcdef1');
            expect(sync?.textContent).not.toContain('card.commit');
            expect(sync?.querySelector('jhi-security-help')).toBeNull();
            fixture.componentRef.setInput('state', 'inactive');
            fixture.componentRef.setInput('toggleOn', false);
            fixture.detectChanges();
            expect(element.querySelector('[data-testid="security-advanced-sync"] jhi-security-help')).not.toBeNull();
            fixture.componentRef.setInput('state', 'configured');
            fixture.componentRef.setInput('toggleOn', true);
            fixture.detectChanges();

            const handlers = comp as unknown as { onToggle(on: boolean): void; onScan(on: boolean): void; onVersion(version: unknown): void };
            handlers.onToggle(false);
            handlers.onScan(true);
            handlers.onVersion(undefined);
            handlers.onVersion('2.2.1');
            expect(toggled).toHaveBeenCalledWith(false);
            expect(scanned).toHaveBeenCalledWith(true);
            expect(versions).toHaveBeenCalledExactlyOnceWith('2.2.1');
        });

        it('disables dependency scanning while a policy is read-only', () => {
            const fixture = TestBed.createComponent(SecurityActivationCardComponent);
            fixture.componentRef.setInput('state', 'configured');
            fixture.componentRef.setInput('toggleOn', true);
            fixture.componentRef.setInput('scanAvailable', true);
            fixture.componentRef.setInput('scanBlocked', true);
            fixture.detectChanges();

            const scanned = vi.fn();
            fixture.componentInstance.scanRequested.subscribe(scanned);
            const toggle = (): HTMLInputElement | null => fixture.nativeElement.querySelector('[data-testid="security-advanced-scan"] input');
            expect(toggle()?.disabled).toBe(true);
            toggle()?.click();
            expect(scanned).not.toHaveBeenCalled();

            fixture.componentRef.setInput('scanBlocked', false);
            fixture.detectChanges();
            expect(toggle()?.disabled).toBe(false);
        });

        it('shows only the toggle while the sandbox is off', () => {
            const fixture = TestBed.createComponent(SecurityActivationCardComponent);
            fixture.componentRef.setInput('state', 'inactive');
            fixture.detectChanges();

            expect(fixture.nativeElement.querySelector('[data-testid="security-advanced-toggle"]')).not.toBeNull();
            expect(fixture.nativeElement.querySelector('[data-testid="security-advanced-configured"]')).toBeNull();
        });
    });

    describe('SecurityActivationDialogComponent', () => {
        it('confirms the chosen version when turning the sandbox on', () => {
            const fixture = TestBed.createComponent(SecurityActivationDialogComponent);
            const comp = fixture.componentInstance;
            const confirmed = vi.fn();
            const cancelled = vi.fn();
            comp.confirmed.subscribe(confirmed);
            comp.cancelled.subscribe(cancelled);
            fixture.componentRef.setInput('versionOptions', [
                { version: '2.2.1', label: '2.2.1' },
                { version: '2.1.0', label: '2.1.0' },
            ]);
            fixture.componentRef.setInput('initialVersion', '2.2.1');
            comp.visible.set(true);
            fixture.detectChanges();
            const handlers = comp as unknown as { onVersionChange(version: unknown): void; confirm(): void; onHidden(): void };

            expect(comp.version()).toBe('2.2.1');
            handlers.onVersionChange('2.1.0');
            handlers.confirm();
            handlers.onHidden();

            expect(confirmed).toHaveBeenCalledWith('2.1.0');
            expect(cancelled).not.toHaveBeenCalled();
        });

        it('cancels when closed and names the test files that use other parts of Ares when turning it off', () => {
            const fixture = TestBed.createComponent(SecurityActivationDialogComponent);
            const comp = fixture.componentInstance;
            const confirmed = vi.fn();
            const cancelled = vi.fn();
            comp.confirmed.subscribe(confirmed);
            comp.cancelled.subscribe(cancelled);
            fixture.componentRef.setInput('mode', 'deactivate');
            fixture.componentRef.setInput('aresApiUses', [
                { location: 'test/de/a/IOTest.java', api: 'IOTester' },
                { location: 'test/de/a/IOTest.java', api: 'StrictTimeout' },
            ]);
            comp.visible.set(true);
            fixture.detectChanges();
            const handlers = comp as unknown as { confirm(): void; onHidden(): void };

            expect(comp.apiFiles()).toEqual(['IOTest']);
            expect(comp.apis()).toEqual(['IOTester', 'StrictTimeout']);
            handlers.onHidden();
            expect(cancelled).toHaveBeenCalledOnce();

            comp.visible.set(true);
            fixture.detectChanges();
            handlers.confirm();
            expect(confirmed).toHaveBeenCalledWith(undefined);
        });
    });
});
