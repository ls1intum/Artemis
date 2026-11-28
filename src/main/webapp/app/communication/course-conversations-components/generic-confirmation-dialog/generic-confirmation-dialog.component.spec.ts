import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { GenericConfirmationDialogComponent } from 'app/communication/course-conversations-components/generic-confirmation-dialog/generic-confirmation-dialog.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { MockDirective, MockPipe } from 'ng-mocks';
import { initializeDialog } from 'test/helpers/dialog-test-helpers';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { TumAetUiDialogConfig, TumAetUiDialogRef } from '@tumaet/ui-angular/dialog';
import { Subject } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('GenericConfirmationDialogComponent', () => {
    let component: GenericConfirmationDialogComponent;
    let fixture: ComponentFixture<GenericConfirmationDialogComponent>;

    beforeEach(async () => {
        TestBed.configureTestingModule({
            imports: [GenericConfirmationDialogComponent, MockPipe(ArtemisTranslatePipe), MockDirective(TranslateDirective)],
            providers: [
                { provide: TumAetUiDialogRef, useValue: { close: vi.fn(), dismiss: vi.fn(), onClose: new Subject() } },
                { provide: TumAetUiDialogConfig, useValue: new TumAetUiDialogConfig({ data: {} }) },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        });
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(GenericConfirmationDialogComponent);
        component = fixture.componentInstance;
        const translationKeys = {
            titleKey: 'title',
            questionKey: 'question',
            descriptionKey: 'description',
            confirmButtonKey: 'confirm',
        };
        initializeDialog(component, fixture, { translationKeys, canBeUndone: true, isDangerousAction: false, translationParameters: {} });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should initialize when inputs are assigned directly before initialize() is called', () => {
        component.isInitialized = false;
        component.translationKeys = {
            titleKey: 'title',
            questionKey: 'question',
            descriptionKey: 'description',
            confirmButtonKey: 'confirm',
        };

        component.initialize();

        expect(component.isInitialized).toBe(true);
    });

    it('should close modal if confirm is selected', () => {
        const dialogRef = TestBed.inject(TumAetUiDialogRef);
        const closeSpy = vi.spyOn(dialogRef, 'close');
        const confirmButton = fixture.debugElement.nativeElement.querySelector('.confirm');
        confirmButton.click();
        expect(closeSpy).toHaveBeenCalledExactlyOnceWith(true);
    });

    it('should dismiss modal if cancel is selected', () => {
        const dialogRef = TestBed.inject(TumAetUiDialogRef);
        const dismissSpy = vi.spyOn(dialogRef, 'dismiss');
        const cancelButton = fixture.debugElement.nativeElement.querySelector('.cancel');
        cancelButton.click();
        expect(dismissSpy).toHaveBeenCalledOnce();
    });

    it('should dismiss modal if the close button is clicked', () => {
        const dialogRef = TestBed.inject(TumAetUiDialogRef);
        const dismissSpy = vi.spyOn(dialogRef, 'dismiss');
        fixture.debugElement.nativeElement.querySelector('[data-testid="dialog-close-button"]').click();
        expect(dismissSpy).toHaveBeenCalledOnce();
    });

    it('should only use the danger severity and warn about a missing undo for a dangerous action that cannot be undone', () => {
        expect(fixture.nativeElement.querySelector('.confirm').className).not.toContain('danger');
        expect(fixture.nativeElement.textContent).not.toContain('entity.action.notUndoable');

        const dangerousFixture = TestBed.createComponent(GenericConfirmationDialogComponent);
        initializeDialog(dangerousFixture.componentInstance, dangerousFixture, {
            translationKeys: component.translationKeys,
            canBeUndone: false,
            isDangerousAction: true,
            translationParameters: {},
        });

        expect(dangerousFixture.nativeElement.querySelector('.confirm').className).toContain('danger');
        expect(dangerousFixture.nativeElement.textContent).toContain('entity.action.notUndoable');
    });
});
