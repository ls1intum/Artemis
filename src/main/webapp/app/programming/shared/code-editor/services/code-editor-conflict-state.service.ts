import { BehaviorSubject, Observable } from 'rxjs';
import { Injectable } from '@angular/core';
import { distinctUntilChanged } from 'rxjs/operators';
import { DomainType, GitConflictState } from 'app/programming/shared/code-editor/model/code-editor.model';
import { DomainDependentService } from 'app/programming/shared/code-editor/services/code-editor-domain-dependent.service';

export interface IConflictStateService {
    subscribeConflictState: () => Observable<GitConflictState>;
    notifyConflictState: (gitConflictState: GitConflictState) => void;
}

/**
 * This service manages the information about git conflicts of repositories.
 * It offers methods to both subscribe and notify on conflicts.
 */
@Injectable({ providedIn: 'root' })
export class CodeEditorConflictStateService extends DomainDependentService implements IConflictStateService {
    private conflictSubjects: Map<string, BehaviorSubject<GitConflictState>> = new Map();

    constructor() {
        super();
        this.initDomainSubscription();
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
        if (domainType === DomainType.AUXILIARY_REPOSITORY) {
            return `auxiliary-${domainValue.id!.toString()}`;
        }
        return `${domainType === DomainType.PARTICIPATION ? 'participation' : 'test'}-${domainValue.id!.toString()}`;
    };
}
