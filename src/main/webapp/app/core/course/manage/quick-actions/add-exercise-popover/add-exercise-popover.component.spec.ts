import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';

import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { AddExercisePopoverComponent } from 'app/core/course/manage/quick-actions/add-exercise-popover/add-exercise-popover.component';
import { MockComponent } from 'ng-mocks';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ExerciseCreateButtonComponent } from 'app/exercise/exercise-create-buttons/exercise-create-button/exercise-create-button.component';
import { ExerciseImportButtonComponent } from 'app/exercise/exercise-create-buttons/exercise-import-button/exercise-import-button.component';

describe('AddExercisePopoverComponent', () => {
    let component: AddExercisePopoverComponent;
    let fixture: ComponentFixture<AddExercisePopoverComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [FaIconComponent, MockComponent(ExerciseCreateButtonComponent), MockComponent(ExerciseImportButtonComponent), AddExercisePopoverComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(AddExercisePopoverComponent);
        fixture.componentRef.setInput('course', { id: 1 });
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should show popover when showPopover is invoked', () => {
        const modalSpy = jest.spyOn(component.addExercisePopover()!, 'show');
        component.showPopover(new Event('click'));
        expect(modalSpy).toHaveBeenCalledOnce();
    });
    it('should hide popover when hidePopover is invoked', () => {
        const modalSpy = jest.spyOn(component.addExercisePopover()!, 'hide');
        component.hidePopover();
        expect(modalSpy).toHaveBeenCalledOnce();
    });
});
