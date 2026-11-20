import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, computed, effect, inject, input, output, signal, untracked, viewChild } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { TumLiveFilePickerComponent } from './tum-live-file-picker.component';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TumAetUiButtonDirective, TumAetUiMessageComponent, TumAetUiProgressBarComponent } from '@tumaet/ui-angular';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TumLiveTransferError, TumLiveUploadService, TumLiveUploadState } from 'app/lecture/manage/lecture-units/services/tum-live-upload.service';
import { Subscription, interval, timer } from 'rxjs';

/** How often the state of an upload that TUM.Live converts is read again. */
const POLL_INTERVAL_MS = 15_000;

/**
 * Uploads the video of an attachment video unit to the course's TUM.Live course. Shown only when the course is connected. Artemis forwards the file to
 * TUM.Live and attaches the converted video in the background, so the page may be left while TUM.Live converts it.
 */
@Component({
    selector: 'jhi-tum-live-upload',
    templateUrl: './tum-live-upload.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [TranslateDirective, ArtemisTranslatePipe, TumAetUiButtonDirective, TumAetUiMessageComponent, TumAetUiProgressBarComponent, TumLiveFilePickerComponent],
})
export class TumLiveUploadComponent {
    private readonly tumLiveUploadService = inject(TumLiveUploadService);
    private readonly destroyRef = inject(DestroyRef);

    readonly lectureUnitId = input.required<number>();
    /** One selected file, handed off by the keyed editor of this newly saved unit. */
    readonly initialFile = input<File>();
    readonly initialFileConsumed = output<void>();
    private readonly heading = viewChild<ElementRef<HTMLElement>>('heading');
    private didTakeInitialFile = false;
    private needsFocus = false;

    /** Emits when an upload of this page was attached to the unit, so the page can show the unit's new video link. */
    readonly attached = output<void>();
    /** Emits when this page sent a file to TUM.Live, so the page knows the unit has a video there now, also if it is left before the video is attached. */
    readonly transferred = output<void>();

    readonly state = signal<TumLiveUploadState | undefined>(undefined);
    readonly file = signal<File | undefined>(undefined);
    /** The share of the file sent so far, while it is sent. */
    readonly transferProgress = signal<number | undefined>(undefined);
    readonly transferring = computed(() => this.transferProgress() !== undefined);
    readonly uncertainRef = signal<string | undefined>(undefined);
    readonly acceptedRef = signal<string | undefined>(undefined);
    readonly pendingRef = computed(() => this.uncertainRef() ?? this.acceptedRef() ?? this.state()?.pendingRef);
    protected readonly statusMessage = computed(() => {
        if (this.transferring()) {
            return this.transferProgress() === 100 ? 'artemisApp.tumLiveUpload.awaiting' : 'artemisApp.tumLiveUpload.uploading';
        }
        if (this.uncertainRef()) {
            return 'artemisApp.tumLiveUpload.checkingTransfer';
        }
        if (this.pendingRef()) {
            return this.acceptedRef() || this.state()?.converting ? 'artemisApp.tumLiveUpload.processing' : 'artemisApp.tumLiveUpload.pending';
        }
        return undefined;
    });
    readonly transferError = signal<TumLiveTransferError | undefined>(undefined);
    readonly attachedNow = signal(false);
    /** Set while a cancelled start or a discard is still answered, so the editor waits for the pending upload to be released. */
    readonly busy = signal(false);

    private transfer?: Subscription;
    private poll?: Subscription;
    private stateRead?: Subscription;
    /** Reads the state again after reading it failed, so the section comes back without opening the editor again. */
    private retry?: Subscription;
    /** The reference of the upload this page is sending, once the server named it, and the unit it is for. */
    private activeRef?: string;
    private activeUnitId?: number;
    /** The reference of the last upload this page started, until it is attached or given up, to recognise its attachment. */
    private startedRef?: string;
    /** The upload whose start request is still answered; cancelling it marks it, so its reference is discarded once known. */
    private starting?: { cancelled: boolean };
    private destroyed = false;

    constructor() {
        effect(() => {
            const lectureUnitId = this.lectureUnitId();
            untracked(() => this.load(lectureUnitId));
        });
        effect(() => {
            const file = this.initialFile();
            if (file && !this.didTakeInitialFile) {
                this.didTakeInitialFile = true;
                this.needsFocus = true;
                untracked(() => {
                    this.file.set(file);
                    this.initialFileConsumed.emit();
                    this.upload();
                });
            }
        });
        effect(() => {
            const heading = this.heading();
            if (heading && this.needsFocus) {
                this.needsFocus = false;
                untracked(() => heading.nativeElement.focus());
            }
        });
        this.destroyRef.onDestroy(() => {
            this.destroyed = true;
            // The start may have created the upload already; its answer still arrives and gives it up.
            if (this.starting) {
                this.starting.cancelled = true;
            }
            this.transfer?.unsubscribe();
            this.poll?.unsubscribe();
            this.retry?.unsubscribe();
            // Leaving the page stops the transfer, so its upload cannot finish: give it up rather than leave it waiting. Not bound to this component, which is gone.
            if (this.activeRef !== undefined && this.activeUnitId !== undefined) {
                // Best effort: if it fails, the background job gives the upload up once it is overdue.
                this.tumLiveUploadService.discard(this.activeUnitId, this.activeRef).subscribe({ error: () => undefined });
            }
        });
    }

    upload(): void {
        const file = this.file();
        if (!file || this.busy() || this.transferring() || this.pendingRef()) {
            return;
        }
        this.transferError.set(undefined);
        this.attachedNow.set(false);
        this.transferProgress.set(0);
        const unitId = this.lectureUnitId();
        const attempt = { cancelled: false };
        this.starting = attempt;
        // Not bound to the component: an upload the start created must be given up also when the page is left before the answer.
        this.tumLiveUploadService.start(unitId).subscribe({
            next: (target) => {
                if (this.starting === attempt) {
                    this.starting = undefined;
                }
                if (this.destroyed) {
                    this.tumLiveUploadService.discard(unitId, target.ref).subscribe({ error: () => undefined });
                    return;
                }
                if (attempt.cancelled) {
                    this.discard(unitId, target.ref);
                    return;
                }
                this.activeRef = target.ref;
                this.activeUnitId = unitId;
                this.startedRef = target.ref;
                this.transfer = this.tumLiveUploadService.transfer(target.uploadUrl, file).subscribe({
                    next: (event) => this.transferProgress.set(event.type === 'progress' ? event.percent : 100),
                    complete: () => {
                        this.activeRef = undefined;
                        this.transferProgress.set(undefined);
                        this.markAccepted(target.ref);
                        this.load(this.lectureUnitId());
                    },
                    error: (error: TumLiveTransferError) => {
                        if ([0, 502, 503, 504].includes(error.status)) {
                            this.activeRef = undefined;
                            this.uncertainRef.set(target.ref);
                            this.transferProgress.set(undefined);
                            this.transferError.set(error);
                            this.load(unitId);
                        } else if (error.status === 409) {
                            // Another request may already be sending this reference. Keep its upload pending.
                            this.activeRef = undefined;
                            this.transferProgress.set(undefined);
                            this.transferError.set(error);
                            this.load(this.lectureUnitId());
                        } else {
                            this.giveUp(unitId, target.ref, error);
                        }
                    },
                });
            },
            error: (error: unknown) => {
                if (this.starting === attempt) {
                    this.starting = undefined;
                }
                if (this.destroyed) {
                    return;
                }
                if (attempt.cancelled) {
                    this.busy.set(false);
                    this.load(this.lectureUnitId());
                    return;
                }
                if (error instanceof HttpErrorResponse) {
                    const key: unknown = error.error?.message;
                    const translated = typeof key === 'string' && key.startsWith('error.') ? key : undefined;
                    this.transferError.set(new TumLiveTransferError(error.message, error.status, translated));
                }
                this.transferProgress.set(undefined);
                this.load(this.lectureUnitId());
            },
        });
    }

    /** Cancels the running upload; its TUM.Live lecture is deleted, also when the server has not named it yet. */
    cancel(): void {
        this.transfer?.unsubscribe();
        this.transferProgress.set(undefined);
        const ref = this.activeRef;
        const unitId = this.activeUnitId ?? this.lectureUnitId();
        this.activeRef = undefined;
        if (ref) {
            this.discard(unitId, ref);
        } else if (this.starting) {
            // The start may still create the upload; its answer names it, and it is discarded then.
            this.starting.cancelled = true;
            this.starting = undefined;
            this.busy.set(true);
        }
    }

    /** Gives up the upload that TUM.Live converts, for example to start another one. */
    discardPending(): void {
        const ref = this.pendingRef();
        if (ref) {
            this.uncertainRef.set(undefined);
            this.acceptedRef.set(undefined);
            this.transferError.set(undefined);
            this.discard(this.lectureUnitId(), ref);
        }
    }

    private giveUp(unitId: number, ref: string, error: TumLiveTransferError): void {
        this.activeRef = undefined;
        this.transferProgress.set(undefined);
        this.transferError.set(error);
        this.discard(unitId, ref);
    }

    /** Discards an upload of the unit it was started for, which the component shows unless it was given another unit meanwhile. */
    private discard(unitId: number, ref: string): void {
        if (this.startedRef === ref) {
            this.startedRef = undefined;
        }
        this.busy.set(true);
        const done = () => {
            if (this.destroyed) {
                return;
            }
            this.busy.set(false);
            this.load(this.lectureUnitId());
        };
        // Not bound to the component: closing the editor must not abort the request, or the upload stays pending.
        this.tumLiveUploadService.discard(unitId, ref).subscribe({ complete: done, error: done });
    }

    private load(lectureUnitId: number): void {
        this.retry?.unsubscribe();
        this.retry = undefined;
        this.stateRead?.unsubscribe();
        this.stateRead = this.tumLiveUploadService
            .getState(lectureUnitId)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (state) => this.apply(state),
                error: () => {
                    // The section stays as it was and reads the state again later; an upload that is attached meanwhile is then still recognised.
                    this.retry = timer(POLL_INTERVAL_MS)
                        .pipe(takeUntilDestroyed(this.destroyRef))
                        .subscribe(() => this.load(this.lectureUnitId()));
                },
            });
    }

    private markAccepted(ref: string): void {
        this.acceptedRef.set(ref);
        this.file.set(undefined);
        this.transferError.set(undefined);
        this.transferred.emit();
    }

    private apply(state: TumLiveUploadState): void {
        const uncertain = this.uncertainRef();
        const watched = uncertain ?? this.startedRef ?? this.state()?.pendingRef;
        this.state.set(state);
        if (uncertain) {
            if (state.attachedRef === uncertain || (state.pendingRef === uncertain && state.converting)) {
                this.uncertainRef.set(undefined);
                this.markAccepted(uncertain);
            } else if (state.pendingRef !== uncertain) {
                this.uncertainRef.set(undefined);
            }
        }
        if (this.acceptedRef() && this.acceptedRef() !== state.pendingRef) {
            this.acceptedRef.set(undefined);
        }
        if (!state.pendingRef && this.transferError()?.translationKey === 'error.tumLiveUploadInProgress') {
            this.transferError.set(undefined);
        }
        // Attached is the upload that was pending, or that this page started, and is now the one the unit shows; a discarded or dropped one is not.
        if (watched && !state.pendingRef && state.attachedRef === watched) {
            this.startedRef = undefined;
            this.attachedNow.set(true);
            this.attached.emit();
        } else if (watched && !state.pendingRef && watched === this.startedRef && !this.activeRef) {
            // Given up by Artemis (failed, overdue, or replaced by an edit): nothing more to watch for.
            this.startedRef = undefined;
        }
        if (state.pendingRef && !this.poll) {
            this.poll = interval(POLL_INTERVAL_MS)
                .pipe(takeUntilDestroyed(this.destroyRef))
                .subscribe(() => this.load(this.lectureUnitId()));
        } else if (!state.pendingRef && this.poll) {
            this.poll.unsubscribe();
            this.poll = undefined;
        }
    }
}
