import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent } from 'ng-mocks';
import { ExerciseUpdateNotificationComponent } from 'app/exercise/exercise-update-notification/exercise-update-notification.component';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { By } from '@angular/platform-browser';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClient } from '@angular/common/http';
import { FormFooterComponent } from 'app/shared/form/form-footer/form-footer.component';
import { ValidationReason } from 'app/exercise/shared/entities/exercise/exercise.model';

describe('FormFooterComponent', () => {
    let fixture: ComponentFixture<FormFooterComponent>;
    let comp: FormFooterComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [MockComponent(ExerciseUpdateNotificationComponent), MockComponent(ButtonComponent)],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }, provideHttpClient()],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(FormFooterComponent);
                comp = fixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('update title depending on input signals', () => {
        fixture.componentRef.setInput('isCreation', true);
        fixture.componentRef.setInput('isImport', false);
        expect(comp.saveTitle()).toBe('entity.action.generate');

        fixture.componentRef.setInput('isImport', true);
        expect(comp.saveTitle()).toBe('entity.action.import');

        fixture.componentRef.setInput('isImport', false);
        fixture.componentRef.setInput('isCreation', false);

        expect(comp.saveTitle()).toBe('entity.action.save');
    });

    it('should display saving badge when isSaving is true', () => {
        fixture.componentRef.setInput('isSaving', true);
        fixture.detectChanges();
        const savingBadge = fixture.debugElement.query(By.css('.badge.bg-secondary'));
        expect(savingBadge).toBeTruthy();
    });

    it('should not display the exercise update notification when in creation or import mode', () => {
        fixture.componentRef.setInput('isCreation', true);
        fixture.componentRef.setInput('isImport', false);
        fixture.detectChanges();
        const notificationComponent = fixture.debugElement.query(By.css('jhi-exercise-update-notification'));
        expect(notificationComponent).toBeNull();
    });

    it('should display invalid input badge when there are invalid reasons', () => {
        fixture.componentRef.setInput('invalidReasons', [{ translateKey: 'test.key', translateValues: 'test.value' }]);
        fixture.detectChanges();
        const invalidBadge = fixture.debugElement.query(By.css('.badge.bg-danger'));
        expect(invalidBadge).toBeTruthy();
    });

    it('should enable save button when form is valid', () => {
        fixture.componentRef.setInput('invalidReasons', []);
        fixture.componentRef.setInput('isDisabled', false);
        fixture.detectChanges();
        const saveButton = fixture.debugElement.query(By.css('#save-entity')).componentInstance;
        expect(saveButton.disabled()).toBeFalse();
    });
});
