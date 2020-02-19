import { OnDestroy } from '@angular/core';
import { DomainChange } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';
import { Subscription } from 'rxjs';
import { filter, tap } from 'rxjs/operators';
import { DomainService } from 'app/exercises/programming/shared/code-editor/service/code-editor-domain.service';

/**
 * Service that can be extended to automatically receive updates on changed domains.
 */
export abstract class DomainDependentService implements OnDestroy {
    protected domain: DomainChange;
    protected domainChangeSubscription: Subscription;

    constructor(private domainService: DomainService) {}

    initDomainSubscription() {
        this.domainChangeSubscription = this.domainService
            .subscribeDomainChange()
            .pipe(
                filter(domain => !!domain),
                tap((domain: DomainChange) => {
                    this.setDomain(domain);
                }),
            )
            .subscribe();
    }

    setDomain(domain: DomainChange) {
        this.domain = domain;
    }

    ngOnDestroy() {
        if (this.domainChangeSubscription) {
            this.domainChangeSubscription.unsubscribe();
        }
    }
}
