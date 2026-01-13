import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { OneToOneChatCreateDialogComponent } from 'app/communication/course-conversations-components/one-to-one-chat-create-dialog/one-to-one-chat-create-dialog.component';
import { Course } from 'app/core/course/shared/entities/course.model';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { CourseUsersSelectorComponent } from 'app/communication/course-users-selector/course-users-selector.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { initializeDialog } from 'test/helpers/dialog-test-helpers';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';

describe('OneToOneChatCreateDialogComponent', () => {
    let component: OneToOneChatCreateDialogComponent;
    let fixture: ComponentFixture<OneToOneChatCreateDialogComponent>;
    const course = { id: 1 } as Course;

    beforeEach(waitForAsync(() => {
        TestBed.configureTestingModule({
            imports: [FormsModule, ReactiveFormsModule],
            declarations: [OneToOneChatCreateDialogComponent, MockComponent(CourseUsersSelectorComponent), MockPipe(ArtemisTranslatePipe), MockDirective(TranslateDirective)],
            providers: [MockProvider(NgbActiveModal)],
        }).compileComponents();
    }));

    beforeEach(() => {
        fixture = TestBed.createComponent(OneToOneChatCreateDialogComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
        initializeDialog(component, fixture, {
            course,
        });
    });

    it('should create', () => {
        expect(component).toBeTruthy();
        expect(component.isInitialized).toBeTrue();
    });

    it('should dismiss modal if cancel is selected', () => {
        const activeModal = TestBed.inject(NgbActiveModal);
        const dismissSpy = jest.spyOn(activeModal, 'dismiss');
        const dismissButton = fixture.debugElement.nativeElement.querySelector('.dismiss');
        dismissButton.click();
        expect(dismissSpy).toHaveBeenCalledOnce();
    });

    it('should close the dialog with the selected user once one is selected', () => {
        const selectedUser = { id: 1 };
        const activeModal = TestBed.inject(NgbActiveModal);
        const closeSpy = jest.spyOn(activeModal, 'close');
        component.onSelectedUsersChange([selectedUser]);
        fixture.changeDetectorRef.detectChanges();
        expect(closeSpy).toHaveBeenCalledOnce();
        expect(closeSpy).toHaveBeenCalledWith(selectedUser);
    });
});
