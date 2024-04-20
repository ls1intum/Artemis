import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ExerciseUpdateNotificationComponent } from 'app/exercises/shared/exercise-update-notification/exercise-update-notification.component';
import { FormsModule } from '@angular/forms';
import { MockModule, MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { By } from '@angular/platform-browser';

describe('ExerciseUpdateNotificationComponent', () => {
    let component: ExerciseUpdateNotificationComponent;
    let fixture: ComponentFixture<ExerciseUpdateNotificationComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [MockModule(FormsModule)],
            declarations: [ExerciseUpdateNotificationComponent, MockPipe(ArtemisTranslatePipe)],
        });
        fixture = TestBed.createComponent(ExerciseUpdateNotificationComponent);
        component = fixture.componentInstance;
        component.isCreation = false;
        component.isImport = false;
        component.notificationText = 'notificationText';
        fixture.detectChanges();
    });

    it('should emit event on inputChange', () => {
        const emitSpy = jest.spyOn(component.notificationTextChange, 'emit');
        component.onInputChanged();
        expect(emitSpy).toHaveBeenCalledExactlyOnceWith(component.notificationText);
    });

    it('should have the correct minlength, type, class, name, and id', () => {
        const inputElement = fixture.debugElement.query(By.css('#field_notification_text')).nativeElement;
        expect(inputElement.minLength).toBe(3);
        expect(inputElement.type).toBe('text');
        expect(inputElement.className).toBe('form-control form-control-sm');
        expect(inputElement.name).toBe('notificationText');
        expect(inputElement.id).toBe('field_notification_text');
    });
});
