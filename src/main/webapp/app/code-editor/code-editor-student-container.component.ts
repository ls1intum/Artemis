import { Component, OnDestroy, OnInit } from '@angular/core';
import { Observable } from 'rxjs';
import { Subscription } from 'rxjs/Subscription';
import { catchError, flatMap, map, switchMap, tap } from 'rxjs/operators';
import { Participation, ParticipationService } from 'app/entities/participation';
import { CodeEditorContainer } from './code-editor-mode-container.component';
import { TranslateService } from '@ngx-translate/core';
import { ActivatedRoute } from '@angular/router';
import { Result, ResultService } from 'app/entities/result';
import { Feedback } from 'app/entities/feedback';

import { JhiAlertService } from 'ng-jhipster';
import { CodeEditorSessionService, DomainService, DomainType } from 'app/code-editor/service';

@Component({
    selector: 'jhi-code-editor-student',
    templateUrl: './code-editor-student-container.component.html',
    providers: [],
})
export class CodeEditorStudentContainerComponent extends CodeEditorContainer implements OnInit, OnDestroy {
    paramSub: Subscription;
    participation: Participation;

    constructor(
        private resultService: ResultService,
        private domainService: DomainService,
        participationService: ParticipationService,
        translateService: TranslateService,
        route: ActivatedRoute,
        jhiAlertService: JhiAlertService,
        sessionService: CodeEditorSessionService,
    ) {
        super(participationService, translateService, route, jhiAlertService, sessionService);
    }

    /**
     * On init set up the route param subscription.
     * Will load the participation according to participation Id with the latest result and result details.
     */
    ngOnInit(): void {
        this.paramSub = this.route.params.subscribe(params => {
            const participationId = Number(params['participationId']);
            this.loadParticipationWithLatestResult(participationId)
                .pipe(
                    tap(participation => this.domainService.setDomain([DomainType.PARTICIPATION, participation])),
                    tap(participationWithResults => (this.participation = participationWithResults)),
                )

                .subscribe();
        });
    }

    ngOnDestroy() {
        if (this.paramSub) {
            this.paramSub.unsubscribe();
        }
    }

    /**
     * Try to retrieve the participation from cache, otherwise do a REST call to fetch it with the latest result.
     * @param participationId
     */
    loadParticipationWithLatestResult(participationId: number): Observable<Participation | null> {
        return this.participationService.findWithLatestResult(participationId).pipe(
            catchError(() => Observable.of(null)),
            map(res => res && res.body),
            flatMap((participation: Participation) =>
                this.loadLatestResult(participation).pipe(
                    switchMap(result =>
                        result
                            ? this.loadResultDetails(result).pipe(
                                  map(feedback => {
                                      if (feedback) {
                                          participation.results[0].feedbacks = feedback;
                                      }
                                      return participation;
                                  }),
                              )
                            : Observable.of(participation),
                    ),
                ),
            ),
        );
    }

    loadLatestResult(participation: Participation): Observable<Result | null> {
        if (participation && participation.results) {
            return Observable.of(participation.results.length ? participation.results[0] : null);
        }
        return this.resultService
            .findResultsForParticipation(this.participation.exercise.course.id, this.participation.exercise.id, participation.id)
            .pipe(map(({ body: results }) => (results.length ? results.reduce((acc, result) => (result > acc ? result : acc)) : null)));
    }

    /**
     * @function loadResultDetails
     * @desc Fetches details for the result (if we received one) and attach them to the result.
     * Mutates the input parameter result.
     */
    loadResultDetails(result: Result): Observable<Feedback[] | null> {
        return this.resultService.getFeedbackDetailsForResult(result.id).pipe(
            catchError(() => Observable.of(null)),
            map(res => res && res.body),
        );
    }
}
