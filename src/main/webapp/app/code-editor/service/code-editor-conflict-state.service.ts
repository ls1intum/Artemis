import { BehaviorSubject, Observable } from 'rxjs';
import { Injectable, OnDestroy } from '@angular/core';
import { DomainDependent, DomainService, DomainType } from 'app/code-editor/service';
import { JhiWebsocketService } from 'app/core';
import { distinctUntilChanged } from 'rxjs/operators';

export enum GitConflictState {
    CHECKOUT_CONFLICT = 'CHECKOUT_CONFLICT',
    OK = 'OK',
}

export interface IConflictStateService {
    subscribeConflictState: () => Observable<GitConflictState>;
    notifyConflictState: (gitConflictState: GitConflictState) => void;
}

/**
 * This service manages the information about git conflicts of repositories.
 * It offers methods to both subscribe and notify on conflicts.
 */
@Injectable({ providedIn: 'root' })
export class CodeEditorConflictStateService extends DomainDependent implements IConflictStateService, OnDestroy {
    private conflictSubjects: Map<string, BehaviorSubject<GitConflictState>> = new Map();
    private websocketConnections: Map<string, string> = new Map();

    constructor(domainService: DomainService, private jhiWebsocketService: JhiWebsocketService) {
        super(domainService);
        this.initDomainSubscription();
    }

    ngOnDestroy(): void {
        Object.values(this.websocketConnections).forEach(channel => this.jhiWebsocketService.unsubscribe(channel));
    }

    /**
     * Subscribe to git conflict notifications. Does not emit the same value twice in a row (distinctUntilChanged).
     * Emits an OK as a first value.
     */
    subscribeConflictState = () => {
        const domainKey = this.getDomainKey();
        const subject = this.conflictSubjects.get(domainKey);
        if (!subject) {
            const repoSubject = new BehaviorSubject(GitConflictState.OK);
            this.conflictSubjects.set(domainKey, repoSubject);
            return repoSubject.pipe(distinctUntilChanged()) as Observable<GitConflictState>;
        } else {
            return subject.pipe(distinctUntilChanged()) as Observable<GitConflictState>;
        }
    };

    /**
     * Notify all subscribers about a given conflictState.
     *
     * @param gitConflictState
     */
    notifyConflictState = (gitConflictState: GitConflictState) => {
        const domainKey = this.getDomainKey();
        const subject = this.conflictSubjects.get(domainKey);
        if (subject) {
            subject.next(gitConflictState);
        }
    };

    private getDomainKey = () => {
        const [domainType, domainValue] = this.domain;
        return `${domainType === DomainType.PARTICIPATION ? 'participation' : 'test'}-${domainValue.id.toString()}`;
    };
}
