import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { MockProvider } from 'ng-mocks';
import { TutorialGroupsService } from 'app/tutorialgroup/shared/service/tutorial-groups.service';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { TutorialGroupRowButtonsComponent } from 'app/tutorialgroup/manage/tutorial-groups-management/tutorial-group-row-buttons/tutorial-group-row-buttons.component';
import { of } from 'rxjs';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { generateExampleTutorialGroup } from 'test/helpers/sample/tutorialgroup/tutorialGroupExampleModels';
import { Course } from 'app/core/course/shared/entities/course.model';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { ActivatedRoute, Router } from '@angular/router';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { DialogService } from 'primeng/dynamicdialog';
import { MockDialogService } from 'test/helpers/mocks/service/mock-dialog.service';
import { TutorialGroupSessionsManagementComponent } from 'app/tutorialgroup/manage/tutorial-group-sessions/tutorial-group-sessions-management/tutorial-group-sessions-management.component';
import { RegisteredStudentsComponent } from 'app/tutorialgroup/manage/registered-students/registered-students.component';
import { OwlNativeDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import '@angular/localize/init';

describe('TutorialGroupRowButtonsComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<TutorialGroupRowButtonsComponent>;
    let component: TutorialGroupRowButtonsComponent;
    const course = {
        id: 1,
    } as Course;
    let tutorialGroup: TutorialGroup;

    let router: MockRouter;

    beforeEach(async () => {
        router = new MockRouter();
        await TestBed.configureTestingModule({
            imports: [TutorialGroupRowButtonsComponent, OwlNativeDateTimeModule],
            providers: [
                MockProvider(TutorialGroupsService),
                { provide: Router, useValue: router },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
                { provide: DialogService, useClass: MockDialogService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(TutorialGroupRowButtonsComponent);
        component = fixture.componentInstance;
        tutorialGroup = generateExampleTutorialGroup({});
        setInputValues();
        fixture.detectChanges();
    });
    const setInputValues = () => {
        fixture.componentRef.setInput('tutorialGroup', tutorialGroup);
        fixture.componentRef.setInput('course', course);
        fixture.componentRef.setInput('isAtLeastInstructor', true);
    };

    it('should open the session management dialog when the respective button is clicked', async () => {
        const mockSessionDialog = { open: vi.fn(), initialize: vi.fn() } as unknown as TutorialGroupSessionsManagementComponent;
        vi.spyOn(component, 'sessionManagementDialog').mockReturnValue(mockSessionDialog);
        const openDialogSpy = vi.spyOn(component, 'openSessionDialog');

        const button = fixture.debugElement.nativeElement.querySelector('#sessions-' + tutorialGroup.id);
        button.click();

        await fixture.whenStable();
        expect(openDialogSpy).toHaveBeenCalledOnce();
        expect(mockSessionDialog.open).toHaveBeenCalledOnce();
    });

    it('should open the registrations dialog when the respective button is clicked', async () => {
        const mockRegistrationDialog = { open: vi.fn(), initialize: vi.fn() } as unknown as RegisteredStudentsComponent;
        vi.spyOn(component, 'registeredStudentsDialog').mockReturnValue(mockRegistrationDialog);
        const openDialogSpy = vi.spyOn(component, 'openRegistrationDialog');

        const button = fixture.debugElement.nativeElement.querySelector('#registrations-' + tutorialGroup.id);
        button.click();

        await fixture.whenStable();
        expect(openDialogSpy).toHaveBeenCalledOnce();
        expect(mockRegistrationDialog.open).toHaveBeenCalledOnce();
    });

    it('should navigate to edit', async () => {
        await testButtonLeadsToRouting('edit-' + tutorialGroup.id, ['/course-management', course.id!, 'tutorial-groups', tutorialGroup.id!, 'edit']);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should call delete and emit deleted event', () => {
        const tutorialGroupService = TestBed.inject(TutorialGroupsService);
        const deleteSpy = vi.spyOn(tutorialGroupService, 'delete').mockReturnValue(of(new HttpResponse<void>({})));
        const deleteEventSpy = vi.spyOn(component.tutorialGroupDeleted, 'emit');
        component.deleteTutorialGroup();
        expect(deleteSpy).toHaveBeenCalledWith(course.id!, tutorialGroup.id);
        expect(deleteEventSpy).toHaveBeenCalledOnce();
    });

    const testButtonLeadsToRouting = async (buttonId: string, expectedRoute: (string | number)[]) => {
        const navigateSpy = vi.spyOn(router, 'navigateByUrl');

        const button = fixture.debugElement.nativeElement.querySelector('#' + buttonId);
        button.click();

        await fixture.whenStable();
        expect(navigateSpy).toHaveBeenCalledOnce();
        const expectedUrlTree = router.createUrlTree(expectedRoute);
        const actualUrlTree = navigateSpy.mock.calls[0][0];

        expect(actualUrlTree).toEqual(expectedUrlTree);

        navigateSpy.mockReset();
    };
});
