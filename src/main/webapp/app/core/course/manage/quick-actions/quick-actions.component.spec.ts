import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CourseManagementSection, QuickActionsComponent } from './quick-actions.component';
import { Router } from '@angular/router';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { MockComponent } from 'ng-mocks';
import { UserManagementDropdownComponent } from 'app/core/course/manage/user-management-dropdown/user-management-dropdown.component';
import { AddExerciseModalComponent } from 'app/core/course/manage/quick-actions/add-exercise-modal/add-exercise-modal.component';
import { ButtonComponent } from 'app/shared/components/button/button.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { MockFeatureToggleService } from 'test/helpers/mocks/service/mock-feature-toggle.service';

describe('QuickActionsComponent', () => {
    let component: QuickActionsComponent;
    let fixture: ComponentFixture<QuickActionsComponent>;
    let router: Router;
    let modalService: NgbModal;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [MockComponent(UserManagementDropdownComponent), MockComponent(ButtonComponent), MockComponent(AddExerciseModalComponent), QuickActionsComponent],
            providers: [
                { provide: Router, useClass: MockRouter },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: FeatureToggleService, useClass: MockFeatureToggleService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(QuickActionsComponent);
        router = TestBed.inject(Router);
        modalService = TestBed.inject(NgbModal);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should handle gracefully when no course id is provided', () => {
        fixture.componentRef.setInput('course', {});
        const routerSpy = jest.spyOn(router, 'navigate');
        component.navigateToCourseManagementSection(CourseManagementSection.EXAM);
        expect(routerSpy).not.toHaveBeenCalled();
    });
    it('should open the add exercise modal', () => {
        const modalServiceSpy = jest.spyOn(modalService, 'open');
        component.openAddExerciseModal();
        expect(modalServiceSpy).toHaveBeenCalledWith(AddExerciseModalComponent, { size: 'md' });
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
