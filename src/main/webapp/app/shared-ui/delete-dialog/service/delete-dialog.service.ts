import { ApplicationRef, DestroyRef, EnvironmentInjector, Service, createComponent, inject, signal } from '@angular/core';
import { DOCUMENT } from '@angular/common';
import { DeleteDialogComponent } from 'app/shared-ui/delete-dialog/component/delete-dialog.component';
import { DeleteDialogData } from 'app/shared-ui/delete-dialog/delete-dialog.model';
import { AlertService } from 'app/foundation/service/alert.service';
import { TranslateService } from '@ngx-translate/core';
import { Observable, Subject, Subscription } from 'rxjs';
import { cloneWith } from 'app/foundation/util/deep-clone.util';
import { setInputs } from 'app/foundation/util/set-inputs.util';

/** The lifecycle exposed to callers that need to distinguish dismissal from confirmation. */
export interface DeleteDialogRef {
    readonly onClose: Observable<void>;
    close(): void;
}

@Service()
export class DeleteDialogService {
    private readonly applicationRef = inject(ApplicationRef);
    private readonly environmentInjector = inject(EnvironmentInjector);
    private readonly document = inject(DOCUMENT);
    private readonly destroyRef = inject(DestroyRef);
    private readonly alertService = inject(AlertService);
    private readonly translateService = inject(TranslateService);

    readonly dialogRef = signal<DeleteDialogRef | undefined>(undefined);
    private errorSubscription?: Subscription;

    constructor() {
        this.destroyRef.onDestroy(() => {
            this.dialogRef()?.close();
            this.errorSubscription?.unsubscribe();
        });
    }

    /** Opens a TUM AET UI confirmation while preserving the caller's delete and error callbacks. */
    openDeleteDialog(deleteDialogData: DeleteDialogData, _animation = true): void {
        this.alertService.closeAll();
        this.errorSubscription?.unsubscribe();

        const component = createComponent(DeleteDialogComponent, { environmentInjector: this.environmentInjector });
        const closed = new Subject<void>();
        const ref: DeleteDialogRef = {
            onClose: closed.asObservable(),
            close: () => component.instance.clear(),
        };
        setInputs(component, {
            data: cloneWith(deleteDialogData, {
                translateValues: cloneWith(deleteDialogData.translateValues, { title: deleteDialogData.entityTitle }),
                // Observables and callbacks belong to the caller and must keep their identity.
                delete: deleteDialogData.delete,
                dialogError: deleteDialogData.dialogError,
                fetchEntitySummary: deleteDialogData.fetchEntitySummary,
                fetchCategorizedEntitySummary: deleteDialogData.fetchCategorizedEntitySummary,
            }),
            header: this.getDialogHeader(deleteDialogData.actionType),
        });

        component.instance.closed.subscribe(() => {
            this.applicationRef.detachView(component.hostView);
            component.destroy();
            component.location.nativeElement.remove();
            if (this.dialogRef() === ref) {
                this.dialogRef.set(undefined);
            }
            closed.next();
            closed.complete();
        });

        // Subscribe before rendering: an action may complete synchronously from its callback.
        if (deleteDialogData.dialogError) {
            this.errorSubscription = deleteDialogData.dialogError.subscribe((errorMessage) => {
                if (errorMessage !== '') {
                    this.alertService.error(errorMessage);
                }
                this.errorSubscription?.unsubscribe();
                this.errorSubscription = undefined;
            });
        }

        this.dialogRef.set(ref);
        this.document.body.appendChild(component.location.nativeElement);
        this.applicationRef.attachView(component.hostView);
        component.changeDetectorRef.detectChanges();
    }

    private getDialogHeader(actionType?: string): string {
        const headerKeys: Record<string, string> = {
            delete: 'entity.delete.title',
            reset: 'entity.reset.title',
            cleanup: 'entity.cleanup.title',
            remove: 'entity.remove.title',
            unlink: 'entity.unlink.title',
            noButtonTextDelete: 'entity.noButtonTextDelete.title',
            'end-now': 'entity.endNow.title',
            endNow: 'entity.endNow.title',
        };
        return this.translateService.instant(headerKeys[actionType || 'delete'] || 'entity.delete.title');
    }
}
