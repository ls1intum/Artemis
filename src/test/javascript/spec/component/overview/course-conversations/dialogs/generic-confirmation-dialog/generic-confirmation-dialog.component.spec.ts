import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { GenericConfirmationDialogComponent } from 'app/overview/course-conversations/dialogs/generic-confirmation-dialog/generic-confirmation-dialog.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe, MockProvider } from 'ng-mocks';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { initializeDialog } from '../dialog-test-helpers';

describe('GenericConfirmationDialogComponent', () => {
    let component: GenericConfirmationDialogComponent;
    let fixture: ComponentFixture<GenericConfirmationDialogComponent>;

    beforeEach(waitForAsync(() => {
        TestBed.configureTestingModule({
            declarations: [GenericConfirmationDialogComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [MockProvider(NgbActiveModal)],
        }).compileComponents();
    }));

    beforeEach(() => {
        fixture = TestBed.createComponent(GenericConfirmationDialogComponent);
        component = fixture.componentInstance;
        const translationKeys = {
            titleKey: 'title',
            questionKey: 'question',
            descriptionKey: 'description',
            confirmButtonKey: 'confirm',
        };
        component.canBeUndone = true;
        component.isDangerousAction = false;
        component.translationParameters = {};
        fixture.detectChanges();
        initializeDialog(component, fixture, { translationKeys });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should close modal if confirm is selected', () => {
        const activeModal = TestBed.inject(NgbActiveModal);
        const closeSpy = jest.spyOn(activeModal, 'close');
        const confirmButton = fixture.debugElement.nativeElement.querySelector('.confirm');
        confirmButton.click();
        expect(closeSpy).toHaveBeenCalled();
    });

    it('should dismiss modal if cancel is selected', () => {
        const activeModal = TestBed.inject(NgbActiveModal);
        const dismissSpy = jest.spyOn(activeModal, 'dismiss');
        const cancelButton = fixture.debugElement.nativeElement.querySelector('.cancel');
        cancelButton.click();
        expect(dismissSpy).toHaveBeenCalled();
    });
});
