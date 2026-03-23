import { AbstractDialogComponent } from 'app/communication/course-conversations-components/abstract-dialog.component';
import { ComponentFixture } from '@angular/core/testing';

type RequiredInputs = {
    [key: string]: any;
};

export function initializeDialog(component: AbstractDialogComponent, fixture: ComponentFixture<AbstractDialogComponent>, requiredInputs: RequiredInputs) {
    component.initialize();
    fixture.changeDetectorRef.detectChanges();
    expect(component.isInitialized).toBe(false);

    Object.keys(requiredInputs).forEach((key) => {
        fixture.componentRef.setInput(key, requiredInputs[key]);
    });

    component.initialize();
    fixture.changeDetectorRef.detectChanges();
    expect(component.isInitialized).toBe(true);
}
