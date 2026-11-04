import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { Subject, of, throwError } from 'rxjs';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { TumLiveUploadComponent } from 'app/lecture/manage/lecture-units/tum-live-upload/tum-live-upload.component';
import { TumLiveTransferEvent, TumLiveUploadService, TumLiveUploadState } from 'app/lecture/manage/lecture-units/services/tum-live-upload.service';

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
    const chooseFile = () => component.onFileChosen({ target: { files: [new File(['video'], 'lecture.mp4')] } } as unknown as Event);

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TumLiveUploadComponent],
            providers: [MockProvider(TumLiveUploadService)],
        })
            .overrideComponent(TumLiveUploadComponent, {
                remove: { imports: [TranslateDirective, ArtemisTranslatePipe] },
                add: { imports: [MockDirective(TranslateDirective), MockPipe(ArtemisTranslatePipe)] },
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
        vi.spyOn(service, 'start').mockReturnValue(of({ ref: 'reference-a-0000', uploadUrl: 'https://tum.live/u' }));
        const transferSpy = vi.spyOn(service, 'transfer').mockReturnValue(transfer);
        chooseFile();
        fixture.detectChanges();

        query('tum-live-upload-start')!.click();
        transfer.next({ type: 'progress', percent: 40 });
        fixture.detectChanges();

        expect(transferSpy).toHaveBeenCalledWith('https://tum.live/u', expect.any(File));
        expect(component.transferProgress()).toBe(40);
        expect(query('tum-live-upload-cancel')).not.toBeNull();

        transfer.next({ type: 'done' });
        transfer.complete();
        state.next({ connected: true, pendingRef: 'reference-a-0000', converting: true, progress: 10 });
        fixture.detectChanges();

        expect(component.transferProgress()).toBeUndefined();
        expect(query('tum-live-upload-discard')).not.toBeNull();
    });

    it('discards the upload when its transfer fails', () => {
        render({ connected: true });
        vi.spyOn(service, 'start').mockReturnValue(of({ ref: 'reference-a-0000', uploadUrl: 'https://tum.live/u' }));
        vi.spyOn(service, 'transfer').mockReturnValue(throwError(() => new Error('HTTP 413')));
        const discardSpy = vi.spyOn(service, 'discard').mockReturnValue(of(undefined));
        chooseFile();

        component.upload();
        fixture.detectChanges();

        expect(discardSpy).toHaveBeenCalledWith(7, 'reference-a-0000');
        expect(component.transferError()).toBe('HTTP 413');
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
        vi.spyOn(service, 'start').mockReturnValue(of({ ref: 'reference-a-0000', uploadUrl: 'https://tum.live/u' }));
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
        start.next({ ref: 'reference-a-0000', uploadUrl: 'https://tum.live/u' });

        expect(transferSpy).not.toHaveBeenCalled();
        expect(discardSpy).toHaveBeenCalledWith(7, 'reference-a-0000');
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
        startB.next({ ref: 'reference-b-0000', uploadUrl: 'https://tum.live/b' });

        expect(startSpy).toHaveBeenCalledTimes(2);
        expect(transferSpy).toHaveBeenCalledWith('https://tum.live/b', expect.any(File));
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
        vi.spyOn(service, 'start').mockReturnValue(of({ ref: 'reference-a-0000', uploadUrl: 'https://tum.live/u' }));
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
        vi.spyOn(service, 'start').mockReturnValue(of({ ref: 'reference-a-0000', uploadUrl: 'https://tum.live/u' }));
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
        start.next({ ref: 'reference-a-0000', uploadUrl: 'https://tum.live/u' });

        expect(discardSpy).toHaveBeenCalledExactlyOnceWith(7, 'reference-a-0000');
        expect(transferSpy).not.toHaveBeenCalled();
    });

    it('reports a file that was sent completely and keeps its upload when the page is left', () => {
        render({ connected: true });
        vi.spyOn(service, 'start').mockReturnValue(of({ ref: 'reference-a-0000', uploadUrl: 'https://tum.live/u' }));
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
