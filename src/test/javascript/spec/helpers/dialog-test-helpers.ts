import { AbstractDialogComponent } from 'app/communication/course-conversations-components/abstract-dialog.component';
import { ComponentFixture } from '@angular/core/testing';

type RequiredInputs = {
    [key: string]: any;
};

export function initializeDialog(component: AbstractDialogComponent, fixture: ComponentFixture<AbstractDialogComponent>, requiredInputs: RequiredInputs) {
    // Set inputs before calling initialize() so signal inputs have values
    Object.keys(requiredInputs).forEach((key) => {
        try {
            fixture.componentRef.setInput(key, requiredInputs[key]);
        } catch {
            // Not a signal input, assign directly as a regular property
            (component as any)[key] = requiredInputs[key];
        }
    });

    component.initialize();
    fixture.changeDetectorRef.detectChanges();
    expect(component.isInitialized).toBe(true);
}
