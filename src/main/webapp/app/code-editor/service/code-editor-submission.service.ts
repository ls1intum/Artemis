import { Injectable, OnDestroy } from '@angular/core';
import { Subject, Subscription } from 'rxjs';
import { map, tap } from 'rxjs/operators';
import { DomainChange, DomainDependent, DomainService } from 'app/code-editor/service/code-editor-domain.service';
import { ProgrammingSubmissionWebsocketService } from 'app/submission/programming-submission-websocket.service';
import { ProgrammingSubmission } from 'app/entities/programming-submission';
import { DomainType } from 'app/code-editor/service/code-editor-repository.service';

/**
 * Wrapper service for using the currently selected participation id in the code-editor for retrieving the submission state.
 */
@Injectable({ providedIn: 'root' })
export class CodeEditorSubmissionService extends DomainDependent implements OnDestroy {
    private participationId: number | null;
    private isBuildingSubject = new Subject<boolean>();
    private submissionSubscription: Subscription;

    constructor(domainService: DomainService, private submissionService: ProgrammingSubmissionWebsocketService) {
        super(domainService);
        this.initDomainSubscription();
    }

    ngOnDestroy() {
        if (this.submissionSubscription) {
            this.submissionSubscription.unsubscribe();
        }
        this.isBuildingSubject.complete();
    }

    setDomain(domain: DomainChange) {
        super.setDomain(domain);
        const [domainType, domainValue] = domain;
        // Subscribe to the submission state of the currently selected participation, map the submission to the isBuilding state.
        if (domainType === DomainType.PARTICIPATION && domainValue.id !== this.participationId) {
            this.participationId = domainValue.id;
            this.submissionSubscription = this.submissionService
                .getLatestPendingSubmission(this.participationId)
                .pipe(
                    map((submission: ProgrammingSubmission) => !!submission),
                    tap((isBuilding: boolean) => this.isBuildingSubject.next(isBuilding)),
                )
                .subscribe();
        } else {
            // There are no submissions for the test repository, so it is never building.
            this.isBuildingSubject.next(false);
        }
    }

    getBuildingState() {
        return this.isBuildingSubject.asObservable();
    }
}
