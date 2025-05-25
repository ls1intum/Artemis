import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CourseManagementSection, QuickActionsComponent } from './quick-actions.component';
import { Router } from '@angular/router';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { MockComponent } from 'ng-mocks';
import { UserManagementDropdownComponent } from 'app/core/course/manage/user-management-dropdown/user-management-dropdown.component';
import { AddExercisePopoverComponent } from 'app/core/course/manage/quick-actions/add-exercise-popover/add-exercise-popover.component';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClient } from '@angular/common/http';
import { By } from '@angular/platform-browser';

describe('QuickActionsComponent', () => {
    let component: QuickActionsComponent;
    let fixture: ComponentFixture<QuickActionsComponent>;
    let router: Router;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [MockComponent(UserManagementDropdownComponent), MockComponent(ButtonComponent), MockComponent(AddExercisePopoverComponent)],
            providers: [{ provide: Router, useClass: MockRouter }, { provide: TranslateService, useClass: MockTranslateService }, provideHttpClient()],
        }).compileComponents();

        fixture = TestBed.createComponent(QuickActionsComponent);
        fixture.componentRef.setInput('course', { id: 1, isAtLeastEditor: true });
        router = TestBed.inject(Router);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should open the add exercise modal', () => {
        const addExercisePopoverComponent = fixture.debugElement.query(By.directive(AddExercisePopoverComponent));
        const addExerciseModalSpy = jest.spyOn(addExercisePopoverComponent.componentInstance, 'showPopover');
        const addExerciseModalButton = fixture.debugElement.query(By.css('#open-add-exercise-popover'));
        addExerciseModalButton.nativeElement.click();
        expect(addExerciseModalSpy).toHaveBeenCalled();
    });

    it.each([
        { section: CourseManagementSection.EXAM, expectedLink: 'exams' },
        { section: CourseManagementSection.LECTURE, expectedLink: 'lectures' },
        { section: CourseManagementSection.FAQ, expectedLink: 'faqs' },
    ])('should link to correct section', ({ section, expectedLink }) => {
        fixture.componentRef.setInput('course', { id: 123 });
        const routerSpy = jest.spyOn(router, 'navigate');
        component.navigateToCourseManagementSection(section);
        expect(routerSpy).toHaveBeenCalledWith(['/course-management', 123, expectedLink, 'new']);
    });
});
