import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { GenericConfirmationDialogComponent } from 'app/communication/course-conversations-components/generic-confirmation-dialog/generic-confirmation-dialog.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockDirective, MockPipe } from 'ng-mocks';
import { initializeDialog } from 'test/helpers/dialog-test-helpers';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { Subject } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('GenericConfirmationDialogComponent', () => {
    setupTestBed({ zoneless: true });

    let component: GenericConfirmationDialogComponent;
    let fixture: ComponentFixture<GenericConfirmationDialogComponent>;

    beforeEach(async () => {
        TestBed.configureTestingModule({
            imports: [GenericConfirmationDialogComponent, MockPipe(ArtemisTranslatePipe), MockDirective(TranslateDirective)],
            providers: [
                { provide: DynamicDialogRef, useValue: { close: vi.fn(), onClose: new Subject() } },
                { provide: DynamicDialogConfig, useValue: { data: {} } },
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
        fixture.changeDetectorRef.detectChanges();
        initializeDialog(component, fixture, { translationKeys, canBeUndone: true, isDangerousAction: false, translationParameters: {} });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should close modal if confirm is selected', () => {
        const dialogRef = TestBed.inject(DynamicDialogRef);
        const closeSpy = vi.spyOn(dialogRef, 'close');
        const confirmButton = fixture.debugElement.nativeElement.querySelector('.confirm');
        confirmButton.click();
        expect(closeSpy).toHaveBeenCalled();
    });

    it('should dismiss modal if cancel is selected', () => {
        const dialogRef = TestBed.inject(DynamicDialogRef);
        const closeSpy = vi.spyOn(dialogRef, 'close');
        const cancelButton = fixture.debugElement.nativeElement.querySelector('.cancel');
        cancelButton.click();
        expect(closeSpy).toHaveBeenCalled();
    });
});
