import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ExerciseUpdateNotificationComponent } from 'app/exercises/shared/exercise-update-notification/exercise-update-notification.component';
import { FormsModule } from '@angular/forms';
import { MockModule, MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

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
});
