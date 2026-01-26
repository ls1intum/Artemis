import { expect, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

import { ExerciseUpdateNotificationComponent } from 'app/exercise/exercise-update-notification/exercise-update-notification.component';
import { FormsModule } from '@angular/forms';
import { MockModule, MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { By } from '@angular/platform-browser';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('ExerciseUpdateNotificationComponent', () => {
    setupTestBed({ zoneless: true });
    let component: ExerciseUpdateNotificationComponent;
    let fixture: ComponentFixture<ExerciseUpdateNotificationComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ExerciseUpdateNotificationComponent, MockPipe(ArtemisTranslatePipe), MockModule(FormsModule)],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        });
        fixture = TestBed.createComponent(ExerciseUpdateNotificationComponent);
        component = fixture.componentInstance;
        component.isCreation = false;
        component.isImport = false;
        component.notificationText = 'notificationText';
        fixture.detectChanges();
    });

    it('should emit event on inputChange', () => {
        const emitSpy = vi.spyOn(component.notificationTextChange, 'emit');
        component.onInputChanged();
        expect(emitSpy).toHaveBeenCalledOnce();
        expect(emitSpy).toHaveBeenCalledWith(component.notificationText);
    });

    it('should have the correct minlength, type, class, name, and id', () => {
        const inputElement = fixture.debugElement.query(By.css('#field_notification_text')).nativeElement;
        expect(inputElement.minLength).toBe(3);
        expect(inputElement.type).toBe('text');
        expect(inputElement.className).toContain('form-control form-control-sm');
        expect(inputElement.name).toBe('notificationText');
        expect(inputElement.id).toBe('field_notification_text');
    });
});
