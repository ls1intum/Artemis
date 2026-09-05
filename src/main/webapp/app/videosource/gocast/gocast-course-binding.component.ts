import { ChangeDetectionStrategy, Component, effect, inject, input, signal } from '@angular/core';
import { finalize } from 'rxjs';
import { TumUiButtonComponent, TumUiDialogComponent, TumUiMessageComponent } from '@tumaet/ui-angular';

import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { GocastBinding } from './gocast.model';
import { GocastService } from './gocast.service';

@Component({
    selector: 'jhi-gocast-course-binding',
    templateUrl: './gocast-course-binding.component.html',
    imports: [TumUiButtonComponent, TumUiDialogComponent, TumUiMessageComponent, ArtemisDatePipe, ArtemisTranslatePipe, TranslateDirective],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GocastCourseBindingComponent {
    readonly courseId = input.required<number>();

    private readonly gocastService = inject(GocastService);
    private requestSequence = 0;
    private courseGeneration = 0;

    readonly binding = signal<GocastBinding | undefined>(undefined);
    readonly loading = signal(false);
    readonly action = signal<'connect' | 'disconnect' | undefined>(undefined);
    readonly error = signal(false);
    readonly disconnectDialogVisible = signal(false);

    constructor() {
        effect(() => {
            const courseId = this.courseId();
            const courseGeneration = ++this.courseGeneration;
            ++this.requestSequence;
            this.binding.set(undefined);
            this.loading.set(false);
            this.action.set(undefined);
            this.error.set(false);
            this.disconnectDialogVisible.set(false);
            this.refresh(courseId, courseGeneration);
        });
    }

    refresh(courseId = this.courseId(), courseGeneration = this.courseGeneration): void {
        const sequence = ++this.requestSequence;
        this.loading.set(true);
        this.error.set(false);
        this.gocastService
            .getBinding(courseId)
            .pipe(finalize(() => sequence === this.requestSequence && this.isCurrentCourse(courseId, courseGeneration) && this.loading.set(false)))
            .subscribe({
                next: (binding) => {
                    if (sequence === this.requestSequence && this.isCurrentCourse(courseId, courseGeneration)) {
                        this.binding.set(binding);
                    }
                },
                error: () => {
                    if (sequence === this.requestSequence && this.isCurrentCourse(courseId, courseGeneration)) {
                        this.error.set(true);
                    }
                },
            });
    }

    connect(): void {
        if (this.action()) {
            return;
        }
        const courseId = this.courseId();
        const courseGeneration = this.courseGeneration;
        this.action.set('connect');
        this.error.set(false);
        this.gocastService
            .startApproval(courseId)
            .pipe(
                finalize(() => {
                    if (this.isCurrentCourse(courseId, courseGeneration) && this.action() === 'connect') {
                        this.action.set(undefined);
                    }
                }),
            )
            .subscribe({
                next: ({ approvalUrl }) => {
                    if (this.isCurrentCourse(courseId, courseGeneration)) {
                        this.navigateToApproval(approvalUrl);
                    }
                },
                error: () => {
                    if (this.isCurrentCourse(courseId, courseGeneration)) {
                        this.error.set(true);
                    }
                },
            });
    }

    showDisconnectDialog(): void {
        this.disconnectDialogVisible.set(true);
    }

    cancelDisconnect(): void {
        this.disconnectDialogVisible.set(false);
    }

    disconnect(): void {
        if (this.action()) {
            return;
        }
        const courseId = this.courseId();
        const courseGeneration = this.courseGeneration;
        this.disconnectDialogVisible.set(false);
        this.action.set('disconnect');
        this.error.set(false);
        this.gocastService
            .unlink(courseId)
            .pipe(
                finalize(() => {
                    if (this.isCurrentCourse(courseId, courseGeneration) && this.action() === 'disconnect') {
                        this.action.set(undefined);
                    }
                }),
            )
            .subscribe({
                next: () => {
                    if (this.isCurrentCourse(courseId, courseGeneration)) {
                        this.binding.set({ available: true, status: 'UNLINKED' });
                    }
                },
                error: () => {
                    if (this.isCurrentCourse(courseId, courseGeneration)) {
                        this.error.set(true);
                    }
                },
            });
    }

    isRestricted(): boolean {
        return this.binding()?.courseVisibility === 'loggedin' || this.binding()?.courseVisibility === 'enrolled';
    }

    protected navigateToApproval(approvalUrl: string): void {
        window.location.assign(approvalUrl);
    }

    private isCurrentCourse(courseId: number, courseGeneration: number): boolean {
        return courseId === this.courseId() && courseGeneration === this.courseGeneration;
    }
}
