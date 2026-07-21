import { Component, HostListener, inject } from '@angular/core';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';

export interface DialogData {
    slideToReference: string;
}

@Component({
    templateUrl: './enlarge-slide-image.component.html',
    styleUrl: './enlarge-slide-image.component.scss',
})
export class EnlargeSlideImageComponent {
    private readonly dialogRef = inject(DynamicDialogRef);
    data = inject<DynamicDialogConfig<DialogData>>(DynamicDialogConfig).data!;

    /**
     * Closes the image preview dialog. Bound to the Escape key as well, since PrimeNG's closeOnEscape does not
     * reliably dismiss this dialog, leaving the preview stuck open (see issue #13287).
     */
    @HostListener('document:keydown.escape')
    close(): void {
        this.dialogRef.close();
    }
}
