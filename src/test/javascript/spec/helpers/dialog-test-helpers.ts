import { AbstractDialogComponent } from 'app/communication/course-conversations-components/abstract-dialog.component';
import { ComponentFixture } from '@angular/core/testing';

type RequiredInputs = {
    [key: string]: any;
};

export function initializeDialog(component: AbstractDialogComponent, fixture: ComponentFixture<AbstractDialogComponent>, requiredInputs: RequiredInputs) {
    // Populate the TumAetUiDialogConfig.data with the provided inputs,
    // so that AbstractDialogComponent.initialize() can apply them correctly
    // (including setting writable signals via .set()).
    if (component.dialogConfig?.data) {
        const data = component.dialogConfig.data;
        Object.keys(requiredInputs).forEach((key) => {
            data[key] = requiredInputs[key];
        });
    }

    component.initialize();
    fixture.changeDetectorRef.detectChanges();
    expect(component.isInitialized).toBe(true);
}
