import { DestroyRef } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateService } from '@ngx-translate/core';
import { TumAetUiDialogService } from '@tumaet/ui-angular';
import { MockProvider } from 'ng-mocks';
import { BehaviorSubject, Subject, throwError } from 'rxjs';
import { SKIP_HTTP_ERROR_ALERT } from 'app/core/interceptor/errorhandler.interceptor';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { DELETION_STATUS_POLL_INTERVAL_MS, EntityDeletionService } from 'app/shared-ui/delete-dialog/service/entity-deletion.service';
import { ConnectionState, WebsocketService } from 'app/foundation/service/websocket.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockAlertService } from 'test/helpers/mocks/service/mock-alert.service';
import { EntityDeletionProgress, EntityDeletionStatus, EntityDeletionTarget, EntityDeletionType } from 'app/shared-ui/delete-dialog/entity-deletion-progress.model';
import { EntityDeletionProgressDialogComponent } from 'app/shared-ui/delete-dialog/component/entity-deletion-progress-dialog.component';

describe('EntityDeletionService', () => {
    const exerciseTarget: EntityDeletionTarget = { entityType: EntityDeletionType.EXERCISE, entityId: 7, title: 'Sorting', successAlertKey: 'artemisApp.textExercise.deleted' };
    const examTarget: EntityDeletionTarget = {
        entityType: EntityDeletionType.EXAM,
        entityId: 3,
        courseId: 1,
        examId: 3,
        title: 'Final',
        successAlertKey: 'artemisApp.exam.deleted',
    };
    const groupTarget: EntityDeletionTarget = {
        entityType: EntityDeletionType.EXERCISE_GROUP,
        entityId: 9,
        courseId: 1,
        examId: 3,
        title: 'Group 1',
        successAlertKey: 'artemisApp.exerciseGroup.deleted',
    };

    let service: EntityDeletionService;
    let http: HttpClient;
    let httpMock: HttpTestingController;
    let topic$: Subject<EntityDeletionProgress>;
    let connection$: BehaviorSubject<ConnectionState>;
    let websocketSubscribe: ReturnType<typeof vi.fn>;
    let dialogOpen: ReturnType<typeof vi.fn>;
    let dialogClose: ReturnType<typeof vi.fn>;
    let alertService: AlertService;
    /** Lifetime of the caller of resume; the callbacks run when the caller is destroyed. */
    let destroyCallbacks: (() => void)[];
    let destroyRef: DestroyRef;

    const progress = (status: EntityDeletionStatus, overrides: Partial<EntityDeletionProgress> = {}): EntityDeletionProgress => ({
        entityType: EntityDeletionType.EXERCISE,
        entityId: 7,
        status,
        stepsCompleted: 1,
        totalSteps: 4,
        itemsProcessed: 0,
        totalItems: 0,
        weightedProgressPercent: 25,
        ...overrides,
    });

    beforeEach(() => {
        vi.useFakeTimers();
        vi.setSystemTime(new Date('2026-11-07T12:00:00Z'));
        topic$ = new Subject<EntityDeletionProgress>();
        connection$ = new BehaviorSubject<ConnectionState>(new ConnectionState(true, true));
        websocketSubscribe = vi.fn().mockReturnValue(topic$);
        dialogClose = vi.fn();
        dialogOpen = vi.fn().mockReturnValue({ close: dialogClose });
        destroyCallbacks = [];
        destroyRef = {
            onDestroy: (callback: () => void) => {
                destroyCallbacks.push(callback);
                return () => undefined;
            },
        } as DestroyRef;
        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                MockProvider(WebsocketService, { subscribe: websocketSubscribe as any, connectionState: connection$.asObservable() }),
                MockProvider(TumAetUiDialogService, { open: dialogOpen as any }),
                { provide: AlertService, useClass: MockAlertService },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        });
        service = TestBed.inject(EntityDeletionService);
        http = TestBed.inject(HttpClient);
        httpMock = TestBed.inject(HttpTestingController);
        alertService = TestBed.inject(AlertService);
        vi.spyOn(alertService, 'success');
        vi.spyOn(alertService, 'error');
        vi.spyOn(alertService, 'info');
    });

    afterEach(() => {
        vi.useRealTimers();
    });

    it.each([
        [exerciseTarget, '/topic/exercises/7/deletion-progress', 'api/exercise/exercises/7/deletion-progress'],
        [examTarget, '/topic/courses/1/exams/3/deletion-progress', 'api/exam/courses/1/exams/3/deletion-progress'],
        [groupTarget, '/topic/courses/1/exams/3/deletion-progress', 'api/exam/courses/1/exams/3/deletion-progress'],
    ])('derives the topic and the status endpoint of %j', (target, topic, statusUrl) => {
        expect(service.topic(target)).toBe(topic);
        expect(service.statusUrl(target)).toBe(statusUrl);
    });

    it('subscribes to the topic before it sends the request', () => {
        service.delete(exerciseTarget, http.delete('api/text/text-exercises/7')).subscribe();

        expect(websocketSubscribe).toHaveBeenCalledWith('/topic/exercises/7/deletion-progress');
        // the request is only sent when the caller subscribed, which the service did after listening
        httpMock.expectOne({ method: 'DELETE', url: 'api/text/text-exercises/7' });
    });

    it('opens the progress dialog once the server accepted the deletion and reads the status once', () => {
        service.delete(exerciseTarget, http.delete('api/text/text-exercises/7')).subscribe();
        expect(dialogOpen).not.toHaveBeenCalled();

        httpMock.expectOne('api/text/text-exercises/7').flush(null, { status: 202, statusText: 'Accepted' });

        expect(dialogOpen).toHaveBeenCalledOnce();
        expect(dialogOpen.mock.calls[0][0]).toBe(EntityDeletionProgressDialogComponent);
        const options = dialogOpen.mock.calls[0][1];
        expect(options.closable).toBe(false);
        expect(options.closeOnEscape).toBe(false);
        expect(options.dismissableMask).toBe(false);
        expect(options.ariaLabel).toBeDefined();
        httpMock.expectOne('api/exercise/exercises/7/deletion-progress').flush(null, { status: 204, statusText: 'No Content' });
    });

    it('feeds the dialog with the progress that arrives over the websocket', () => {
        service.delete(exerciseTarget, http.delete('api/text/text-exercises/7')).subscribe();
        httpMock.expectOne('api/text/text-exercises/7').flush(null, { status: 202, statusText: 'Accepted' });
        httpMock.expectOne('api/exercise/exercises/7/deletion-progress').flush(null, { status: 204, statusText: 'No Content' });
        const data = dialogOpen.mock.calls[0][1].data;
        expect(data.progress()).toBeUndefined();

        topic$.next(progress(EntityDeletionStatus.IN_PROGRESS, { currentStep: 'participations', weightedProgressPercent: 40 }));

        expect(data.progress()?.currentStep).toBe('participations');
        expect(data.progress()?.weightedProgressPercent).toBe(40);
    });

    it('emits the final progress, shows the success alert and stops listening when the deletion completed', () => {
        const results: EntityDeletionProgress[] = [];
        let completed = false;
        service.delete(exerciseTarget, http.delete('api/text/text-exercises/7')).subscribe({ next: (value) => results.push(value), complete: () => (completed = true) });
        httpMock.expectOne('api/text/text-exercises/7').flush(null, { status: 202, statusText: 'Accepted' });
        httpMock.expectOne('api/exercise/exercises/7/deletion-progress').flush(null, { status: 204, statusText: 'No Content' });

        topic$.next(progress(EntityDeletionStatus.IN_PROGRESS));
        expect(results).toHaveLength(0);
        topic$.next(progress(EntityDeletionStatus.COMPLETED, { weightedProgressPercent: 100 }));

        expect(results).toHaveLength(1);
        expect(results[0].status).toBe(EntityDeletionStatus.COMPLETED);
        expect(completed).toBe(true);
        expect(alertService.success).toHaveBeenCalledWith('artemisApp.textExercise.deleted', { param: 'Sorting' });
        expect(topic$.observed).toBe(false);
    });

    it('emits a failed deletion without a success alert', () => {
        const results: EntityDeletionProgress[] = [];
        service.delete(exerciseTarget, http.delete('api/text/text-exercises/7')).subscribe((value) => results.push(value));
        httpMock.expectOne('api/text/text-exercises/7').flush(null, { status: 202, statusText: 'Accepted' });
        httpMock.expectOne('api/exercise/exercises/7/deletion-progress').flush(null, { status: 204, statusText: 'No Content' });

        topic$.next(progress(EntityDeletionStatus.FAILED, { errorMessage: 'boom' }));

        expect(results).toHaveLength(1);
        expect(results[0].errorMessage).toBe('boom');
        expect(alertService.success).not.toHaveBeenCalled();
    });

    it('takes the outcome from the status endpoint when the websocket message was lost', () => {
        const results: EntityDeletionProgress[] = [];
        service.delete(exerciseTarget, http.delete('api/text/text-exercises/7')).subscribe((value) => results.push(value));
        httpMock.expectOne('api/text/text-exercises/7').flush(null, { status: 202, statusText: 'Accepted' });

        httpMock.expectOne('api/exercise/exercises/7/deletion-progress').flush(progress(EntityDeletionStatus.COMPLETED, { weightedProgressPercent: 100 }));

        expect(results).toHaveLength(1);
        expect(results[0].status).toBe(EntityDeletionStatus.COMPLETED);
    });

    it('treats a 404 of the status endpoint as completed for an exercise, because the entity is gone', () => {
        const results: EntityDeletionProgress[] = [];
        service.delete(exerciseTarget, http.delete('api/text/text-exercises/7')).subscribe((value) => results.push(value));
        httpMock.expectOne('api/text/text-exercises/7').flush(null, { status: 202, statusText: 'Accepted' });

        httpMock.expectOne('api/exercise/exercises/7/deletion-progress').flush(null, { status: 404, statusText: 'Not Found' });

        expect(results).toHaveLength(1);
        expect(results[0].status).toBe(EntityDeletionStatus.COMPLETED);
        expect(alertService.success).toHaveBeenCalledWith('artemisApp.textExercise.deleted', { param: 'Sorting' });
        expect(topic$.observed).toBe(false);
    });

    it('treats a 404 of the status endpoint as completed for an exam', () => {
        const results: EntityDeletionProgress[] = [];
        service.delete(examTarget, http.delete('api/exam/courses/1/exams/3')).subscribe((value) => results.push(value));
        httpMock.expectOne('api/exam/courses/1/exams/3').flush(null, { status: 202, statusText: 'Accepted' });

        httpMock.expectOne('api/exam/courses/1/exams/3/deletion-progress').flush(null, { status: 404, statusText: 'Not Found' });

        expect(results).toHaveLength(1);
        expect(results[0].status).toBe(EntityDeletionStatus.COMPLETED);
    });

    it('does not treat a 404 of the status endpoint as completed for an exercise group, whose exam stays', () => {
        const results: EntityDeletionProgress[] = [];
        service.delete(groupTarget, http.delete('api/exam/courses/1/exams/3/exercise-groups/9')).subscribe((value) => results.push(value));
        httpMock.expectOne('api/exam/courses/1/exams/3/exercise-groups/9').flush(null, { status: 202, statusText: 'Accepted' });

        httpMock.expectOne('api/exam/courses/1/exams/3/deletion-progress').flush(null, { status: 404, statusText: 'Not Found' });

        expect(results).toHaveLength(0);
        expect(topic$.observed).toBe(true);
    });

    it('does not let failing status reads raise an error alert', () => {
        service.delete(exerciseTarget, http.delete('api/text/text-exercises/7')).subscribe();
        httpMock.expectOne('api/text/text-exercises/7').flush(null, { status: 202, statusText: 'Accepted' });
        const statusRequest = httpMock.expectOne('api/exercise/exercises/7/deletion-progress');
        expect(statusRequest.request.context.get(SKIP_HTTP_ERROR_ALERT)).toBe(true);
        statusRequest.flush(null, { status: 404, statusText: 'Not Found' });

        service.resume(exerciseTarget, destroyRef).subscribe();
        const resumeRequest = httpMock.expectOne('api/exercise/exercises/7/deletion-progress');
        expect(resumeRequest.request.context.get(SKIP_HTTP_ERROR_ALERT)).toBe(true);
        resumeRequest.flush(null, { status: 404, statusText: 'Not Found' });
    });

    it('never moves the progress bar backwards when messages arrive out of order', () => {
        service.delete(exerciseTarget, http.delete('api/text/text-exercises/7')).subscribe();
        httpMock.expectOne('api/text/text-exercises/7').flush(null, { status: 202, statusText: 'Accepted' });
        httpMock.expectOne('api/exercise/exercises/7/deletion-progress').flush(null, { status: 204, statusText: 'No Content' });
        const data = dialogOpen.mock.calls[0][1].data;

        topic$.next(progress(EntityDeletionStatus.IN_PROGRESS, { weightedProgressPercent: 60 }));
        topic$.next(progress(EntityDeletionStatus.IN_PROGRESS, { weightedProgressPercent: 40 }));

        expect(data.progress()?.weightedProgressPercent).toBe(60);
    });

    it('ignores in-progress messages that arrive after the terminal message', () => {
        const results: EntityDeletionProgress[] = [];
        service.delete(exerciseTarget, http.delete('api/text/text-exercises/7')).subscribe((value) => results.push(value));
        httpMock.expectOne('api/text/text-exercises/7').flush(null, { status: 202, statusText: 'Accepted' });
        httpMock.expectOne('api/exercise/exercises/7/deletion-progress').flush(null, { status: 204, statusText: 'No Content' });
        const data = dialogOpen.mock.calls[0][1].data;

        topic$.next(progress(EntityDeletionStatus.COMPLETED, { weightedProgressPercent: 100 }));
        topic$.next(progress(EntityDeletionStatus.IN_PROGRESS, { weightedProgressPercent: 70 }));

        expect(results).toHaveLength(1);
        expect(data.progress()?.status).toBe(EntityDeletionStatus.COMPLETED);
        expect(data.progress()?.weightedProgressPercent).toBe(100);
    });

    it.each([2, 5, 7])('follows a deletion with %i steps without assuming a fixed step count', (totalSteps) => {
        const results: EntityDeletionProgress[] = [];
        service.delete(exerciseTarget, http.delete('api/text/text-exercises/7')).subscribe((value) => results.push(value));
        httpMock.expectOne('api/text/text-exercises/7').flush(null, { status: 202, statusText: 'Accepted' });
        httpMock.expectOne('api/exercise/exercises/7/deletion-progress').flush(null, { status: 204, statusText: 'No Content' });

        topic$.next(progress(EntityDeletionStatus.IN_PROGRESS, { totalSteps, stepsCompleted: totalSteps - 1, weightedProgressPercent: 90 }));
        topic$.next(progress(EntityDeletionStatus.COMPLETED, { totalSteps, stepsCompleted: totalSteps, weightedProgressPercent: 100 }));

        expect(results).toHaveLength(1);
        expect(results[0].totalSteps).toBe(totalSteps);
    });

    it('ignores messages about other entities that share the topic', () => {
        const results: EntityDeletionProgress[] = [];
        service.delete(examTarget, http.delete('api/exam/courses/1/exams/3')).subscribe((value) => results.push(value));
        httpMock.expectOne('api/exam/courses/1/exams/3').flush(null, { status: 202, statusText: 'Accepted' });
        httpMock.expectOne('api/exam/courses/1/exams/3/deletion-progress').flush(null, { status: 204, statusText: 'No Content' });

        topic$.next(progress(EntityDeletionStatus.COMPLETED, { entityType: EntityDeletionType.EXERCISE_GROUP, entityId: 9 }));
        expect(results).toHaveLength(0);

        topic$.next(progress(EntityDeletionStatus.COMPLETED, { entityType: EntityDeletionType.EXAM, entityId: 3 }));
        expect(results).toHaveLength(1);
    });

    it('converts the start time of the progress to a date', () => {
        service.delete(exerciseTarget, http.delete('api/text/text-exercises/7')).subscribe();
        httpMock.expectOne('api/text/text-exercises/7').flush(null, { status: 202, statusText: 'Accepted' });
        httpMock.expectOne('api/exercise/exercises/7/deletion-progress').flush(null, { status: 204, statusText: 'No Content' });

        topic$.next(progress(EntityDeletionStatus.IN_PROGRESS, { startedAt: '2026-11-07T10:00:00Z' as any }));

        const startedAt = dialogOpen.mock.calls[0][1].data.progress().startedAt;
        expect(startedAt.toISOString()).toBe('2026-11-07T10:00:00.000Z');
    });

    it('passes on the error of the request and shows no dialog when the server refuses to start the deletion', () => {
        let error: unknown;
        service.delete(exerciseTarget, http.delete('api/text/text-exercises/7')).subscribe({ error: (e) => (error = e) });

        httpMock.expectOne('api/text/text-exercises/7').flush({ errorKey: 'deletionInProgress' }, { status: 409, statusText: 'Conflict' });

        expect((error as { status: number }).status).toBe(409);
        expect(dialogOpen).not.toHaveBeenCalled();
        expect(topic$.observed).toBe(false);
    });

    it('stops listening when the request fails immediately', () => {
        let failed = false;
        service
            .delete(
                exerciseTarget,
                throwError(() => new Error('offline')),
            )
            .subscribe({ error: () => (failed = true) });

        expect(failed).toBe(true);
        expect(topic$.observed).toBe(false);
    });

    it('neither opens a dialog nor shows an alert for silent deletions but still reports the outcome', () => {
        const results: EntityDeletionProgress[] = [];
        service.delete(exerciseTarget, http.delete('api/text/text-exercises/7'), { silent: true }).subscribe((value) => results.push(value));
        httpMock.expectOne('api/text/text-exercises/7').flush(null, { status: 202, statusText: 'Accepted' });
        httpMock.expectOne('api/exercise/exercises/7/deletion-progress').flush(null, { status: 204, statusText: 'No Content' });

        topic$.next(progress(EntityDeletionStatus.COMPLETED));

        expect(dialogOpen).not.toHaveBeenCalled();
        expect(alertService.success).not.toHaveBeenCalled();
        expect(results).toHaveLength(1);
    });

    it('reports the failure of a silent deletion with an alert itself, because its caller may be gone by then', () => {
        const results: EntityDeletionProgress[] = [];
        service.delete(exerciseTarget, http.delete('api/text/text-exercises/7'), { silent: true }).subscribe((value) => results.push(value));
        httpMock.expectOne('api/text/text-exercises/7').flush(null, { status: 202, statusText: 'Accepted' });
        httpMock.expectOne('api/exercise/exercises/7/deletion-progress').flush(null, { status: 204, statusText: 'No Content' });

        topic$.next(progress(EntityDeletionStatus.FAILED, { errorMessage: 'boom' }));

        expect(alertService.error).toHaveBeenCalledOnce();
        expect(alertService.error).toHaveBeenCalledWith('artemisApp.deletionProgress.failedInBackground', { title: 'Sorting', error: 'boom' });
        expect(results).toHaveLength(1);
    });

    describe('status fallback', () => {
        const startDeletion = (results: EntityDeletionProgress[]) => {
            service.delete(exerciseTarget, http.delete('api/text/text-exercises/7')).subscribe((value) => results.push(value));
            httpMock.expectOne('api/text/text-exercises/7').flush(null, { status: 202, statusText: 'Accepted' });
            httpMock.expectOne('api/exercise/exercises/7/deletion-progress').flush(null, { status: 204, statusText: 'No Content' });
        };

        it('polls the status endpoint while the deletion runs and ends it when the message was lost', () => {
            const results: EntityDeletionProgress[] = [];
            startDeletion(results);

            vi.advanceTimersByTime(DELETION_STATUS_POLL_INTERVAL_MS);
            httpMock.expectOne('api/exercise/exercises/7/deletion-progress').flush(progress(EntityDeletionStatus.IN_PROGRESS));
            expect(results).toHaveLength(0);

            vi.advanceTimersByTime(DELETION_STATUS_POLL_INTERVAL_MS);
            httpMock.expectOne('api/exercise/exercises/7/deletion-progress').flush(progress(EntityDeletionStatus.COMPLETED, { weightedProgressPercent: 100 }));

            expect(results).toHaveLength(1);
            expect(results[0].status).toBe(EntityDeletionStatus.COMPLETED);
        });

        it('ends the deletion when the polled status vanished because the entity is gone', () => {
            const results: EntityDeletionProgress[] = [];
            startDeletion(results);

            vi.advanceTimersByTime(DELETION_STATUS_POLL_INTERVAL_MS);
            httpMock.expectOne('api/exercise/exercises/7/deletion-progress').flush(null, { status: 404, statusText: 'Not Found' });

            expect(results).toHaveLength(1);
            expect(results[0].status).toBe(EntityDeletionStatus.COMPLETED);
        });

        it('stops polling once the deletion ended', () => {
            startDeletion([]);
            topic$.next(progress(EntityDeletionStatus.COMPLETED));

            vi.advanceTimersByTime(3 * DELETION_STATUS_POLL_INTERVAL_MS);

            httpMock.expectNone('api/exercise/exercises/7/deletion-progress');
        });

        it('keeps polling after a failed status read', () => {
            startDeletion([]);

            vi.advanceTimersByTime(DELETION_STATUS_POLL_INTERVAL_MS);
            httpMock.expectOne('api/exercise/exercises/7/deletion-progress').flush(null, { status: 500, statusText: 'Server Error' });
            vi.advanceTimersByTime(DELETION_STATUS_POLL_INTERVAL_MS);

            httpMock.expectOne('api/exercise/exercises/7/deletion-progress').flush(null, { status: 204, statusText: 'No Content' });
            expect(alertService.error).not.toHaveBeenCalled();
        });

        it('reads the status again when the websocket reconnects', () => {
            const results: EntityDeletionProgress[] = [];
            startDeletion(results);

            connection$.next(new ConnectionState(false, true));
            httpMock.expectNone('api/exercise/exercises/7/deletion-progress');
            connection$.next(new ConnectionState(true, true));

            httpMock.expectOne('api/exercise/exercises/7/deletion-progress').flush(progress(EntityDeletionStatus.COMPLETED));
            expect(results).toHaveLength(1);
        });

        it('keeps following a running deletion however long ago it reported, until the server says it ended', () => {
            const results: EntityDeletionProgress[] = [];
            startDeletion(results);

            vi.advanceTimersByTime(DELETION_STATUS_POLL_INTERVAL_MS);
            httpMock
                .expectOne('api/exercise/exercises/7/deletion-progress')
                .flush(progress(EntityDeletionStatus.IN_PROGRESS, { updatedAt: new Date(Date.now() - 60 * 60 * 1000).toISOString() as any }));
            expect(results).toHaveLength(0);

            vi.advanceTimersByTime(DELETION_STATUS_POLL_INTERVAL_MS);
            httpMock.expectOne('api/exercise/exercises/7/deletion-progress').flush(progress(EntityDeletionStatus.FAILED, { errorMessage: 'interrupted' }));
            expect(results).toHaveLength(1);
            expect(results[0].status).toBe(EntityDeletionStatus.FAILED);
        });

        it.each([403, 404])('treats a %i of the polled status of an exam as completed, because the exam is gone', (status) => {
            const results: EntityDeletionProgress[] = [];
            service.delete(examTarget, http.delete('api/exam/courses/1/exams/3')).subscribe((value) => results.push(value));
            httpMock.expectOne('api/exam/courses/1/exams/3').flush(null, { status: 202, statusText: 'Accepted' });
            httpMock
                .expectOne('api/exam/courses/1/exams/3/deletion-progress')
                .flush(progress(EntityDeletionStatus.IN_PROGRESS, { entityType: EntityDeletionType.EXAM, entityId: 3 }));

            vi.advanceTimersByTime(DELETION_STATUS_POLL_INTERVAL_MS);
            httpMock.expectOne('api/exam/courses/1/exams/3/deletion-progress').flush(null, { status, statusText: 'Gone' });

            expect(results).toHaveLength(1);
            expect(results[0].status).toBe(EntityDeletionStatus.COMPLETED);
            expect(alertService.error).not.toHaveBeenCalled();
        });

        it('treats a 403 of the status endpoint as completed for an exercise, whose permission check needs the exercise that is gone', () => {
            const results: EntityDeletionProgress[] = [];
            service.delete(exerciseTarget, http.delete('api/text/text-exercises/7')).subscribe((value) => results.push(value));
            httpMock.expectOne('api/text/text-exercises/7').flush(null, { status: 202, statusText: 'Accepted' });

            httpMock.expectOne('api/exercise/exercises/7/deletion-progress').flush(null, { status: 403, statusText: 'Forbidden' });

            expect(results).toHaveLength(1);
            expect(results[0].status).toBe(EntityDeletionStatus.COMPLETED);
            expect(topic$.observed).toBe(false);
        });
    });

    describe('exercise group whose exam status was taken over by another operation', () => {
        const startGroupDeletion = (results: EntityDeletionProgress[]) => {
            service.delete(groupTarget, http.delete('api/exam/courses/1/exams/3/exercise-groups/9')).subscribe((value) => results.push(value));
            httpMock.expectOne('api/exam/courses/1/exams/3/exercise-groups/9').flush(null, { status: 202, statusText: 'Accepted' });
            httpMock.expectOne('api/exam/courses/1/exams/3/deletion-progress').flush(null, { status: 204, statusText: 'No Content' });
        };

        it.each([EntityDeletionStatus.IN_PROGRESS, EntityDeletionStatus.COMPLETED, EntityDeletionStatus.FAILED])(
            'ends the run without polling forever when the exam status describes another group (%s)',
            (otherStatus) => {
                const results: EntityDeletionProgress[] = [];
                startGroupDeletion(results);
                topic$.next(progress(EntityDeletionStatus.IN_PROGRESS, { entityType: EntityDeletionType.EXERCISE_GROUP, entityId: 9 }));

                vi.advanceTimersByTime(DELETION_STATUS_POLL_INTERVAL_MS);
                httpMock.expectOne('api/exam/courses/1/exams/3/deletion-progress').flush(progress(otherStatus, { entityType: EntityDeletionType.EXERCISE_GROUP, entityId: 10 }));

                // The outcome is not known any more: the caller is told to refresh, and no success alert claims something that is unverified.
                expect(results).toHaveLength(1);
                expect(results[0].status).toBe(EntityDeletionStatus.COMPLETED);
                expect(alertService.success).not.toHaveBeenCalled();
                expect(alertService.error).not.toHaveBeenCalled();
                expect(dialogClose).toHaveBeenCalledOnce();
                expect(topic$.observed).toBe(false);
                vi.advanceTimersByTime(3 * DELETION_STATUS_POLL_INTERVAL_MS);
                httpMock.expectNone('api/exam/courses/1/exams/3/deletion-progress');
            },
        );

        it('ends the run when the exam status describes the exam itself', () => {
            const results: EntityDeletionProgress[] = [];
            startGroupDeletion(results);

            vi.advanceTimersByTime(DELETION_STATUS_POLL_INTERVAL_MS);
            httpMock
                .expectOne('api/exam/courses/1/exams/3/deletion-progress')
                .flush(progress(EntityDeletionStatus.IN_PROGRESS, { entityType: EntityDeletionType.EXAM, entityId: 3 }));

            expect(results).toHaveLength(1);
        });

        it('still follows its own status while it is the one the exam reports', () => {
            const results: EntityDeletionProgress[] = [];
            startGroupDeletion(results);

            vi.advanceTimersByTime(DELETION_STATUS_POLL_INTERVAL_MS);
            httpMock
                .expectOne('api/exam/courses/1/exams/3/deletion-progress')
                .flush(progress(EntityDeletionStatus.IN_PROGRESS, { entityType: EntityDeletionType.EXERCISE_GROUP, entityId: 9 }));

            expect(results).toHaveLength(0);
            expect(topic$.observed).toBe(true);
        });
    });

    describe('continue in background', () => {
        const startWithDialog = (results: EntityDeletionProgress[]) => {
            service.delete(exerciseTarget, http.delete('api/text/text-exercises/7')).subscribe((value) => results.push(value));
            httpMock.expectOne('api/text/text-exercises/7').flush(null, { status: 202, statusText: 'Accepted' });
            httpMock.expectOne('api/exercise/exercises/7/deletion-progress').flush(null, { status: 204, statusText: 'No Content' });
            return dialogOpen.mock.calls[0][1].data;
        };

        it('keeps following the deletion and reports its completion', () => {
            const results: EntityDeletionProgress[] = [];
            startWithDialog(results).continueInBackground();

            topic$.next(progress(EntityDeletionStatus.COMPLETED));

            expect(results).toHaveLength(1);
            expect(alertService.success).toHaveBeenCalledWith('artemisApp.textExercise.deleted', { param: 'Sorting' });
            expect(alertService.error).not.toHaveBeenCalled();
        });

        it('raises an error alert when the deletion fails after the dialog was closed', () => {
            const results: EntityDeletionProgress[] = [];
            startWithDialog(results).continueInBackground();

            topic$.next(progress(EntityDeletionStatus.FAILED, { errorMessage: 'boom' }));

            expect(results).toHaveLength(1);
            expect(alertService.error).toHaveBeenCalledWith('artemisApp.deletionProgress.failedInBackground', { title: 'Sorting', error: 'boom' });
        });

        it('leaves the failure to the dialog while the dialog is still open', () => {
            startWithDialog([]);

            topic$.next(progress(EntityDeletionStatus.FAILED, { errorMessage: 'boom' }));

            expect(alertService.error).not.toHaveBeenCalled();
        });
    });

    describe('resume', () => {
        it('shows the dialog of a deletion that is still running and reports how it ends', () => {
            const results: EntityDeletionProgress[] = [];
            service.resume(examTarget, destroyRef).subscribe((value) => results.push(value));

            httpMock
                .expectOne('api/exam/courses/1/exams/3/deletion-progress')
                .flush(progress(EntityDeletionStatus.IN_PROGRESS, { entityType: EntityDeletionType.EXAM, entityId: 3, currentStep: 'exercises' }));

            expect(dialogOpen).toHaveBeenCalledOnce();
            expect(dialogOpen.mock.calls[0][1].data.progress()?.currentStep).toBe('exercises');
            topic$.next(progress(EntityDeletionStatus.COMPLETED, { entityType: EntityDeletionType.EXAM, entityId: 3 }));
            expect(results).toHaveLength(1);
            expect(alertService.success).toHaveBeenCalledWith('artemisApp.exam.deleted', { param: 'Final' });
        });

        it('does not open a dialog on the exam page for the deletion of one of its exercise groups', () => {
            let completed = false;
            const results: EntityDeletionProgress[] = [];
            service.resume(examTarget, destroyRef).subscribe({ next: (value) => results.push(value), complete: () => (completed = true) });

            httpMock
                .expectOne('api/exam/courses/1/exams/3/deletion-progress')
                .flush(progress(EntityDeletionStatus.IN_PROGRESS, { entityType: EntityDeletionType.EXERCISE_GROUP, entityId: 9 }));

            expect(dialogOpen).not.toHaveBeenCalled();
            expect(alertService.info).toHaveBeenCalledWith('artemisApp.deletionProgress.otherDeletionRunning');
            expect(completed).toBe(true);
            expect(results).toHaveLength(0);
            expect(topic$.observed).toBe(false);
            vi.advanceTimersByTime(3 * DELETION_STATUS_POLL_INTERVAL_MS);
            httpMock.expectNone('api/exam/courses/1/exams/3/deletion-progress');
        });

        it('does not open a dialog for another exercise group of the exam than the one that is asked about', () => {
            service.resume(groupTarget, destroyRef).subscribe();

            httpMock
                .expectOne('api/exam/courses/1/exams/3/deletion-progress')
                .flush(progress(EntityDeletionStatus.IN_PROGRESS, { entityType: EntityDeletionType.EXERCISE_GROUP, entityId: 10 }));

            expect(dialogOpen).not.toHaveBeenCalled();
        });

        it('trusts the status of the server and shows a running deletion however long ago it reported', () => {
            service.resume(examTarget, destroyRef).subscribe();

            httpMock.expectOne('api/exam/courses/1/exams/3/deletion-progress').flush(
                progress(EntityDeletionStatus.IN_PROGRESS, {
                    entityType: EntityDeletionType.EXAM,
                    entityId: 3,
                    updatedAt: new Date(Date.now() - 60 * 60 * 1000).toISOString() as any,
                }),
            );

            expect(dialogOpen).toHaveBeenCalledOnce();
        });

        it('cancels the status request and opens no dialog when the caller is destroyed first', () => {
            let completed = false;
            service.resume(examTarget, destroyRef).subscribe({ complete: () => (completed = true) });
            const request = httpMock.expectOne('api/exam/courses/1/exams/3/deletion-progress');

            destroyCallbacks.forEach((callback) => callback());

            expect(request.cancelled).toBe(true);
            expect(dialogOpen).not.toHaveBeenCalled();
            expect(websocketSubscribe).not.toHaveBeenCalled();
            expect(completed).toBe(true);
        });

        it('reuses the run that already follows the entity instead of starting a second one', () => {
            const first: EntityDeletionProgress[] = [];
            const second: EntityDeletionProgress[] = [];
            service.delete(exerciseTarget, http.delete('api/text/text-exercises/7')).subscribe((value) => first.push(value));
            httpMock.expectOne('api/text/text-exercises/7').flush(null, { status: 202, statusText: 'Accepted' });
            httpMock.expectOne('api/exercise/exercises/7/deletion-progress').flush(null, { status: 204, statusText: 'No Content' });
            dialogOpen.mock.calls[0][1].data.continueInBackground();

            service.resume(exerciseTarget, destroyRef).subscribe((value) => second.push(value));

            // no status request and no second websocket subscription: the existing run is reattached and shows its dialog again
            httpMock.expectNone('api/exercise/exercises/7/deletion-progress');
            expect(websocketSubscribe).toHaveBeenCalledOnce();
            expect(dialogOpen).toHaveBeenCalledTimes(2);

            vi.advanceTimersByTime(DELETION_STATUS_POLL_INTERVAL_MS);
            httpMock.expectOne('api/exercise/exercises/7/deletion-progress').flush(progress(EntityDeletionStatus.COMPLETED, { weightedProgressPercent: 100 }));

            expect(first).toHaveLength(1);
            expect(second).toHaveLength(1);
            expect(alertService.success).toHaveBeenCalledOnce();
        });

        it('does not open a second dialog while the dialog of the run is still shown', () => {
            service.delete(exerciseTarget, http.delete('api/text/text-exercises/7')).subscribe();
            httpMock.expectOne('api/text/text-exercises/7').flush(null, { status: 202, statusText: 'Accepted' });
            httpMock.expectOne('api/exercise/exercises/7/deletion-progress').flush(null, { status: 204, statusText: 'No Content' });

            service.resume(exerciseTarget, destroyRef).subscribe();

            expect(dialogOpen).toHaveBeenCalledOnce();
        });

        it('shows a failure in the reopened dialog instead of an alert', () => {
            service.delete(exerciseTarget, http.delete('api/text/text-exercises/7')).subscribe();
            httpMock.expectOne('api/text/text-exercises/7').flush(null, { status: 202, statusText: 'Accepted' });
            httpMock.expectOne('api/exercise/exercises/7/deletion-progress').flush(null, { status: 204, statusText: 'No Content' });
            dialogOpen.mock.calls[0][1].data.continueInBackground();
            service.resume(exerciseTarget, destroyRef).subscribe();

            topic$.next(progress(EntityDeletionStatus.FAILED, { errorMessage: 'boom' }));

            expect(alertService.error).not.toHaveBeenCalled();
        });

        it('starts a new run once the earlier run for the entity ended', () => {
            service.delete(exerciseTarget, http.delete('api/text/text-exercises/7')).subscribe();
            httpMock.expectOne('api/text/text-exercises/7').flush(null, { status: 202, statusText: 'Accepted' });
            httpMock.expectOne('api/exercise/exercises/7/deletion-progress').flush(null, { status: 204, statusText: 'No Content' });
            topic$.next(progress(EntityDeletionStatus.FAILED, { errorMessage: 'boom' }));

            service.resume(exerciseTarget, destroyRef).subscribe();

            httpMock.expectOne('api/exercise/exercises/7/deletion-progress').flush(null, { status: 204, statusText: 'No Content' });
        });

        it('polls the status of a resumed deletion until it ended', () => {
            const results: EntityDeletionProgress[] = [];
            service.resume(examTarget, destroyRef).subscribe((value) => results.push(value));
            httpMock
                .expectOne('api/exam/courses/1/exams/3/deletion-progress')
                .flush(progress(EntityDeletionStatus.IN_PROGRESS, { entityType: EntityDeletionType.EXAM, entityId: 3 }));

            vi.advanceTimersByTime(DELETION_STATUS_POLL_INTERVAL_MS);
            httpMock.expectOne('api/exam/courses/1/exams/3/deletion-progress').flush(null, { status: 404, statusText: 'Not Found' });

            expect(results).toHaveLength(1);
            expect(results[0].status).toBe(EntityDeletionStatus.COMPLETED);
        });

        it('does nothing when no deletion is running', () => {
            let completed = false;
            const results: EntityDeletionProgress[] = [];
            service.resume(examTarget, destroyRef).subscribe({ next: (value) => results.push(value), complete: () => (completed = true) });

            httpMock.expectOne('api/exam/courses/1/exams/3/deletion-progress').flush(null, { status: 204, statusText: 'No Content' });

            expect(dialogOpen).not.toHaveBeenCalled();
            expect(results).toHaveLength(0);
            expect(completed).toBe(true);
            expect(topic$.observed).toBe(false);
        });

        it('does not show the result of a deletion that already ended earlier', () => {
            let completed = false;
            service.resume(examTarget, destroyRef).subscribe({ complete: () => (completed = true) });

            httpMock.expectOne('api/exam/courses/1/exams/3/deletion-progress').flush(progress(EntityDeletionStatus.FAILED, { entityType: EntityDeletionType.EXAM, entityId: 3 }));

            expect(dialogOpen).not.toHaveBeenCalled();
            expect(completed).toBe(true);
        });

        it('stays quiet when the status cannot be read', () => {
            let completed = false;
            service.resume(exerciseTarget, destroyRef).subscribe({ complete: () => (completed = true) });

            httpMock.expectOne('api/exercise/exercises/7/deletion-progress').flush(null, { status: 500, statusText: 'Server Error' });

            expect(dialogOpen).not.toHaveBeenCalled();
            expect(completed).toBe(true);
        });
    });
});
