import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { TranslateService } from '@ngx-translate/core';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

import { ExerciseUpdateNotificationComponent } from 'app/exercise/exercise-update-notification/exercise-update-notification.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('ExerciseUpdateNotificationComponent', () => {
    setupTestBed({ zoneless: true });

    let component: ExerciseUpdateNotificationComponent;
    let fixture: ComponentFixture<ExerciseUpdateNotificationComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        });
        fixture = TestBed.createComponent(ExerciseUpdateNotificationComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('isCreation', false);
        fixture.componentRef.setInput('isImport', false);
        fixture.componentRef.setInput('notificationText', 'notificationText');
        fixture.detectChanges();
    });

    it('should emit event on inputChange', () => {
        const emitSpy = vi.spyOn(component.notificationTextChange, 'emit');

        component.onInputChanged();

        expect(emitSpy).toHaveBeenCalledExactlyOnceWith('notificationText');
    });

    it('should have the correct minlength, type, class, name, and id', () => {
        const inputElement = fixture.debugElement.query(By.css('#field_notification_text')).nativeElement;
        expect(inputElement.minLength).toBe(3);
        expect(inputElement.type).toBe('text');
        expect(inputElement.classList).toContain('form-control');
        expect(inputElement.classList).toContain('form-control-sm');
        expect(inputElement.name).toBe('notificationText');
        expect(inputElement.id).toBe('field_notification_text');
    });
});
