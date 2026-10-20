import { ChangeDetectionStrategy, Component, DestroyRef, effect, inject, input, output, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TumAetUiButtonDirective, TumAetUiMessageComponent, TumAetUiProgressBarComponent } from '@tumaet/ui-angular';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TumLiveUploadService, TumLiveUploadState } from 'app/lecture/manage/lecture-units/services/tum-live-upload.service';
import { EMPTY, Subscription, catchError, interval, switchMap } from 'rxjs';

/** How often the state of an upload that TUM.Live converts is read again. */
const POLL_INTERVAL_MS = 15_000;

/**
 * Uploads the video of an attachment video unit to the course's TUM.Live course. Shown only when the course is connected. The file goes from the browser
 * straight to TUM.Live; Artemis attaches the converted video in the background, so the page may be left while TUM.Live converts it.
 */
@Component({
    selector: 'jhi-tum-live-upload',
    templateUrl: './tum-live-upload.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [TranslateDirective, ArtemisTranslatePipe, TumAetUiButtonDirective, TumAetUiMessageComponent, TumAetUiProgressBarComponent],
})
export class TumLiveUploadComponent {
    private readonly tumLiveUploadService = inject(TumLiveUploadService);
    private readonly destroyRef = inject(DestroyRef);

    readonly lectureUnitId = input.required<number>();
    /** Emits when an upload of this page was attached to the unit, so the page can show the unit's new video link. */
    readonly attached = output<void>();

    readonly state = signal<TumLiveUploadState | undefined>(undefined);
    readonly file = signal<File | undefined>(undefined);
    /** The share of the file sent so far, while it is sent. */
    readonly transferProgress = signal<number | undefined>(undefined);
    readonly transferError = signal<string | undefined>(undefined);
    readonly attachedNow = signal(false);
    /** Set while a cancelled start or a discard is still answered; a new upload waits for it, since the server would reuse the reference it gives up. */
    readonly busy = signal(false);

    private transfer?: Subscription;
    private poll?: Subscription;
    /** The reference of the upload this page is sending, once the server named it, and the unit it is for. */
    private activeRef?: string;
    private activeUnitId?: number;
    /** The reference of the last upload this page started, until it is attached or given up, to recognise its attachment. */
    private startedRef?: string;
    /** The upload whose start request is still answered; cancelling it marks it, so its reference is discarded once known. */
    private starting?: { cancelled: boolean };

    constructor() {
        effect(() => {
            const lectureUnitId = this.lectureUnitId();
            untracked(() => this.load(lectureUnitId));
        });
        this.destroyRef.onDestroy(() => {
            this.transfer?.unsubscribe();
            this.poll?.unsubscribe();
        });
    }

    onFileChosen(event: Event): void {
        this.file.set((event.target as HTMLInputElement).files?.[0]);
        this.transferError.set(undefined);
    }

    upload(): void {
        const file = this.file();
        if (!file || this.busy()) {
            return;
        }
        this.transferError.set(undefined);
        this.attachedNow.set(false);
        this.transferProgress.set(0);
        const unitId = this.lectureUnitId();
        const attempt = { cancelled: false };
        this.starting = attempt;
        this.tumLiveUploadService
            .start(unitId)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (target) => {
                    if (this.starting === attempt) {
                        this.starting = undefined;
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
                            this.file.set(undefined);
                            this.load(this.lectureUnitId());
                        },
                        error: (error: Error) => this.giveUp(unitId, target.ref, error.message),
                    });
                },
                error: () => {
                    if (this.starting === attempt) {
                        this.starting = undefined;
                    }
                    if (attempt.cancelled) {
                        this.busy.set(false);
                        this.load(this.lectureUnitId());
                        return;
                    }
                    // The error alert names the reason.
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
        const ref = this.state()?.pendingRef;
        if (ref) {
            this.discard(this.lectureUnitId(), ref);
        }
    }

    private giveUp(unitId: number, ref: string, message: string): void {
        this.activeRef = undefined;
        this.transferProgress.set(undefined);
        this.transferError.set(message);
        this.discard(unitId, ref);
    }

    /** Discards an upload of the unit it was started for, which the component shows unless it was given another unit meanwhile. */
    private discard(unitId: number, ref: string): void {
        if (this.startedRef === ref) {
            this.startedRef = undefined;
        }
        this.busy.set(true);
        const done = () => {
            this.busy.set(false);
            this.load(this.lectureUnitId());
        };
        this.tumLiveUploadService.discard(unitId, ref).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({ complete: done, error: done });
    }

    private load(lectureUnitId: number): void {
        this.tumLiveUploadService
            .getState(lectureUnitId)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({ next: (state) => this.apply(state), error: () => this.state.set(undefined) });
    }

    private apply(state: TumLiveUploadState): void {
        const watched = this.state()?.pendingRef ?? this.startedRef;
        this.state.set(state);
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
                .pipe(
                    // A failed request is skipped, so polling goes on.
                    switchMap(() => this.tumLiveUploadService.getState(this.lectureUnitId()).pipe(catchError(() => EMPTY))),
                    takeUntilDestroyed(this.destroyRef),
                )
                .subscribe({ next: (next) => this.apply(next) });
        } else if (!state.pendingRef && this.poll) {
            this.poll.unsubscribe();
            this.poll = undefined;
        }
    }
}
