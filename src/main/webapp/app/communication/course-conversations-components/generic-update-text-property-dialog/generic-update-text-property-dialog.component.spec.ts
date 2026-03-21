import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { GenericUpdateTextPropertyDialogComponent } from 'app/communication/course-conversations-components/generic-update-text-property-dialog/generic-update-text-property-dialog.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockDirective, MockPipe } from 'ng-mocks';
import { initializeDialog } from 'test/helpers/dialog-test-helpers';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { Subject } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('GenericUpdateTextPropertyDialog', () => {
    setupTestBed({ zoneless: true });

    let component: GenericUpdateTextPropertyDialogComponent;
    let fixture: ComponentFixture<GenericUpdateTextPropertyDialogComponent>;

    beforeEach(async () => {
        TestBed.configureTestingModule({
            imports: [FormsModule, ReactiveFormsModule, GenericUpdateTextPropertyDialogComponent, MockPipe(ArtemisTranslatePipe), MockDirective(TranslateDirective)],
            providers: [
                { provide: DynamicDialogRef, useValue: { close: vi.fn(), destroy: vi.fn(), onClose: new Subject() } },
                { provide: DynamicDialogConfig, useValue: { data: {} } },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        });
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(GenericUpdateTextPropertyDialogComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    beforeEach(() => {
        vi.useFakeTimers();
    });

    afterEach(() => {
        vi.useRealTimers();
        vi.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should build the correct form, the initial value and set the correct validators', () => {
        setUpDialog();
        expect(component!.form!.get('name')!.value).toBe('loremipsum');
        expect(component!.form!.get('name')!.validator).toBeDefined();
    });

    it('should close modal if confirm is selected with the form value', () => {
        setUpDialog();
        const dialogRef = TestBed.inject(DynamicDialogRef);
        const closeSpy = vi.spyOn(dialogRef, 'close');
        const confirmButton = fixture.debugElement.nativeElement.querySelector('#submitButton');
        expect(component!.form!.valid).toBe(true);
        confirmButton.click();
        vi.advanceTimersByTime(0);
        expect(closeSpy).toHaveBeenCalledOnce();
        expect(closeSpy).toHaveBeenCalledWith('loremipsum');
    });

    it('should dismiss modal if cancel is selected', () => {
        setUpDialog();
        const dialogRef = TestBed.inject(DynamicDialogRef);
        const destroySpy = vi.spyOn(dialogRef, 'destroy');
        const dismissButton = fixture.debugElement.nativeElement.querySelector('.dismiss');
        dismissButton.click();
        vi.advanceTimersByTime(0);
        expect(destroySpy).toHaveBeenCalledOnce();
    });

    function setUpDialog() {
        const propertyName = 'name';
        const isRequired = true;
        const regexPattern = new RegExp('^[a-z0-9-]{1}[a-z0-9-]{0,20}$');
        const maxPropertyLength = 10;
        const initialValue = 'loremipsum';
        const translationKeys = {
            titleKey: 'title',
            labelKey: 'label',
            helpKey: 'help',
            maxLengthErrorKey: 'maxLengthError',
            requiredErrorKey: 'requiredError',
            regexErrorKey: 'regexError',
        };

        initializeDialog(component, fixture, {
            propertyName,
            maxPropertyLength,
            translationKeys,
            isRequired,
            initialValue,
            regexPattern,
        });
        fixture.changeDetectorRef.detectChanges();
    }
});
