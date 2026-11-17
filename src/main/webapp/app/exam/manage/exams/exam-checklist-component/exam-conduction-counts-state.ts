import { signal, untracked } from '@angular/core';
import { Observable, Subscription } from 'rxjs';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExamChecklist } from 'app/exam/shared/entities/exam-checklist.model';
import { ExamConductionCounts } from 'app/exam/shared/entities/exam-conduction-counts.model';

/** Keeps the initial checklist and live events together while bounding count refreshes to one per second. */
export class ExamConductionCountsState {
    readonly numberOfStarted = signal(0);
    readonly numberOfSubmitted = signal(0);
    readonly numberOfGeneratedStudentExams = signal(0);

    private exam?: Exam;
    private initialized = false;
    private revision = 0;
    private pending = false;
    private refreshing = false;
    private nextRefreshAt = 0;
    private retriesRemaining = 2;
    private subscription?: Subscription;
    private timeout?: ReturnType<typeof setTimeout>;

    constructor(private readonly fetchCounts: (exam: Exam) => Observable<ExamConductionCounts>) {}

    reset(exam: Exam): void {
        this.destroy();
        this.exam = exam;
        this.initialized = false;
        this.revision = 0;
        this.nextRefreshAt = 0;
        this.retriesRemaining = 2;
        this.numberOfStarted.set(0);
        this.numberOfSubmitted.set(0);
        this.numberOfGeneratedStudentExams.set(0);
    }

    initialize(checklist?: ExamChecklist): void {
        if (checklist) {
            // A response can already include an event received while the request was in flight. Do not add its delta twice.
            this.numberOfStarted.update((count) => Math.max(count, checklist.numberOfExamsStarted ?? 0));
            this.numberOfSubmitted.update((count) => Math.max(count, checklist.numberOfExamsSubmitted ?? 0));
            this.numberOfGeneratedStudentExams.update((count) => Math.max(count, checklist.numberOfGeneratedStudentExams ?? 0));
        }
        this.initialized = true;
        this.keepCountsConsistent();
        this.scheduleRefresh();
    }

    started(): void {
        this.numberOfStarted.update((count) => count + 1);
        this.keepCountsConsistent();
        this.changed();
    }

    submitted(): void {
        this.numberOfSubmitted.update((count) => count + 1);
        this.keepCountsConsistent();
        this.changed();
    }

    /**
     * Keeps what is displayed possible while the authoritative counts are on their way: a student exam exists before it is started. Without this, the start event of the next
     * student raises the started count at once while the total waits for the response, which shows more than 100 percent.
     */
    private keepCountsConsistent(): void {
        // Read untracked: the first statistics can arrive while an effect of the component runs, which must not start to depend on the started count and reset the state with it.
        const started = untracked(this.numberOfStarted);
        this.numberOfGeneratedStudentExams.update((count) => Math.max(count, started));
    }

    private changed(): void {
        this.revision++;
        // Reconcile regular exams too: their initial snapshot can already include a start before its notification arrives.
        this.retriesRemaining = 2;
        this.pending = true;
        this.scheduleRefresh();
    }

    private scheduleRefresh(): void {
        if (!this.exam || !this.initialized || !this.pending || this.refreshing || this.timeout !== undefined) {
            return;
        }
        const delay = this.nextRefreshAt - Date.now();
        if (delay > 0) {
            this.timeout = setTimeout(() => {
                this.timeout = undefined;
                this.refresh();
            }, delay);
        } else {
            this.refresh();
        }
    }

    private refresh(): void {
        const exam = this.exam!;
        const revision = this.revision;
        this.pending = false;
        this.refreshing = true;
        this.nextRefreshAt = Date.now() + 1000;
        const subscription = new Subscription();
        this.subscription = subscription;
        subscription.add(
            this.fetchCounts(exam).subscribe({
                next: (counts) => {
                    if (this.exam !== exam) {
                        return;
                    }
                    this.retriesRemaining = 2;
                    // Events published after this request began may not be in its snapshot. A retained trailing refresh reconciles them.
                    const reconcile = (current: number, authoritative: number) => (revision === this.revision ? authoritative : Math.max(current, authoritative));
                    this.numberOfGeneratedStudentExams.update((count) => reconcile(count, counts.numberOfGeneratedStudentExams));
                    this.numberOfStarted.update((count) => reconcile(count, counts.numberOfExamsStarted));
                    this.numberOfSubmitted.update((count) => reconcile(count, counts.numberOfExamsSubmitted));
                    this.keepCountsConsistent();
                },
                error: () => {
                    if (this.exam !== exam) {
                        return;
                    }
                    if (this.retriesRemaining > 0) {
                        this.retriesRemaining--;
                        this.pending = true;
                        this.nextRefreshAt = Math.max(this.nextRefreshAt, Date.now() + 1000);
                    }
                    this.finished();
                },
                complete: () => this.finished(),
            }),
        );
    }

    private finished(): void {
        this.refreshing = false;
        this.scheduleRefresh();
    }

    destroy(): void {
        this.exam = undefined;
        this.pending = false;
        this.refreshing = false;
        this.subscription?.unsubscribe();
        clearTimeout(this.timeout);
        this.timeout = undefined;
    }
}
