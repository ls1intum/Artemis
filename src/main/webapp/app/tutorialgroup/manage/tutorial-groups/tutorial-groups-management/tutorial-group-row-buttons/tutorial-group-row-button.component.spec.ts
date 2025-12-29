import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { MockModule, MockProvider } from 'ng-mocks';
import { TutorialGroupsService } from 'app/tutorialgroup/shared/service/tutorial-groups.service';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { TutorialGroupRowButtonsComponent } from 'app/tutorialgroup/manage/tutorial-groups/tutorial-groups-management/tutorial-group-row-buttons/tutorial-group-row-buttons.component';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { generateExampleTutorialGroup } from 'test/helpers/sample/tutorialgroup/tutorialGroupExampleModels';
import { Course } from 'app/core/course/shared/entities/course.model';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { TutorialGroupSessionsManagementComponent } from 'app/tutorialgroup/manage/tutorial-group-sessions/tutorial-group-sessions-management/tutorial-group-sessions-management.component';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { RegisteredStudentsComponent } from 'app/tutorialgroup/manage/registered-students/registered-students.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { DialogService } from 'primeng/dynamicdialog';
import { MockDialogService } from 'test/helpers/mocks/service/mock-dialog.service';

describe('TutorialGroupRowButtonsComponent', () => {
    let fixture: ComponentFixture<TutorialGroupRowButtonsComponent>;
    let component: TutorialGroupRowButtonsComponent;
    const course = {
        id: 1,
    } as Course;
    let tutorialGroup: TutorialGroup;

    let router: MockRouter;

    beforeEach(() => {
        router = new MockRouter();
        TestBed.configureTestingModule({
            imports: [MockModule(RouterModule)],
            providers: [
                MockProvider(TutorialGroupsService),
                MockProvider(NgbModal),
                { provide: Router, useValue: router },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
                { provide: DialogService, useClass: MockDialogService },
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

    it('should open the session management dialog when the respective button is clicked', fakeAsync(() => {
        const modalService = TestBed.inject(NgbModal);
        const mockModalRef = {
            componentInstance: { course: undefined, tutorialGroupId: undefined, initialize: () => {} },
            result: of(),
        };
        const modalOpenSpy = jest.spyOn(modalService, 'open').mockReturnValue(mockModalRef as unknown as NgbModalRef);
        const openDialogSpy = jest.spyOn(component, 'openSessionDialog');

        const button = fixture.debugElement.nativeElement.querySelector('#sessions-' + tutorialGroup.id);
        button.click();

        fixture.whenStable().then(() => {
            expect(openDialogSpy).toHaveBeenCalledOnce();
            expect(modalOpenSpy).toHaveBeenCalledOnce();
            expect(modalOpenSpy).toHaveBeenCalledWith(TutorialGroupSessionsManagementComponent, {
                backdrop: 'static',
                scrollable: false,
                animation: false,
                windowClass: 'session-management-dialog',
            });
            expect(mockModalRef.componentInstance.tutorialGroupId).toEqual(tutorialGroup.id);
            expect(mockModalRef.componentInstance.course).toEqual(course);
        });
    }));

    it('should open the registrations dialog when the respective button is clicked', fakeAsync(() => {
        const modalService = TestBed.inject(NgbModal);
        const mockModalRef = {
            componentInstance: { course: undefined, tutorialGroupId: undefined, initialize: () => {} },
            result: of(),
        };
        const modalOpenSpy = jest.spyOn(modalService, 'open').mockReturnValue(mockModalRef as unknown as NgbModalRef);
        const openDialogSpy = jest.spyOn(component, 'openRegistrationDialog');

        const button = fixture.debugElement.nativeElement.querySelector('#registrations-' + tutorialGroup.id);
        button.click();

        fixture.whenStable().then(() => {
            expect(openDialogSpy).toHaveBeenCalledOnce();
            expect(modalOpenSpy).toHaveBeenCalledOnce();
            expect(modalOpenSpy).toHaveBeenCalledWith(RegisteredStudentsComponent, { backdrop: 'static', scrollable: false, size: 'xl', animation: false });
            expect(mockModalRef.componentInstance.tutorialGroupId).toEqual(tutorialGroup.id);
            expect(mockModalRef.componentInstance.course).toEqual(course);
        });
    }));

    it('should navigate to edit', fakeAsync(() => {
        testButtonLeadsToRouting('edit-' + tutorialGroup.id, ['/course-management', course.id!, 'tutorial-groups', tutorialGroup.id!, 'edit']);
    }));

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should call delete and emit deleted event', () => {
        const tutorialGroupService = TestBed.inject(TutorialGroupsService);
        const deleteSpy = jest.spyOn(tutorialGroupService, 'delete').mockReturnValue(of(new HttpResponse<void>({})));
        const deleteEventSpy = jest.spyOn(component.tutorialGroupDeleted, 'emit');
        component.deleteTutorialGroup();
        expect(deleteSpy).toHaveBeenCalledWith(course.id!, tutorialGroup.id);
        expect(deleteEventSpy).toHaveBeenCalledOnce();
    });

    const testButtonLeadsToRouting = (buttonId: string, expectedRoute: (string | number)[]) => {
        const navigateSpy = jest.spyOn(router, 'navigateByUrl');

        const button = fixture.debugElement.nativeElement.querySelector('#' + buttonId);
        button.click();

        fixture.whenStable().then(() => {
            expect(navigateSpy).toHaveBeenCalledOnce();
            const expectedUrlTree = router.createUrlTree(expectedRoute);
            const actualUrlTree = navigateSpy.mock.calls[0][0];

            expect(actualUrlTree).toEqual(expectedUrlTree);

            navigateSpy.mockReset();
        });
    };
});
