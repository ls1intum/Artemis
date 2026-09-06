import { ChangeDetectionStrategy, Component, DestroyRef, inject, input, output } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Subject } from 'rxjs';
import { faTrash, faUsers, faWrench } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TumUiButtonDirective } from '@tumaet/ui-angular';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { DeleteButtonDirective } from 'app/shared-ui/delete-dialog/directive/delete-button.directive';
import { TutorialGroupApi } from 'app/openapi/api/tutorial-group-api';

@Component({
    selector: 'jhi-tutorial-group-row-buttons',
    templateUrl: './tutorial-group-row-buttons.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [FaIconComponent, TranslateDirective, RouterLink, DeleteButtonDirective, TumUiButtonDirective],
})
export class TutorialGroupRowButtonsComponent {
    private readonly tutorialGroupApiService = inject(TutorialGroupApi);
    private readonly destroyRef = inject(DestroyRef);

    readonly isAtLeastInstructor = input(false);
    readonly courseId = input.required<number>();
    readonly tutorialGroup = input.required<TutorialGroup>();

    readonly tutorialGroupDeleted = output<void>();

    private readonly dialogErrorSource = new Subject<string>();
    readonly dialogError$ = this.dialogErrorSource.asObservable();

    protected readonly faWrench = faWrench;
    protected readonly faUsers = faUsers;
    protected readonly faTrash = faTrash;

    constructor() {
        // complete() ends the stream for whoever is still subscribed; unsubscribe() would close the subject so that
        // any later next() throws ObjectUnsubscribedError instead.
        this.destroyRef.onDestroy(() => this.dialogErrorSource.complete());
    }

    /** Deletes the group and, on success, closes the delete dialog by clearing its error stream. */
    deleteTutorialGroup(): void {
        const tutorialGroupId = this.tutorialGroup().id;
        if (tutorialGroupId === undefined) {
            return;
        }
        this.tutorialGroupApiService
            .deleteTutorialGroup(this.courseId(), tutorialGroupId)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: () => {
                    this.dialogErrorSource.next('');
                    this.tutorialGroupDeleted.emit();
                },
                error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
            });
    }
}
