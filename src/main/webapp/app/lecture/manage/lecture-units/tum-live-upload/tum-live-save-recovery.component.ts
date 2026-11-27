import { ChangeDetectionStrategy, Component, DestroyRef, inject, input, linkedSignal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { TumAetUiButtonDirective } from '@tumaet/ui-angular/button';
import { TumAetUiMessageComponent } from '@tumaet/ui-angular/message';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { LectureService } from 'app/lecture/manage/services/lecture.service';
import { LectureUnitType } from 'app/lecture/shared/entities/lecture-unit/lectureUnit.model';
import { finalize, map } from 'rxjs';
import { RecordingNavigation, RecordingSaveScope, lectureEditorCommands, unitEditorCommands } from './tum-live-creation';

export interface RecordingRecoveryScope extends RecordingSaveScope {
    readonly detail?: string;
}

@Component({
    selector: 'jhi-tum-live-save-recovery',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [ArtemisTranslatePipe, TumAetUiButtonDirective, TumAetUiMessageComponent, RouterLink],
    template: `
        <section class="flex flex-col gap-2" data-testid="tum-live-save-recovery">
            <tumaet-ui-message severity="warn" [text]="'artemisApp.tumLiveUpload.unknownSave' | artemisTranslate" />
            @if (scope().detail; as detail) {
                <p>{{ detail }}</p>
            }
            @if (failed()) {
                <tumaet-ui-message severity="error" [text]="'artemisApp.tumLiveUpload.checkSaveFailure' | artemisTranslate" />
            }
            @if (entries(); as saved) {
                <p>{{ (saved.length ? 'artemisApp.tumLiveUpload.checkedSave' : 'artemisApp.tumLiveUpload.noSavedEntries') | artemisTranslate }}</p>
                @if (saved.length) {
                    <ul class="divide-y divide-border">
                        @for (entry of saved; track entry.id) {
                            <li class="flex items-center justify-between gap-2 py-2">
                                <span>{{ entry.name }}</span>
                                <a tumAetUiButton variant="outlined" size="small" [routerLink]="entry.commands" [info]="entry.navigation">
                                    {{ 'artemisApp.tumLiveUpload.open' | artemisTranslate }}
                                </a>
                            </li>
                        }
                    </ul>
                }
            }
            <div>
                <button tumAetUiButton variant="outlined" size="small" type="button" [disabled]="loading()" (click)="check()">
                    {{ 'artemisApp.tumLiveUpload.checkSave' | artemisTranslate }}
                </button>
            </div>
        </section>
    `,
})
export class TumLiveSaveRecoveryComponent {
    private readonly lectures = inject(LectureService);
    private readonly destroyRef = inject(DestroyRef);

    readonly scope = input.required<RecordingRecoveryScope>();

    readonly entries = linkedSignal<RecordingRecoveryScope, { id: number; name?: string; commands: (string | number)[]; navigation: RecordingNavigation }[] | undefined>({
        source: this.scope,
        computation: () => undefined,
    });
    readonly loading = linkedSignal({ source: this.scope, computation: () => false });
    readonly failed = linkedSignal({ source: this.scope, computation: () => false });

    protected check(): void {
        if (this.loading()) {
            return;
        }
        const scope = this.scope();
        const { courseId, lectureId } = scope;
        this.loading.set(true);
        this.failed.set(false);
        const load = lectureId
            ? this.lectures
                  .findWithDetails(lectureId)
                  .pipe(
                      map((lecture) =>
                          (lecture.lectureUnits ?? []).filter((unit) => unit.type === LectureUnitType.ATTACHMENT_VIDEO).map((unit) => ({ id: unit.id!, name: unit.name })),
                      ),
                  )
            : this.lectures.findAllByCourseId(courseId).pipe(map((lectures) => lectures.map((lecture) => ({ id: lecture.id!, name: lecture.title }))));
        load.pipe(
            takeUntilDestroyed(this.destroyRef),
            finalize(() => {
                if (!this.destroyRef.destroyed && this.scope() === scope) {
                    this.loading.set(false);
                }
            }),
        ).subscribe({
            next: (entries) => {
                if (this.scope() !== scope) {
                    return;
                }
                this.entries.set(
                    entries
                        .toSorted((first, second) => second.id - first.id)
                        .map(({ id, name }) => {
                            const target = { courseId, lectureId: lectureId ?? id, unitId: lectureId ? id : undefined };
                            return {
                                id,
                                name,
                                commands: lectureId ? unitEditorCommands(target) : lectureEditorCommands(target),
                                navigation: new RecordingNavigation(target),
                            };
                        }),
                );
            },
            error: () => {
                if (this.scope() === scope) {
                    this.failed.set(true);
                }
            },
        });
    }
}
