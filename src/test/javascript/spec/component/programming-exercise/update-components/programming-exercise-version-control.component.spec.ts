import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProgrammingExerciseVersionControlComponent } from 'app/programming/manage/update/update-components/version-control/programming-exercise-version-control.component';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { ProgrammingExercise } from 'app/entities/programming/programming-exercise.model';
import { ComponentRef } from '@angular/core';
import { By } from '@angular/platform-browser';

describe('ProgrammingExerciseVersionControlComponent', () => {
    let fixture: ComponentFixture<ProgrammingExerciseVersionControlComponent>;
    let comp: ProgrammingExerciseVersionControlComponent;
    let componentRef: ComponentRef<ProgrammingExerciseVersionControlComponent>;
    let programmingExercise: ProgrammingExercise;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ProgrammingExerciseVersionControlComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(ProgrammingExerciseVersionControlComponent);
        comp = fixture.componentInstance;
        componentRef = fixture.componentRef;

        fixture.componentRef.setInput('isEditFieldDisplayedRecord', {
            allowBranching: true,
        });

        programmingExercise = new ProgrammingExercise(undefined, undefined);
        componentRef.setInput('programmingExercise', programmingExercise);
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(comp).toBeDefined();
        expect(comp.programmingExercise()).toEqual(programmingExercise);
        expect(comp.programmingExercise().allowBranching).toBeFalse();
    });

    it('should update allowBranching attribute when checking the option', () => {
        const checkbox = fixture.debugElement.query(By.css('#field_allowBranching'));
        checkbox.triggerEventHandler('change', { target: { checked: true } });
        fixture.detectChanges();
        expect(comp.programmingExercise().allowBranching).toBeTrue();
    });

    it('should update allowBranching attribute when unchecking the option', () => {
        comp.programmingExercise().allowBranching = true;
        const checkbox = fixture.debugElement.query(By.css('#field_allowBranching'));
        checkbox.triggerEventHandler('change', { target: { checked: false } });
        fixture.detectChanges();
        expect(comp.programmingExercise().allowBranching).toBeFalse();
    });
});
