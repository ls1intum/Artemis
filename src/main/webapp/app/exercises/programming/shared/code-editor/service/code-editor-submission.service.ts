import { Injectable, OnDestroy } from '@angular/core';
import { AlertService } from 'app/core/util/alert.service';
import { Subject, Subscription } from 'rxjs';
import { map, tap } from 'rxjs/operators';
import { DomainService } from 'app/exercises/programming/shared/code-editor/service/code-editor-domain.service';
import { ProgrammingSubmissionService, ProgrammingSubmissionState } from 'app/exercises/programming/participate/programming-submission.service';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { SolutionProgrammingExerciseParticipation } from 'app/entities/participation/solution-programming-exercise-participation.model';
import { DomainChange, DomainType } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';
import { DomainDependentService } from 'app/exercises/programming/shared/code-editor/service/code-editor-domain-dependent.service';

/**
 * Wrapper service for using the currently selected participation id in the code-editor for retrieving the submission state.
 */
@Injectable({ providedIn: 'root' })
export class CodeEditorSubmissionService extends DomainDependentService implements OnDestroy {
    private participationId?: number;
    private exerciseId?: number;
    private isBuildingSubject = new Subject<boolean>();
    private submissionSubscription: Subscription;

    constructor(domainService: DomainService, private submissionService: ProgrammingSubmissionService, private alertService: AlertService) {
        super(domainService);
        this.initDomainSubscription();
    }

    /**
     * Completes building subject. If there are subscriptions unsubscribe form them.
     */
    ngOnDestroy() {
        if (this.submissionSubscription) {
            this.submissionSubscription.unsubscribe();
        }
        this.isBuildingSubject.complete();
    }

    /**
     * Calls setDomain of super and updates according to parameter.
     * @param domain - defines new domain of super and variables of current service.
     */
    setDomain(domain: DomainChange) {
        super.setDomain(domain);
        const [domainType, domainValue] = domain;
        // Subscribe to the submission state of the currently selected participation, map the submission to the isBuilding state.
        if (domainType === DomainType.PARTICIPATION) {
            this.participationId = domainValue.id;
            // There is no differentiation between the participation types atm.
            // This could be implemented in the domain service, but this would make the implementation more complicated, too.
            this.exerciseId = (domainValue as StudentParticipation).exercise
                ? (domainValue as StudentParticipation).exercise?.id
                : (domainValue as SolutionProgrammingExerciseParticipation).programmingExercise?.id;
            const personalParticipation = !!(domainValue as StudentParticipation).exercise;
            if (this.participationId && this.exerciseId) {
                this.submissionSubscription = this.submissionService
                    .getLatestPendingSubmissionByParticipationId(this.participationId, this.exerciseId, personalParticipation)
                    .pipe(
                        tap(({ submissionState }) => submissionState === ProgrammingSubmissionState.HAS_FAILED_SUBMISSION && this.onError()),
                        map(({ submission }) => !!submission),
                        tap((isBuilding: boolean) => this.isBuildingSubject.next(isBuilding)),
                    )
                    .subscribe();
            }
        } else if (domainType === DomainType.TEST_REPOSITORY) {
            // There are no submissions for the test repository, so it is never building.
            this.isBuildingSubject.next(false);
        }
    }

    /**
     * Call an error if there is one.
     */
    onError() {
        this.alertService.error('artemisApp.submission.resultTimeout');
    }

    /**
     * Returns building state of this service.
     */
    getBuildingState() {
        return this.isBuildingSubject.asObservable();
    }
}
