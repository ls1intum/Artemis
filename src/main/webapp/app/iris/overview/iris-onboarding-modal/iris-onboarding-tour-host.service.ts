import { ApplicationRef, EnvironmentInjector, Injector, Service, createComponent, inject } from '@angular/core';
import { DOCUMENT } from '@angular/common';
import { IrisOnboardingModalComponent } from './iris-onboarding-modal.component';
import { IrisOnboardingRef } from './iris-onboarding-ref';

/**
 * Mounts the Iris onboarding tour at the end of the document.
 *
 * Only the welcome screen is a modal dialog. The numbered steps draw their own spotlight, and the user has to click the
 * chat beneath them, so a modal shell around the entire tour would block the very elements the tour points at. Like the PrimeNG
 * dialog it replaces, the tour belongs to the application and stays until it closes itself.
 */
@Service()
export class IrisOnboardingTourHost {
    private readonly applicationRef = inject(ApplicationRef);
    private readonly environmentInjector = inject(EnvironmentInjector);
    private readonly injector = inject(Injector);
    private readonly document = inject(DOCUMENT);

    /** Shows the tour and returns its handle; the tour is removed once it closes. */
    open(): IrisOnboardingRef {
        const ref = new IrisOnboardingRef();
        const elementInjector = Injector.create({ parent: this.injector, providers: [{ provide: IrisOnboardingRef, useValue: ref }] });
        const componentRef = createComponent(IrisOnboardingModalComponent, { environmentInjector: this.environmentInjector, elementInjector });
        this.document.body.appendChild(componentRef.location.nativeElement);
        this.applicationRef.attachView(componentRef.hostView);
        ref.onClose.subscribe({
            complete: () => {
                this.applicationRef.detachView(componentRef.hostView);
                componentRef.destroy();
                componentRef.location.nativeElement.remove();
            },
        });
        return ref;
    }
}
