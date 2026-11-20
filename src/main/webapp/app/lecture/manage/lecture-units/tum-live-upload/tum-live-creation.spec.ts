import { Location } from '@angular/common';
import { provideLocationMocks } from '@angular/common/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter, withRouterConfig } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { describe, expect, it } from 'vitest';
import { RecordingNavigation, RecordingRedirect, createOutcomeIsUnknown } from './tum-live-creation';

@Component({ template: '' })
class RecordingCreatePage {
    redirect = new RecordingRedirect();
}

@Component({ template: '' })
class RecordingEditPage {}

describe('RecordingRedirect', () => {
    const target = { courseId: 1, lectureId: 2, unitId: 3 };
    const editUrl = '/course-management/1/lectures/2/edit';

    async function sourcePage(canOpen = () => true) {
        TestBed.configureTestingModule({
            providers: [
                provideLocationMocks(),
                provideRouter(
                    [
                        {
                            path: 'new',
                            component: RecordingCreatePage,
                            canDeactivate: [(page: RecordingCreatePage, _route, _current, next) => page.redirect.allows(next.url)],
                        },
                        { path: 'course-management/:courseId/lectures/:lectureId/edit', component: RecordingEditPage, canActivate: [canOpen] },
                    ],
                    withRouterConfig({ onSameUrlNavigation: 'reload' }),
                ),
            ],
        });
        const harness = await RouterTestingHarness.create();
        return harness.navigateByUrl('/new', RecordingCreatePage);
    }

    it('allows only the navigation it started and puts the file in memory rather than in the history entry', async () => {
        const source = await sourcePage();
        const router = TestBed.inject(Router);
        const file = new File(['mp4'], 'recording.mp4', { type: 'video/mp4' });
        const saved = { lectureDetailsSavedAt: '2026-11-20T00:00:00Z' };

        // The same URL is insufficient to bypass the guard.
        await expect(router.navigateByUrl(editUrl, { info: new RecordingNavigation(target, file) })).resolves.toBe(false);
        await expect(source.redirect.open(target, false, file, undefined, saved)).resolves.toBe(true);

        expect(router.url).toBe(editUrl);
        expect(TestBed.inject(Location).getState()).toEqual(expect.objectContaining(saved));
        expect(TestBed.inject(Location).getState()).not.toHaveProperty('file');
        const accepted = router.lastSuccessfulNavigation()?.extras.info as RecordingNavigation;
        expect(accepted.takeFile()).toBe(file);
        expect(accepted.takeFile()).toBeUndefined();
    });

    it('keeps only the acknowledged IDs in the source history when navigation fails', async () => {
        let allow = false;
        const source = await sourcePage(() => allow);
        const location = TestBed.inject(Location);
        const file = new File(['mp4'], 'recording.mp4', { type: 'video/mp4' });
        source.redirect.checkpoint(target);

        await expect(source.redirect.open(target, false, file)).resolves.toBe(false);
        expect(source.redirect.allows(editUrl)).toBe(false);
        expect(location.getState()).toMatchObject({ tumLiveRecordingCheckpoint: target });
        expect(source.redirect.recover(1, 2)).toEqual(target);
        expect(source.redirect.recover(1, 20)).toBeUndefined();
        expect(source.redirect.recover(10)).toBeUndefined();

        // Reloading can re-open the confirmed unit, even though the original file is unavailable.
        const reloaded = TestBed.runInInjectionContext(() => new RecordingRedirect());
        source.redirect = reloaded;
        allow = true;
        await expect(reloaded.open(reloaded.recover(1, 2)!, false)).resolves.toBe(true);
        const accepted = TestBed.inject(Router).lastSuccessfulNavigation()?.extras.info as RecordingNavigation;
        expect(accepted.takeFile()).toBeUndefined();
    });

    it('restores only the pending save for the matching scope and clears it after acknowledgment', async () => {
        const source = await sourcePage();
        const location = TestBed.inject(Location);
        location.replaceState('/new', '', { preserved: true, tumLiveRecordingSavePending: { courseId: 1, lectureId: 2 } });
        expect(source.redirect.recoverPendingSave(1, 2)).toEqual({ courseId: 1, lectureId: 2 });

        source.redirect.checkpointPendingSave({ courseId: 1 });
        expect(source.redirect.recoverPendingSave(1)).toEqual({ courseId: 1, lectureId: undefined });
        expect(source.redirect.recoverPendingSave(1, 2)).toBeUndefined();
        expect(source.redirect.recoverPendingSave(5)).toBeUndefined();

        source.redirect.checkpointPendingSave({ courseId: 1, lectureId: 2 });
        source.redirect.checkpoint(target);
        expect(source.redirect.recoverPendingSave(1, 2)).toBeUndefined();
        expect(source.redirect.recover(1, 2)).toEqual(target);
        expect(location.getState()).toMatchObject({ preserved: true });
    });

    it.each([false, true])('keeps a pending save when Angular cancels navigation (already reloaded: %s)', async (reloaded) => {
        const source = await sourcePage();
        const router = TestBed.inject(Router);
        const location = TestBed.inject(Location);
        const scope = { courseId: 1, lectureId: 2 };
        if (reloaded) {
            location.replaceState('/new', '', { tumLiveRecordingSavePending: scope });
            expect(source.redirect.recoverPendingSave(1, 2)).toEqual(scope);
        } else {
            source.redirect.checkpointPendingSave(scope);
        }

        await expect(router.navigateByUrl(editUrl)).resolves.toBe(false);

        expect(location.getState()).toMatchObject({ tumLiveRecordingSavePending: scope });
    });

    it.each([false, true])('keeps the checkpoint on same-URL navigation (pending save: %s)', async (pending) => {
        const source = await sourcePage();
        const scope = { courseId: 1, lectureId: 2 };
        if (pending) {
            source.redirect.checkpointPendingSave(scope);
        } else {
            source.redirect.checkpoint(scope);
        }

        await expect(TestBed.inject(Router).navigateByUrl('/new')).resolves.toBe(true);

        const key = pending ? 'tumLiveRecordingSavePending' : 'tumLiveRecordingCheckpoint';
        expect(TestBed.inject(Location).getState()).toMatchObject({ [key]: scope });
    });

    it('keeps a pending save when navigation fails with an error', async () => {
        const source = await sourcePage();
        const scope = { courseId: 1, lectureId: 2 };
        source.redirect.checkpointPendingSave(scope);

        await expect(TestBed.inject(Router).navigateByUrl('/unmatched')).rejects.toThrow();

        expect(TestBed.inject(Location).getState()).toMatchObject({ tumLiveRecordingSavePending: scope });
    });

    it('keeps the acknowledged lecture if a leave attempt is canceled', async () => {
        const source = await sourcePage();
        const scope = { courseId: 1, lectureId: 2 };
        TestBed.inject(Location).replaceState('/new', '', { tumLiveRecordingCheckpoint: scope });
        expect(source.redirect.recover(1)).toEqual(scope);

        await expect(TestBed.inject(Router).navigateByUrl(editUrl)).resolves.toBe(false);

        expect(TestBed.inject(Location).getState()).toMatchObject({ tumLiveRecordingCheckpoint: scope });
    });
});

describe('createOutcomeIsUnknown', () => {
    it.each([0, 408, 502, 503, 504])('requires a status check when a create returns %s', (status) => {
        expect(createOutcomeIsUnknown(new HttpErrorResponse({ status }))).toBe(true);
    });

    it.each([401, 403, 404, 413, 415])('treats %s as a rejected request', (status) => {
        expect(createOutcomeIsUnknown(new HttpErrorResponse({ status }))).toBe(false);
    });

    it('allows retrying a known validation error but requires a check for other 400 responses', () => {
        expect(createOutcomeIsUnknown(new HttpErrorResponse({ status: 400, error: { message: 'error.invalidPdf' } }))).toBe(false);
        expect(createOutcomeIsUnknown(new HttpErrorResponse({ status: 400, error: { fieldErrors: [{ field: 'name' }] } }))).toBe(false);
        expect(createOutcomeIsUnknown(new HttpErrorResponse({ status: 400, error: { detail: 'An unclassified error' } }))).toBe(true);
        expect(createOutcomeIsUnknown(new Error('Missing unit ID'))).toBe(true);
    });
});
