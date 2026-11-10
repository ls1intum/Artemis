import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { IrisOnboardingTourHost } from './iris-onboarding-tour-host.service';
import { IrisOnboardingModalComponent } from './iris-onboarding-modal.component';
import { IrisOnboardingRef } from './iris-onboarding-ref';
import { IrisOnboardingService } from './iris-onboarding.service';
import { Component, inject, signal } from '@angular/core';
import { Subject } from 'rxjs';

@Component({ template: '<button type="button" (click)="open()">Open tour</button>' })
class TourLauncherComponent {
    private readonly host = inject(IrisOnboardingTourHost);
    ref?: IrisOnboardingRef;

    open(): void {
        this.ref = this.host.open();
    }
}

describe('IrisOnboardingTourHost', () => {
    let host: IrisOnboardingTourHost;

    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [{ provide: IrisOnboardingService, useValue: { onboardingEvent$: new Subject(), currentStep: signal(0) } }] });
        TestBed.overrideComponent(IrisOnboardingModalComponent, { set: { template: '<p class="tour-stub">tour</p>', imports: [], styleUrls: [] } });
        host = TestBed.inject(IrisOnboardingTourHost);
    });

    afterEach(() => {
        vi.restoreAllMocks();
        document.querySelectorAll('jhi-iris-onboarding-modal').forEach((element) => element.remove());
    });

    it('should mount the tour at the end of the document and give it the handle', () => {
        const ref = host.open();

        const element = document.body.lastElementChild;
        expect(element?.tagName.toLowerCase()).toBe('jhi-iris-onboarding-modal');
        expect(element?.querySelector('.tour-stub')).not.toBeNull();
        expect(ref).toBeInstanceOf(IrisOnboardingRef);
    });

    it('should report the result and remove the tour once it closes', () => {
        const ref = host.open();
        const results: unknown[] = [];
        ref.onClose.subscribe((result) => results.push(result));

        ref.close({ action: 'finish' });

        expect(results).toEqual([{ action: 'finish' }]);
        expect(document.querySelector('jhi-iris-onboarding-modal')).toBeNull();
    });

    it('keeps the application-owned tour alive after its launcher is destroyed', () => {
        const launcher = TestBed.createComponent(TourLauncherComponent);
        launcher.detectChanges();
        launcher.nativeElement.querySelector('button').click();
        const ref = launcher.componentInstance.ref!;
        launcher.destroy();

        expect(document.querySelector('jhi-iris-onboarding-modal .tour-stub')).not.toBeNull();
        ref.close();
        expect(document.querySelector('jhi-iris-onboarding-modal')).toBeNull();
    });

    it('should only close once', () => {
        const ref = host.open();
        const results: unknown[] = [];
        ref.onClose.subscribe((result) => results.push(result));

        ref.close();
        ref.close({ action: 'finish' });

        expect(results).toEqual([undefined]);
    });
});
