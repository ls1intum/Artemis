import { AbstractDialogComponent } from 'app/communication/course-conversations-components/abstract-dialog.component';
import { ComponentFixture } from '@angular/core/testing';

type RequiredInputs = {
    [key: string]: any;
};

export function initializeDialog(component: AbstractDialogComponent, fixture: ComponentFixture<AbstractDialogComponent>, requiredInputs: RequiredInputs) {
    component.initialize();
    fixture.changeDetectorRef.detectChanges();
    expect(component.isInitialized).toBeFalse();

    // expect console.err not to be called
    // loop over required inputs and set on component
    Object.keys(requiredInputs).forEach((key) => {
        component[key as keyof AbstractDialogComponent] = requiredInputs[key];
    });

    component.initialize();
    fixture.changeDetectorRef.detectChanges();
    expect(component.isInitialized).toBeTrue();
}
