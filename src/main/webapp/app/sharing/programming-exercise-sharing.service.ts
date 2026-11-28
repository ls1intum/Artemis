import { HttpClient, HttpResponse } from '@angular/common/http';
import { convertProgrammingExerciseResponseDatesFromServer, toProgrammingExerciseSetupPayload } from 'app/programming/api/programming-exercise-payload';
import { Service, inject } from '@angular/core';
import type { ProgrammingExercise, ProgrammingExerciseExportAction } from 'app/programming/api/programming-exercise-extensions';
import { AlertService } from 'app/foundation/service/alert.service';
import { SharingInfo, ShoppingBasket } from 'app/sharing/sharing.model';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

export type EntityResponseType = HttpResponse<ProgrammingExercise>;
/** the programming exercise sharing service */
@Service()
export class ProgrammingExerciseSharingService {
    private readonly http = inject(HttpClient);
    private readonly alerts = inject(AlertService);

    protected readonly baseSharingConfigUrl = 'api/core/sharing/config';
    protected readonly resourceUrl = 'api/programming/sharing/import';
    protected readonly resourceUrlBasket = 'api/programming/sharing/import/basket/';
    protected readonly resourceUrlExport = 'api/programming/sharing/export';
    protected readonly resourceUrlSetupImport = 'api/programming/sharing/setup-import';

    /**
     * loads the Shopping Basket via the Service
     */
    getSharedExercises(sharingInfo: SharingInfo): Observable<ShoppingBasket> {
        // Validate required parameters
        if (!sharingInfo.basketToken || !sharingInfo.apiBaseURL || !sharingInfo.checksum || !sharingInfo.returnURL) {
            throw new Error('Missing required sharing information: basketToken, apiBaseURL, sharingInfo.checksum and sharingInfo.returnURL are required');
        }
        return this.http
            .get<ShoppingBasket>(this.resourceUrl + '/basket', {
                params: { basketToken: sharingInfo.basketToken, returnURL: sharingInfo.returnURL, apiBaseURL: sharingInfo.apiBaseURL, checksum: sharingInfo.checksum },
                observe: 'response',
            })
            .pipe(map((response: HttpResponse<ShoppingBasket>) => response.body!));
    }

    loadDetailsForExercises(sharingInfo: SharingInfo): Observable<ProgrammingExercise> {
        return this.http
            .post<ProgrammingExercise>(this.resourceUrlBasket + 'exercise-details', sharingInfo, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => convertProgrammingExerciseResponseDatesFromServer(res).body!));
    }

    /**
     * Sets a new programming exercise up
     * @param programmingExercise which should be setup
     * @param courseId id of the course in which the exercise should be imported
     * @param sharingInfo sharing related data needed for the import
     */
    setUpFromSharingImport(programmingExercise: ProgrammingExercise, courseId: number, sharingInfo: SharingInfo): Observable<EntityResponseType> {
        const exerciseCopy = toProgrammingExerciseSetupPayload(programmingExercise);
        delete exerciseCopy.templateParticipation?.submissions;
        delete exerciseCopy.solutionParticipation?.submissions;
        return this.http
            .post<ProgrammingExercise>(this.resourceUrlSetupImport, { exercise: exerciseCopy, courseId, sharingInfo }, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => convertProgrammingExerciseResponseDatesFromServer(res)));
    }

    /**
     * Used to initiate export to Sharing.
     * This returns a URL to Sharing with a callback as parameter to the exposed exercise in Artemis
     * @param programmingExerciseId id of the exercise to export
     * @param callBackUrl used to redirect back after export has been completed
     */
    exportProgrammingExerciseToSharing(programmingExerciseId: number, callBackUrl: string): Observable<HttpResponse<string>> {
        return this.http.post<string>(`${this.resourceUrlExport}?exerciseId=${programmingExerciseId}`, callBackUrl, {
            observe: 'response',
        });
    }

    /** A configured platform can accept exports once it has connected to Artemis. */
    getExportAction(): Observable<ProgrammingExerciseExportAction | undefined> {
        return this.http
            .get<boolean>(`${this.baseSharingConfigUrl}/is-enabled`)
            .pipe(map((connected) => (connected ? { label: 'artemisApp.sharing.export', run: (id: number) => this.openExport(id) } : undefined)));
    }

    private openExport(exerciseId: number) {
        this.exportProgrammingExerciseToSharing(exerciseId, window.location.href).subscribe({
            next: (response) => {
                if (response.body) {
                    window.location.href = response.body;
                } else {
                    this.alerts.error('artemisApp.sharing.error.noRedirect');
                }
            },
            error: (error) => this.alerts.error('artemisApp.sharing.error.export', { message: error?.error?.message || error?.error || 'Unknown error' }),
        });
    }
}
