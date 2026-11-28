import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { Subject, of, throwError } from 'rxjs';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { TumLiveFilePickerComponent } from './tum-live-file-picker.component';
import { TumLiveUploadComponent } from 'app/lecture/manage/lecture-units/tum-live-upload/tum-live-upload.component';
import { TumLiveTransferError, TumLiveTransferEvent, TumLiveUploadService, TumLiveUploadState } from 'app/lecture/manage/lecture-units/services/tum-live-upload.service';

describe('TumLiveUploadComponent', () => {
    let fixture: ComponentFixture<TumLiveUploadComponent>;
    let component: TumLiveUploadComponent;
    let service: TumLiveUploadService;
    let state: Subject<TumLiveUploadState>;

    const render = (initial: TumLiveUploadState) => {
        state = new Subject<TumLiveUploadState>();
        vi.spyOn(service, 'getState').mockReturnValue(state);
        fixture.componentRef.setInput('lectureUnitId', 7);
        fixture.detectChanges();
        state.next(initial);
        fixture.detectChanges();
    };
    const query = (testId: string): HTMLElement | null => fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);
    const chooseFile = () => component.file.set(new File(['video'], 'lecture.mp4', { type: 'video/mp4' }));

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TumLiveUploadComponent],
            providers: [MockProvider(TumLiveUploadService)],
        })
            .overrideComponent(TumLiveUploadComponent, {
                remove: { imports: [TranslateDirective, ArtemisTranslatePipe, TumLiveFilePickerComponent] },
                add: { imports: [MockDirective(TranslateDirective), MockPipe(ArtemisTranslatePipe, (value) => value ?? ''), MockComponent(TumLiveFilePickerComponent)] },
            })
            .compileComponents();
        service = TestBed.inject(TumLiveUploadService);
        fixture = TestBed.createComponent(TumLiveUploadComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => vi.useRealTimers());

    it('shows nothing for a course that is not connected', () => {
        render({ connected: false });

        expect(query('tum-live-upload')).toBeNull();
    });

    it('uploads the chosen file and then shows the conversion', () => {
        render({ connected: true });
        const transfer = new Subject<TumLiveTransferEvent>();
        vi.spyOn(service, 'start').mockReturnValue(of({ ref: 'reference-a-0000', uploadUrl: '/upload-a' }));
        const transferSpy = vi.spyOn(service, 'transfer').mockReturnValue(transfer);
        chooseFile();
        fixture.detectChanges();

        query('tum-live-upload-start')!.click();
        transfer.next({ type: 'progress', percent: 40 });
        fixture.detectChanges();

        expect(transferSpy).toHaveBeenCalledWith('/upload-a', expect.any(File));
        expect(component.transferProgress()).toBe(40);
        expect(query('tum-live-upload')?.textContent).toContain('40%');
        expect(query('tum-live-upload')?.querySelector('[role="progressbar"]')?.getAttribute('aria-valuenow')).toBe('40');
        expect(query('tum-live-upload-cancel')).not.toBeNull();

        transfer.next({ type: 'done' });
        transfer.complete();
        state.next({ connected: true, pendingRef: 'reference-a-0000', converting: true, progress: 10 });
        fixture.detectChanges();

        expect(component.transferProgress()).toBeUndefined();
        expect(query('tum-live-upload-discard')).not.toBeNull();
    });

    it('shows an accepted upload when the editor is reopened before conversion progress is available', () => {
        const pendingRef = 'reference-a-0000';
        render({ connected: true, pendingRef, transferAccepted: true });

        expect(query('tum-live-upload')?.textContent).toContain('artemisApp.tumLiveUpload.uploaded');
        expect(query('tum-live-upload')?.querySelector('[role="progressbar"]')).toBeNull();

        state.next({ connected: true, pendingRef, transferAccepted: true, converting: true, progress: 42 });
        fixture.detectChanges();

        expect(query('tum-live-upload')?.textContent).toContain('artemisApp.tumLiveUpload.processing');
        expect(query('tum-live-upload')?.querySelector('[role="progressbar"]')?.getAttribute('aria-valuenow')).toBe('42');
    });

    it('takes the initial file and starts once before the first status response', () => {
        const states = new Subject<TumLiveUploadState>();
        const transfer = new Subject<TumLiveTransferEvent>();
        vi.spyOn(service, 'getState').mockReturnValue(states);
        const start = vi.spyOn(service, 'start').mockReturnValue(of({ ref: 'reference-a-0000', uploadUrl: '/transfer' }));
        const send = vi.spyOn(service, 'transfer').mockReturnValue(transfer);
        const file = new File(['video'], 'lecture.mp4', { type: 'video/mp4' });
        fixture.componentRef.setInput('lectureUnitId', 7);
        fixture.componentRef.setInput('initialFile', file);
        fixture.detectChanges();
        states.next({ connected: true });
        fixture.detectChanges();
        transfer.next({ type: 'progress', percent: 100 });

        expect(start).toHaveBeenCalledExactlyOnceWith(7);
        expect(send).toHaveBeenCalledExactlyOnceWith('/transfer', file);
        expect(component.transferring()).toBe(true);

        transfer.complete();
        expect(component.transferring()).toBe(false);
        expect(component.pendingRef()).toBe('reference-a-0000');
    });

    it('checks the exact reference after losing the transfer response and leaves it intact on destroy', () => {
        const ref = 'reference-a-0000';
        render({ connected: true });
        vi.spyOn(service, 'start').mockReturnValue(of({ ref, uploadUrl: '/transfer' }));
        vi.spyOn(service, 'transfer').mockReturnValue(throwError(() => new TumLiveTransferError('network', 0)));
        const discard = vi.spyOn(service, 'discard');
        chooseFile();

        component.upload();
        state.next({ connected: true, pendingRef: ref });
        expect(component.uncertainRef()).toBe(ref);
        expect(component.transferring()).toBe(false);
        fixture.destroy();
        expect(discard).not.toHaveBeenCalled();
    });

    it.each([{ transferAccepted: true }, { converting: true }])('recovers a lost transfer response from server state %j', (confirmation) => {
        const ref = 'reference-a-0000';
        render({ connected: true });
        vi.spyOn(service, 'start').mockReturnValue(of({ ref, uploadUrl: '/transfer' }));
        vi.spyOn(service, 'transfer').mockReturnValue(throwError(() => new TumLiveTransferError('HTTP 504', 504)));
        const discard = vi.spyOn(service, 'discard');
        chooseFile();

        component.upload();
        state.next({ connected: true, pendingRef: ref, ...confirmation });
        expect(component.uncertainRef()).toBeUndefined();
        expect(component.file()).toBeUndefined();
        expect(component.pendingRef()).toBe(ref);
        expect(discard).not.toHaveBeenCalled();
    });

    it.each([413, 500])('reads the server state after HTTP %i without discarding the upload', (status) => {
        const ref = 'reference-a-0000';
        render({ connected: true });
        vi.spyOn(service, 'start').mockReturnValue(of({ ref, uploadUrl: '/upload-a' }));
        vi.spyOn(service, 'transfer').mockReturnValue(throwError(() => new TumLiveTransferError(`HTTP ${status}`, status)));
        const discardSpy = vi.spyOn(service, 'discard');
        chooseFile();

        component.upload();
        const currentState = status === 413 ? { connected: true, pendingRef: ref } : { connected: true };
        state.next(currentState);
        fixture.detectChanges();

        expect(service.getState).toHaveBeenCalledTimes(2);
        expect(discardSpy).not.toHaveBeenCalled();
        expect(component.transferError()?.status).toBe(status);
        expect(component.file()?.name).toBe('lecture.mp4');
        if (status === 413) {
            // A proxy can reject the request before it reaches Artemis. Its reservation can still be discarded explicitly.
            expect(query('tum-live-upload-discard')).not.toBeNull();
        } else {
            expect(query('tum-live-upload-start')).not.toBeNull();
        }
    });

    it('keeps a pending upload when another request is already transferring it', () => {
        render({ connected: true });
        vi.spyOn(service, 'start').mockReturnValue(of({ ref: 'reference-a-0000', uploadUrl: '/transfer' }));
        vi.spyOn(service, 'transfer').mockReturnValue(throwError(() => new TumLiveTransferError('HTTP 409', 409, 'error.tumLiveUploadInProgress')));
        const discardSpy = vi.spyOn(service, 'discard');

        chooseFile();
        component.upload();

        expect(discardSpy).not.toHaveBeenCalled();
        expect(component.transferProgress()).toBeUndefined();
        expect(component.transferError()?.translationKey).toBe('error.tumLiveUploadInProgress');
        state.next({ connected: true, pendingRef: 'reference-a-0000' });
        expect(component.transferError()?.status).toBe(409);

        state.next({ connected: true, lastError: 'The video transfer to TUM.Live failed.' });
        expect(component.transferError()).toBeUndefined();
        expect(discardSpy).not.toHaveBeenCalled();
    });

    it('offers to discard an unconfirmed pending upload without claiming that it is converting', () => {
        render({ connected: true });
        vi.spyOn(service, 'start').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 0 })));
        const discard = vi.spyOn(service, 'discard').mockReturnValue(of(undefined));
        const transfer = vi.spyOn(service, 'transfer');
        const status = fixture.nativeElement.querySelector('[role=status]');
        chooseFile();

        component.upload();
        state.next({ connected: true, pendingRef: 'unconfirmed-reference', converting: false });
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('[role=status]')).toBe(status);
        expect(status.textContent).toContain('artemisApp.tumLiveUpload.pending');
        expect(query('tum-live-upload-discard')).not.toBeNull();
        expect(transfer).not.toHaveBeenCalled();
        expect(discard).not.toHaveBeenCalled();
    });

    it('shows a competing reservation after a rejected start and leaves it untouched when the page closes', () => {
        render({ connected: true });
        vi.spyOn(service, 'start').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 409, error: { message: 'error.tumLiveUploadInProgress' } })));
        const discard = vi.spyOn(service, 'discard');
        const transfer = vi.spyOn(service, 'transfer');
        chooseFile();

        component.upload();
        state.next({ connected: true, pendingRef: 'another-editors-reference' });
        expect(component.transferError()?.translationKey).toBe('error.tumLiveUploadInProgress');
        expect(component.pendingRef()).toBe('another-editors-reference');

        fixture.destroy();
        expect(transfer).not.toHaveBeenCalled();
        expect(discard).not.toHaveBeenCalled();
    });

    it('leaves the reason of a failed start to the error alert', () => {
        render({ connected: true });
        vi.spyOn(service, 'start').mockReturnValue(throwError(() => new Error('409')));
        const transferSpy = vi.spyOn(service, 'transfer');
        chooseFile();

        component.upload();
        fixture.detectChanges();

        expect(transferSpy).not.toHaveBeenCalled();
        expect(component.transferProgress()).toBeUndefined();
        expect(component.transferError()).toBeUndefined();
    });

    it('reports when the converted video was attached', () => {
        render({ connected: true, pendingRef: 'reference-a-0000', converting: true, progress: 50 });
        const attached = vi.fn();
        component.attached.subscribe(attached);

        state.next({ connected: true, attachedRef: 'reference-a-0000' });
        fixture.detectChanges();

        expect(attached).toHaveBeenCalledOnce();
        expect(component.attachedNow()).toBe(true);
    });

    it('does not report a dropped or discarded replacement as attached', () => {
        render({ connected: true, pendingRef: 'reference-b-0000', attachedRef: 'reference-a-0000' });
        const attached = vi.fn();
        component.attached.subscribe(attached);

        state.next({ connected: true, attachedRef: 'reference-a-0000' });
        fixture.detectChanges();

        expect(attached).not.toHaveBeenCalled();
        expect(component.attachedNow()).toBe(false);
    });

    it('cancels a running transfer and discards its upload', () => {
        render({ connected: true });
        const transfer = new Subject<TumLiveTransferEvent>();
        vi.spyOn(service, 'start').mockReturnValue(of({ ref: 'reference-a-0000', uploadUrl: '/upload-a' }));
        vi.spyOn(service, 'transfer').mockReturnValue(transfer);
        const discardSpy = vi.spyOn(service, 'discard').mockReturnValue(of(undefined));
        chooseFile();
        component.upload();

        component.cancel();

        expect(transfer.observed).toBe(false);
        expect(discardSpy).toHaveBeenCalledWith(7, 'reference-a-0000');
    });

    it('cancels before the server named the upload, and discards exactly that upload once named', () => {
        render({ connected: true });
        const start = new Subject<{ ref: string; uploadUrl: string }>();
        vi.spyOn(service, 'start').mockReturnValue(start);
        const transferSpy = vi.spyOn(service, 'transfer');
        const discardSpy = vi.spyOn(service, 'discard').mockReturnValue(of(undefined));
        chooseFile();
        component.upload();

        component.cancel();
        expect(discardSpy).not.toHaveBeenCalled();
        start.next({ ref: 'reference-a-0000', uploadUrl: '/upload-a' });

        expect(transferSpy).not.toHaveBeenCalled();
        expect(discardSpy).toHaveBeenCalledWith(7, 'reference-a-0000');
    });

    it('does not discard another editor’s reservation after cancelling a conflicting start', () => {
        render({ connected: true });
        const start = new Subject<{ ref: string; uploadUrl: string }>();
        vi.spyOn(service, 'start').mockReturnValue(start);
        const transferSpy = vi.spyOn(service, 'transfer');
        const discardSpy = vi.spyOn(service, 'discard');
        chooseFile();
        component.upload();
        component.cancel();
        start.error(new HttpErrorResponse({ status: 409 }));

        expect(component.busy()).toBe(false);
        expect(transferSpy).not.toHaveBeenCalled();
        expect(discardSpy).not.toHaveBeenCalled();
    });

    it('starts no new upload until a cancelled start and its discard are answered', () => {
        render({ connected: true });
        const startA = new Subject<{ ref: string; uploadUrl: string }>();
        const startB = new Subject<{ ref: string; uploadUrl: string }>();
        const startSpy = vi.spyOn(service, 'start').mockReturnValueOnce(startA).mockReturnValueOnce(startB);
        const discard = new Subject<void>();
        const discardSpy = vi.spyOn(service, 'discard').mockReturnValue(discard);
        const transferSpy = vi.spyOn(service, 'transfer').mockReturnValue(new Subject<TumLiveTransferEvent>());
        chooseFile();
        component.upload();
        component.cancel();

        component.upload();
        expect(startSpy).toHaveBeenCalledOnce();
        startA.next({ ref: 'reference-a-0000', uploadUrl: 'https://tum.live/a' });
        expect(discardSpy).toHaveBeenCalledWith(7, 'reference-a-0000');
        component.upload();
        expect(startSpy).toHaveBeenCalledOnce();

        discard.complete();
        component.upload();
        startB.next({ ref: 'reference-b-0000', uploadUrl: '/upload-b' });

        expect(startSpy).toHaveBeenCalledTimes(2);
        expect(transferSpy).toHaveBeenCalledWith('/upload-b', expect.any(File));
        expect(discardSpy).toHaveBeenCalledOnce();
    });

    it('discards an upload for the unit it was started for, also after being given another unit', () => {
        render({ connected: true });
        const start = new Subject<{ ref: string; uploadUrl: string }>();
        vi.spyOn(service, 'start').mockReturnValue(start);
        const discardSpy = vi.spyOn(service, 'discard').mockReturnValue(of(undefined));
        chooseFile();
        component.upload();
        component.cancel();

        fixture.componentRef.setInput('lectureUnitId', 8);
        fixture.detectChanges();
        start.next({ ref: 'reference-a-0000', uploadUrl: 'https://tum.live/a' });

        expect(service.start).toHaveBeenCalledWith(7);
        expect(discardSpy).toHaveBeenCalledWith(7, 'reference-a-0000');
    });

    it('recognises the attachment of its upload even without seeing it pending', () => {
        render({ connected: true });
        vi.spyOn(service, 'start').mockReturnValue(of({ ref: 'reference-a-0000', uploadUrl: '/upload-a' }));
        vi.spyOn(service, 'transfer').mockReturnValue(of({ type: 'done' } as TumLiveTransferEvent));
        const attached = vi.fn();
        component.attached.subscribe(attached);
        chooseFile();

        component.upload();
        state.next({ connected: true, attachedRef: 'reference-a-0000' });

        expect(attached).toHaveBeenCalledOnce();
    });

    it('keeps polling after a failed request', () => {
        vi.useFakeTimers();
        render({ connected: true, pendingRef: 'reference-a-0000', converting: true, progress: 5 });
        vi.spyOn(service, 'getState')
            .mockReturnValueOnce(throwError(() => new Error('offline')))
            .mockReturnValue(of({ connected: true, attachedRef: 'reference-a-0000' }));
        const attached = vi.fn();
        component.attached.subscribe(attached);

        vi.advanceTimersByTime(30_000);

        expect(attached).toHaveBeenCalledOnce();
    });

    it('ignores a stale poll when a transfer conflict triggers a fresh read', () => {
        vi.useFakeTimers();
        const pending = { connected: true, pendingRef: 'reference-a-0000' };
        render({ connected: true });
        const transfer = new Subject<TumLiveTransferEvent>();
        vi.spyOn(service, 'start').mockReturnValue(of({ ref: pending.pendingRef, uploadUrl: '/transfer' }));
        vi.spyOn(service, 'transfer').mockReturnValue(transfer);
        chooseFile();
        component.upload();
        state.next(pending);

        const stalePoll = new Subject<TumLiveUploadState>();
        vi.spyOn(service, 'getState').mockReturnValueOnce(stalePoll).mockReturnValue(of(pending));
        vi.advanceTimersByTime(15_000);
        expect(stalePoll.observed).toBe(true);

        transfer.error(new TumLiveTransferError('HTTP 409', 409, 'error.tumLiveUploadInProgress'));
        stalePoll.next({ connected: true });
        expect(stalePoll.observed).toBe(false);
        expect(component.state()).toEqual(pending);
        expect(component.transferError()?.status).toBe(409);

        vi.spyOn(service, 'getState').mockReturnValue(of({ connected: true }));
        vi.advanceTimersByTime(15_000);
        expect(component.state()?.pendingRef).toBeUndefined();
        expect(component.transferError()).toBeUndefined();
    });

    it('reads the state again after reading it failed, and then shows the section', () => {
        vi.useFakeTimers();
        vi.spyOn(service, 'getState')
            .mockReturnValueOnce(throwError(() => new Error('offline')))
            .mockReturnValue(of({ connected: true }));
        fixture.componentRef.setInput('lectureUnitId', 7);
        fixture.detectChanges();
        expect(query('tum-live-upload')).toBeNull();

        vi.advanceTimersByTime(15_000);
        fixture.detectChanges();

        expect(service.getState).toHaveBeenCalledTimes(2);
        expect(query('tum-live-upload')).not.toBeNull();
    });

    it('reports a sent file, and gives up a transfer that leaving the page stops', () => {
        render({ connected: true });
        vi.spyOn(service, 'start').mockReturnValue(of({ ref: 'reference-a-0000', uploadUrl: '/upload-a' }));
        const transfer = new Subject<TumLiveTransferEvent>();
        vi.spyOn(service, 'transfer').mockReturnValue(transfer);
        const discardSpy = vi.spyOn(service, 'discard').mockReturnValue(of(undefined));
        const transferred = vi.fn();
        component.transferred.subscribe(transferred);
        chooseFile();
        component.upload();
        transfer.next({ type: 'progress', percent: 40 });

        fixture.destroy();

        expect(discardSpy).toHaveBeenCalledWith(7, 'reference-a-0000');
        expect(transferred).not.toHaveBeenCalled();
    });

    it('gives up the upload a start created when the page was left before its answer', () => {
        render({ connected: true });
        const start = new Subject<{ ref: string; uploadUrl: string }>();
        vi.spyOn(service, 'start').mockReturnValue(start);
        const transferSpy = vi.spyOn(service, 'transfer');
        const discardSpy = vi.spyOn(service, 'discard').mockReturnValue(of(undefined));
        chooseFile();
        component.upload();

        fixture.destroy();
        start.next({ ref: 'reference-a-0000', uploadUrl: '/upload-a' });

        expect(discardSpy).toHaveBeenCalledExactlyOnceWith(7, 'reference-a-0000');
        expect(transferSpy).not.toHaveBeenCalled();
    });

    it('reports a file that was sent completely and keeps its upload when the page is left', () => {
        render({ connected: true });
        vi.spyOn(service, 'start').mockReturnValue(of({ ref: 'reference-a-0000', uploadUrl: '/upload-a' }));
        vi.spyOn(service, 'transfer').mockReturnValue(of({ type: 'done' } as TumLiveTransferEvent));
        const discardSpy = vi.spyOn(service, 'discard').mockReturnValue(of(undefined));
        const transferred = vi.fn();
        component.transferred.subscribe(transferred);
        chooseFile();
        component.upload();

        fixture.destroy();

        expect(transferred).toHaveBeenCalledOnce();
        expect(discardSpy).not.toHaveBeenCalled();
    });

    it('polls the conversion and discards a pending upload on request', () => {
        vi.useFakeTimers();
        render({ connected: true, pendingRef: 'reference-a-0000', converting: true, progress: 5 });
        const discardSpy = vi.spyOn(service, 'discard').mockReturnValue(of(undefined));

        vi.advanceTimersByTime(15_000);
        expect(service.getState).toHaveBeenCalledTimes(2);

        component.discardPending();
        expect(discardSpy).toHaveBeenCalledWith(7, 'reference-a-0000');
    });
});
