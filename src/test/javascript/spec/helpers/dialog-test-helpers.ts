import { AbstractDialogComponent } from 'app/communication/course-conversations-components/abstract-dialog.component';
import { ComponentFixture } from '@angular/core/testing';

type RequiredInputs = {
    [key: string]: any;
};

export function initializeDialog(component: AbstractDialogComponent, fixture: ComponentFixture<AbstractDialogComponent>, requiredInputs: RequiredInputs) {
    // Populate the DynamicDialogConfig.data with the provided inputs,
    // so that AbstractDialogComponent.initialize() can apply them correctly
    // (including setting writable signals via .set()).
    if (component.dialogConfig) {
        Object.keys(requiredInputs).forEach((key) => {
            component.dialogConfig!.data[key] = requiredInputs[key];
        });
    }

    component.initialize();
    fixture.changeDetectorRef.detectChanges();
    expect(component.isInitialized).toBe(true);
}
