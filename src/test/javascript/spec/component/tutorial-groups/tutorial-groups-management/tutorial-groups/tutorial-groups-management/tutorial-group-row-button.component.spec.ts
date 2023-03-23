import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { TutorialGroupsService } from 'app/course/tutorial-groups/services/tutorial-groups.service';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { TutorialGroupRowButtonsComponent } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-groups/tutorial-groups-management/tutorial-group-row-buttons/tutorial-group-row-buttons.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { MockRouterLinkDirective } from '../../../../../helpers/mocks/directive/mock-router-link.directive';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { generateExampleTutorialGroup } from '../../../helpers/tutorialGroupExampleModels';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { Course } from 'app/entities/course.model';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { TutorialGroupSessionsManagementComponent } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-group-sessions/tutorial-group-sessions-management/tutorial-group-sessions-management.component';
import { MockRouter } from '../../../../../helpers/mocks/mock-router';
import { Router } from '@angular/router';
import { RegisteredStudentsComponent } from 'app/course/tutorial-groups/tutorial-groups-management/registered-students/registered-students.component';

describe('TutorialGroupRowButtonsComponent', () => {
    let fixture: ComponentFixture<TutorialGroupRowButtonsComponent>;
    let component: TutorialGroupRowButtonsComponent;
    const course = {
        id: 1,
    } as Course;
    let tutorialGroup: TutorialGroup;

    const router = new MockRouter();

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [
                TutorialGroupRowButtonsComponent,
                MockComponent(FaIconComponent),
                MockRouterLinkDirective,
                MockDirective(DeleteButtonDirective),
                MockPipe(ArtemisTranslatePipe),
            ],
            providers: [MockProvider(TutorialGroupsService), MockProvider(NgbModal), { provide: Router, useValue: router }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TutorialGroupRowButtonsComponent);
                component = fixture.componentInstance;
                tutorialGroup = generateExampleTutorialGroup({});
                setInputValues();
                fixture.detectChanges();
            });
    });
    const setInputValues = () => {
        component.course = course;
        component.tutorialGroup = tutorialGroup;
        component.isAtLeastInstructor = true;
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
            expect(navigateSpy).toHaveBeenCalledWith(expectedRoute);
            navigateSpy.mockReset();
        });
    };
});
