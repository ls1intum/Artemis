import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { FormFooterComponent } from 'app/forms/form-footer/form-footer.component';
import { MockComponent } from 'ng-mocks';
import { ExerciseUpdateNotificationComponent } from 'app/exercises/shared/exercise-update-notification/exercise-update-notification.component';
import { ButtonComponent } from 'app/shared/components/button.component';
import { InputSignal, WritableSignal, signal } from '@angular/core';

describe('FormFooterComponent', () => {
    let fixture: ComponentFixture<FormFooterComponent>;
    let comp: FormFooterComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [MockComponent(ExerciseUpdateNotificationComponent), MockComponent(ButtonComponent)],
            providers: [],
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
        comp.isCreation = signal(true) as any as InputSignal<boolean>;
        comp.isImport = signal(false) as any as InputSignal<boolean>;
        expect(comp.saveTitle()).toBe('entity.action.generate');

        (comp.isImport as any as WritableSignal<boolean>).set(true);
        expect(comp.saveTitle()).toBe('entity.action.import');

        (comp.isImport as any as WritableSignal<boolean>).set(false);
        (comp.isCreation as any as WritableSignal<boolean>).set(false);

        expect(comp.saveTitle()).toBe('entity.action.save');
    });
});
