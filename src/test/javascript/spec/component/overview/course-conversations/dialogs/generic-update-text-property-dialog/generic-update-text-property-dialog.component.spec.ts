import { ComponentFixture, TestBed, fakeAsync, tick, waitForAsync } from '@angular/core/testing';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { GenericUpdateTextPropertyDialogComponent } from 'app/overview/course-conversations/dialogs/generic-update-text-property-dialog/generic-update-text-property-dialog.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe, MockProvider } from 'ng-mocks';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { initializeDialog } from '../dialog-test-helpers';

describe('GenericUpdateTextPropertyDialog', () => {
    let component: GenericUpdateTextPropertyDialogComponent;
    let fixture: ComponentFixture<GenericUpdateTextPropertyDialogComponent>;

    beforeEach(waitForAsync(() => {
        TestBed.configureTestingModule({
            imports: [FormsModule, ReactiveFormsModule],
            declarations: [GenericUpdateTextPropertyDialogComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [MockProvider(NgbActiveModal)],
        }).compileComponents();
    }));

    beforeEach(() => {
        fixture = TestBed.createComponent(GenericUpdateTextPropertyDialogComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should build the correct form, the initial value and set the correct validators', () => {
        setUpDialog();
        expect(component!.form!.get('name')!.value).toBe('loremipsum');
        expect(component!.form!.get('name')!.validator).toBeDefined();
    });

    it('should close modal if confirm is selected with the form value', fakeAsync(() => {
        setUpDialog();
        const activeModal = TestBed.inject(NgbActiveModal);
        const closeSpy = jest.spyOn(activeModal, 'close');
        const confirmButton = fixture.debugElement.nativeElement.querySelector('#submitButton');
        expect(component!.form!.valid).toBeTrue();
        confirmButton.click();
        tick();
        expect(closeSpy).toHaveBeenCalledOnce();
        expect(closeSpy).toHaveBeenCalledWith('loremipsum');
    }));

    it('should dismiss modal if cancel is selected', fakeAsync(() => {
        setUpDialog();
        const activeModal = TestBed.inject(NgbActiveModal);
        const dismissSpy = jest.spyOn(activeModal, 'dismiss');
        const dismissButton = fixture.debugElement.nativeElement.querySelector('.dismiss');
        dismissButton.click();
        tick();
        expect(dismissSpy).toHaveBeenCalledOnce();
    }));

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

        component.isRequired = isRequired;
        component.initialValue = initialValue;
        component.regexPattern = regexPattern;

        initializeDialog(component, fixture, {
            propertyName,
            maxPropertyLength,
            translationKeys,
        });
        fixture.detectChanges();
    }
});
