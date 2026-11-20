import { Location } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { DestroyRef, Signal, computed, inject } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { NavigationBehaviorOptions, Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { TumAetUiConfirmationRequest, TumAetUiConfirmationService } from '@tumaet/ui-angular';
import { AttachmentVideoUnit } from 'app/lecture/shared/entities/lecture-unit/attachmentVideoUnit.model';
import { AccountService } from 'app/core/auth/account.service';
import { TumLiveUploadService } from 'app/lecture/manage/lecture-units/services/tum-live-upload.service';
import { Observable, catchError, distinctUntilChanged, of, startWith, switchMap } from 'rxjs';

const CHECKPOINT = 'tumLiveRecordingCheckpoint';

export interface RecordingTarget {
    courseId: number;
    lectureId: number;
    unitId?: number;
}

interface RecordingCheckpoint extends RecordingTarget {
    unitSavePending?: true;
}

/** Angular navigation info is scoped to one navigation and stays in memory, unlike history state. */
export class RecordingNavigation {
    constructor(
        readonly target: RecordingTarget,
        private file?: File,
        readonly unit?: AttachmentVideoUnit,
        readonly fromCreate = false,
    ) {}

    takeFile(): File | undefined {
        const file = this.file;
        this.file = undefined;
        return file;
    }
}

export function recordingNavigation(info: unknown, courseId: number | undefined, lectureId: number | undefined): RecordingNavigation | undefined {
    return info instanceof RecordingNavigation && info.target.courseId === courseId && info.target.lectureId === lectureId ? info : undefined;
}

export function lectureEditorCommands(target: RecordingTarget): (string | number)[] {
    return ['/course-management', target.courseId, 'lectures', target.lectureId, 'edit'];
}

export function unitEditorCommands(target: RecordingTarget): (string | number)[] {
    return ['/course-management', target.courseId, 'lectures', target.lectureId, 'unit-management', 'attachment-video-units', target.unitId!, 'edit'];
}

/** The source form remembers acknowledged IDs and an unresolved save in history. The file stays in memory. */
export class RecordingRedirect {
    private readonly router = inject(Router);
    private readonly location = inject(Location);
    private readonly destroyRef = inject(DestroyRef);
    private next?: { url: string; info: RecordingNavigation };

    checkpoint(target: RecordingCheckpoint): void {
        const state = (this.location.getState() ?? {}) as Record<string, unknown>;
        state[CHECKPOINT] = target;
        this.location.replaceState(this.location.path(), '', state);
    }

    recover(courseId: number, lectureId?: number): RecordingCheckpoint | undefined {
        const state = this.location.getState() as Record<string, unknown>;
        const target = state?.[CHECKPOINT] as RecordingCheckpoint | undefined;
        if (target?.courseId === courseId && Number.isSafeInteger(target.lectureId) && (lectureId === undefined || target.lectureId === lectureId)) {
            return target;
        }
        return undefined;
    }

    allows(nextUrl: string | undefined): boolean {
        return !!this.next && this.next.url === nextUrl && this.router.currentNavigation()?.extras.info === this.next.info;
    }

    async open(target: RecordingTarget, standalone: boolean, file?: File, unit?: AttachmentVideoUnit, state?: NavigationBehaviorOptions['state']): Promise<boolean> {
        if (this.next) {
            return false;
        }
        const url = this.router.createUrlTree(standalone ? unitEditorCommands(target) : lectureEditorCommands(target));
        const info = new RecordingNavigation(target, file, unit, true);
        const next = { url: this.router.serializeUrl(url), info };
        const source = this.location.path();
        this.next = next;
        let opened = false;
        try {
            opened = await this.router.navigateByUrl(url, { replaceUrl: true, info, state });
            return opened;
        } catch {
            return false;
        } finally {
            if (this.next === next) {
                this.next = undefined;
                // Router rollback can restore the previous navigation's state, which has no checkpoint.
                if (!opened && !this.destroyRef.destroyed && this.location.path() === source) {
                    this.checkpoint(target);
                }
            }
        }
    }
}

export function injectRecordingAvailability(courseId: Signal<number | undefined>): Signal<boolean> {
    const account = inject(AccountService);
    const service = inject(TumLiveUploadService);
    const eligibleCourse = computed(() => {
        const id = courseId();
        return id && account.isAtLeastEditorInCourseWithId(id) ? id : undefined;
    });
    return toSignal(
        toObservable(eligibleCourse).pipe(
            distinctUntilChanged(),
            switchMap((id) =>
                id === undefined
                    ? of(false)
                    : service.isAvailable(id).pipe(
                          catchError(() => of(false)),
                          startWith(false),
                      ),
            ),
        ),
        { initialValue: false },
    );
}

/** Only known pre-write rejection statuses allow an immediate retry of a non-idempotent creation request. */
export function createOutcomeIsUnknown(error: unknown): boolean {
    if (!(error instanceof HttpErrorResponse)) {
        return true;
    }
    const message: unknown = error.error?.message;
    const preWriteErrors = [
        'error.idexists',
        'error.idExists',
        'error.courseMissing',
        'error.invalidDateRange',
        'error.attachmentLinkRequired',
        'error.invalidVideoSourceScheme',
        'error.invalidYouTubeUrl',
        'error.invalidPdf',
    ];
    const rejected =
        [401, 403, 404, 413, 415].includes(error.status) ||
        (error.status === 400 && (Array.isArray(error.error?.fieldErrors) || (typeof message === 'string' && preWriteErrors.includes(message))));
    return !rejected;
}

export function unknownCreateDetail(error: unknown): string | undefined {
    if (!(error instanceof HttpErrorResponse) || error.status !== 400) {
        return undefined;
    }
    const detail: unknown = error.error?.detail ?? error.error?.title;
    return typeof detail === 'string' ? detail : undefined;
}

const UPLOAD_TRANSLATION = 'artemisApp.tumLiveUpload.';

export function confirmRecordingLeave(service: TumAetUiConfirmationService, translate: TranslateService, phase: 'saving' | 'sending' | 'sent'): Observable<boolean> {
    return confirmRecordingAction(
        service,
        translate,
        phase === 'saving' ? 'savingTitle' : 'leaveTitle',
        phase === 'saving' ? 'savingMessage' : phase === 'sent' ? 'leaveSentMessage' : 'leaveMessage',
        phase === 'saving' ? 'leave' : 'leaveUpload',
    );
}

export function confirmReplacingRecording(service: TumAetUiConfirmationService, translate: TranslateService): Observable<boolean> {
    return confirmRecordingAction(service, translate, 'replacePendingLinkTitle', 'replacePendingLink', 'replacePendingLinkConfirm');
}

function confirmRecordingAction(service: TumAetUiConfirmationService, translate: TranslateService, header: string, message: string, acceptLabel: string): Observable<boolean> {
    return new Observable<boolean>((subscriber) => {
        const decide = (value: boolean) => {
            subscriber.next(value);
            subscriber.complete();
        };
        const request: TumAetUiConfirmationRequest = {
            header: translate.instant(UPLOAD_TRANSLATION + header),
            message: translate.instant(UPLOAD_TRANSLATION + message),
            acceptLabel: translate.instant(UPLOAD_TRANSLATION + acceptLabel),
            rejectLabel: translate.instant(UPLOAD_TRANSLATION + 'stay'),
            acceptSeverity: 'danger',
            rejectSeverity: 'primary',
            accept: () => decide(true),
            reject: () => decide(false),
        };
        service.confirm(request);
        return () => {
            if (service.request(undefined) === request) {
                service.close(undefined);
            }
        };
    });
}

export interface RecordingLeaveComponent {
    confirmRecordingLeave(nextUrl?: string): Observable<boolean> | boolean;
}
