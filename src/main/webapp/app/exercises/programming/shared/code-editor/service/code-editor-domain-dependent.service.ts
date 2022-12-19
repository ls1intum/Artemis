import { Injectable, OnDestroy } from '@angular/core';
import { DomainChange } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';
import { Subscription } from 'rxjs';
import { filter, tap } from 'rxjs/operators';
import { DomainService } from 'app/exercises/programming/shared/code-editor/service/code-editor-domain.service';

/**
 * Service that can be extended to automatically receive updates on changed domains.
 */
@Injectable({ providedIn: 'root' })
export abstract class DomainDependentService implements OnDestroy {
    protected domain: DomainChange;
    protected domainChangeSubscription: Subscription;

    constructor(private domainService: DomainService) {}

    /**
     * Initializes a domain subscription.
     */
    initDomainSubscription() {
        this.domainChangeSubscription = this.domainService
            .subscribeDomainChange()
            .pipe(
                filter((domain) => !!domain),
                tap((domain: DomainChange) => {
                    this.setDomain(domain);
                }),
            )
            .subscribe();
    }

    /**
     * Sets domain according to the parameter.
     * @param domain - enum that defines the type of the domain.
     */
    setDomain(domain: DomainChange) {
        this.domain = domain;
    }

    /**
     * Unsubscribe from current subscription.
     */
    ngOnDestroy() {
        if (this.domainChangeSubscription) {
            this.domainChangeSubscription.unsubscribe();
        }
    }
}
