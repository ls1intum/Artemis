import { ProgrammingExercise } from 'app/entities/programming-exercise';
import { Injectable, OnDestroy } from '@angular/core';
import { SERVER_API_URL } from 'app/app.constants';
import { HttpClient } from '@angular/common/http';
import { JhiWebsocketService } from 'app/core';
import { BehaviorSubject, Observable, Subscription } from 'rxjs';
import { filter, tap } from 'rxjs/operators';
import { DomainParticipationChange, DomainType } from 'app/code-editor/service';
import { Participation } from 'app/entities/participation';

export type DomainParticipationChange =
    | [DomainType.SOLUTION_PARTICIPATION, Participation]
    | [DomainType.TEMPLATE_PARTICIPATION, Participation]
    | [DomainType.STUDENT_PARTICIPATION, Participation];
export type DomainTestRepositoryChange = [DomainType.TEST_REPOSITORY, ProgrammingExercise];
export type DomainChange = DomainParticipationChange | DomainTestRepositoryChange;

/**
 * This service provides subscribing services with the most recently selected domain (participation vs repository).
 * This is used to make components independent of the domains, as they can just call the method of an injected service without passing the domain.
 */
@Injectable({ providedIn: 'root' })
export class DomainService {
    protected domain: DomainChange;
    private subject = new BehaviorSubject<DomainParticipationChange | DomainTestRepositoryChange | null>(null);

    public setDomain(domain: DomainChange) {
        this.domain = domain;
        this.subject.next(domain);
    }

    public subscribeDomainChange(): Observable<DomainChange | null> {
        return this.subject;
    }
}

/**
 * Service that can be extended to automatically receive updates on changed domains.
 */
export abstract class DomainDependent implements OnDestroy {
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

/**
 * Service that can be extended to update rest endpoint urls with the received domain information.
 */
export abstract class DomainDependentEndpoint extends DomainDependent {
    private restResourceUrlBase = `${SERVER_API_URL}/api`;
    protected restResourceUrl: string | null;
    private websocketResourceUrlBase = '/topic';
    protected websocketResourceUrlSend: string | null;
    protected websocketResourceUrlReceive: string | null;

    constructor(protected http: HttpClient, protected jhiWebsocketService: JhiWebsocketService, domainService: DomainService) {
        super(domainService);
        this.initDomainSubscription();
    }

    setDomain(domain: DomainChange) {
        super.setDomain(domain);
        const [domainType, domainValue] = this.domain;
        switch (domainType) {
            case DomainType.SOLUTION_PARTICIPATION:
                this.restResourceUrl = `${this.restResourceUrlBase}/solution-repository/${domainValue.id}`;
                this.websocketResourceUrlSend = `${this.websocketResourceUrlBase}/solution-repository/${domainValue.id}`;
                this.websocketResourceUrlReceive = `/user${this.websocketResourceUrlSend}`;
                break;
            case DomainType.TEMPLATE_PARTICIPATION:
                this.restResourceUrl = `${this.restResourceUrlBase}/template-repository/${domainValue.id}`;
                this.websocketResourceUrlSend = `${this.websocketResourceUrlBase}/template-repository/${domainValue.id}`;
                this.websocketResourceUrlReceive = `/user${this.websocketResourceUrlSend}`;
                break;
            case DomainType.STUDENT_PARTICIPATION:
                this.restResourceUrl = `${this.restResourceUrlBase}/student-repository/${domainValue.id}`;
                this.websocketResourceUrlSend = `${this.websocketResourceUrlBase}/student-repository/${domainValue.id}`;
                this.websocketResourceUrlReceive = `/user${this.websocketResourceUrlSend}`;
                break;
            case DomainType.TEST_REPOSITORY:
                this.restResourceUrl = `${this.restResourceUrlBase}/test-repository/${domainValue.id}`;
                this.websocketResourceUrlSend = `${this.websocketResourceUrlBase}/test-repository/${domainValue.id}`;
                this.websocketResourceUrlReceive = `/user${this.websocketResourceUrlSend}`;
                break;
            default:
                this.restResourceUrl = null;
                this.websocketResourceUrlSend = null;
                this.websocketResourceUrlReceive = null;
        }
    }
}
