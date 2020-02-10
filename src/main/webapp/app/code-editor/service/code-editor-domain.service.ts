import { Injectable, OnDestroy } from '@angular/core';
import { SERVER_API_URL } from 'app/app.constants';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, Subscription } from 'rxjs';
import { filter, tap } from 'rxjs/operators';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { DomainType } from 'app/code-editor/service/code-editor-repository.service';
import { ProgrammingExercise } from 'app/entities/programming-exercise/programming-exercise.model';
import { TemplateProgrammingExerciseParticipation } from 'app/entities/participation/template-programming-exercise-participation.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { SolutionProgrammingExerciseParticipation } from 'app/entities/participation/solution-programming-exercise-participation.model';

export type DomainParticipationChange = [DomainType.PARTICIPATION, StudentParticipation | TemplateProgrammingExerciseParticipation | SolutionProgrammingExerciseParticipation];
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
            case DomainType.PARTICIPATION:
                this.restResourceUrl = `${this.restResourceUrlBase}/repository/${domainValue.id}`;
                this.websocketResourceUrlSend = `${this.websocketResourceUrlBase}/repository/${domainValue.id}`;
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
