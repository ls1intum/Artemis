import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';

import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { AddExercisePopoverComponent } from 'app/core/course/manage/quick-actions/add-exercise-popover/add-exercise-popover.component';
import { MockComponent } from 'ng-mocks';
import { ExerciseCreateButtonComponent } from 'app/exercise/exercise-create-buttons/exercise-create-button/exercise-create-button.component';
import { ExerciseImportButtonComponent } from 'app/exercise/exercise-create-buttons/exercise-import-button/exercise-import-button.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { DialogService } from 'primeng/dynamicdialog';
import { MockDialogService } from 'test/helpers/mocks/service/mock-dialog.service';

describe('AddExercisePopoverComponent', () => {
    setupTestBed({ zoneless: true });

    let component: AddExercisePopoverComponent;
    let fixture: ComponentFixture<AddExercisePopoverComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [MockComponent(ExerciseCreateButtonComponent), MockComponent(ExerciseImportButtonComponent), AddExercisePopoverComponent, FaIconComponent],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ProfileService, useClass: MockProfileService },
                { provide: DialogService, useClass: MockDialogService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(AddExercisePopoverComponent);
        fixture.componentRef.setInput('course', { id: 1 });
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should show popover when showPopover is invoked', () => {
        const modalSpy = vi.spyOn(component.addExercisePopover()!, 'show');
        component.showPopover(new Event('click'));
        expect(modalSpy).toHaveBeenCalledOnce();
    });

    it('should hide popover when hidePopover is invoked', () => {
        const modalSpy = vi.spyOn(component.addExercisePopover()!, 'hide');
        component.hidePopover();
        expect(modalSpy).toHaveBeenCalledOnce();
    });
});
