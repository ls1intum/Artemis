import { AbstractDialogComponent } from 'app/overview/course-conversations/dialogs/abstract-dialog.component';
import { ComponentFixture } from '@angular/core/testing';

export function initializeDialog(component: AbstractDialogComponent, fixture: ComponentFixture<AbstractDialogComponent>, requiredInputs: object) {
    // expect console.err to be called
    const consoleErrorSpy = jest.spyOn(console, 'error').mockImplementation(() => {});
    component.initialize();
    fixture.detectChanges();
    expect(consoleErrorSpy).toHaveBeenCalled();
    expect(component.isInitialized).toBeFalse();
    consoleErrorSpy.mockRestore();

    // expect console.err not to be called
    // loop over required inputs and set on component
    Object.keys(requiredInputs).forEach((key) => {
        component[key] = requiredInputs[key];
    });

    component.initialize();
    fixture.detectChanges();
    expect(component.isInitialized).toBeTrue();
    expect(consoleErrorSpy).not.toHaveBeenCalled();
}
