import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProgrammingExerciseVersionControlComponent } from 'app/programming/manage/update/update-components/version-control/programming-exercise-version-control.component';
import { TranslateService } from '@ngx-translate/core';
import { ComponentRef } from '@angular/core';
import { By } from '@angular/platform-browser';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

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
        expect(comp.programmingExercise().buildConfig!.allowBranching).toBeFalse();
    });

    it('should update allowBranching attribute when checking the option', () => {
        const checkbox = fixture.debugElement.query(By.css('#field_allowBranching'));
        checkbox.triggerEventHandler('change', { target: { checked: true } });
        fixture.detectChanges();
        expect(comp.programmingExercise().buildConfig!.allowBranching).toBeTrue();
    });

    it('should update allowBranching attribute when unchecking the option', () => {
        comp.programmingExercise().buildConfig!.allowBranching = true;
        const checkbox = fixture.debugElement.query(By.css('#field_allowBranching'));
        checkbox.triggerEventHandler('change', { target: { checked: false } });
        fixture.detectChanges();
        expect(comp.programmingExercise().buildConfig!.allowBranching).toBeFalse();
    });

    it('should show regex input field when checking the allowBranching option', () => {
        comp.programmingExercise().buildConfig!.allowBranching = true;
        fixture.detectChanges();
        const textInput = fixture.debugElement.query(By.css('#field_branchRegex'));
        expect(textInput).not.toBeNull();
    });

    it('should hide regex input field when unchecking the allowBranching option', () => {
        comp.programmingExercise().buildConfig!.allowBranching = false;
        fixture.detectChanges();
        const textInput = fixture.debugElement.query(By.css('#field_branchRegex'));
        expect(textInput).toBeNull();
    });

    it('should not render branching configuration when edit field flag is disabled', () => {
        componentRef.setInput('isEditFieldDisplayedRecord', { allowBranching: false });
        fixture.detectChanges();
        expect(fixture.debugElement.query(By.css('#field_allowBranching'))).toBeNull();
        expect(fixture.debugElement.query(By.css('#field_branchRegex'))).toBeNull();
    });
});
