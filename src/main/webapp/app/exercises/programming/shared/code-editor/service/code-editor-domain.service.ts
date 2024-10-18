import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import {
    DomainAuxiliaryRepositoryChange,
    DomainChange,
    DomainParticipationChange,
    DomainTestRepositoryChange,
} from 'app/exercises/programming/shared/code-editor/model/code-editor.model';

/**
 * This service provides subscribing services with the most recently selected domain (participation vs repository).
 * This is used to make components independent of the domains, as they can just call the method of an injected service without passing the domain.
 */
@Injectable({ providedIn: 'root' })
export class DomainService {
    protected domain: DomainChange;
    private subject = new BehaviorSubject<DomainParticipationChange | DomainTestRepositoryChange | DomainAuxiliaryRepositoryChange | undefined>(undefined);

    constructor() {}

    /**
     * Sets domain and subject.next according to parameter.
     * @param domain - defines new domain of the service.
     */
    public setDomain(domain: DomainChange) {
        this.domain = domain;
        this.subject.next(domain);
    }

    /**
     * Subscribes to current subject.
     */
    public subscribeDomainChange(): Observable<DomainChange | undefined> {
        return this.subject;
    }
}
