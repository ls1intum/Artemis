import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { GenericUpdateTextPropertyDialogComponent } from 'app/communication/course-conversations-components/generic-update-text-property-dialog/generic-update-text-property-dialog.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { initializeDialog } from 'test/helpers/dialog-test-helpers';
import { TranslateDirective } from 'app/shared/language/translate.directive';

describe('GenericUpdateTextPropertyDialog', () => {
    setupTestBed({ zoneless: true });
    let component: GenericUpdateTextPropertyDialogComponent;
    let fixture: ComponentFixture<GenericUpdateTextPropertyDialogComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [GenericUpdateTextPropertyDialogComponent],
            providers: [MockProvider(NgbActiveModal)],
        })
            .overrideComponent(GenericUpdateTextPropertyDialogComponent, {
                remove: { imports: [TranslateDirective, ArtemisTranslatePipe] },
                add: { imports: [MockDirective(TranslateDirective), MockPipe(ArtemisTranslatePipe)] },
            })
            .compileComponents();
        fixture = TestBed.createComponent(GenericUpdateTextPropertyDialogComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    afterEach(() => vi.restoreAllMocks());

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
        const activeModal = TestBed.inject(NgbActiveModal);
        const closeSpy = vi.spyOn(activeModal, 'close');
        const confirmButton = fixture.debugElement.nativeElement.querySelector('#submitButton');
        expect(component!.form!.valid).toBe(true);
        confirmButton.click();
        expect(closeSpy).toHaveBeenCalledOnce();
        expect(closeSpy).toHaveBeenCalledWith('loremipsum');
    });

    it('should dismiss modal if cancel is selected', () => {
        setUpDialog();
        const activeModal = TestBed.inject(NgbActiveModal);
        const dismissSpy = vi.spyOn(activeModal, 'dismiss');
        const dismissButton = fixture.debugElement.nativeElement.querySelector('.dismiss');
        dismissButton.click();
        expect(dismissSpy).toHaveBeenCalledOnce();
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

        fixture.componentRef.setInput('isRequired', isRequired);
        fixture.componentRef.setInput('initialValue', initialValue);
        fixture.componentRef.setInput('regexPattern', regexPattern);

        initializeDialog(component, fixture, {
            propertyName,
            maxPropertyLength,
            translationKeys,
        });
        fixture.changeDetectorRef.detectChanges();
    }
});
