import { HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { of } from 'rxjs';

import { MockRouterLinkDirective } from '../../../../../helpers/mocks/directive/mock-router-link.directive';
import { MockNgbModalService } from '../../../../../helpers/mocks/service/mock-ngb-modal.service';
import { generateExampleTutorialGroup } from '../../../helpers/tutorialGroupExampleModels';
import { generateExampleTutorialGroupSession } from '../../../helpers/tutorialGroupSessionExampleModels';
import { TutorialGroupSessionService } from 'app/course/tutorial-groups/services/tutorial-group-session.service';
import { EditTutorialGroupSessionComponent } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-group-sessions/crud/edit-tutorial-group-session/edit-tutorial-group-session.component';
import { CancellationModalComponent } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-group-sessions/tutorial-group-sessions-management/cancellation-modal/cancellation-modal.component';
import { TutorialGroupSessionRowButtonsComponent } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-group-sessions/tutorial-group-sessions-management/tutorial-group-session-row-buttons/tutorial-group-session-row-buttons.component';
import { Course } from 'app/entities/course.model';
import { TutorialGroupSession } from 'app/entities/tutorial-group/tutorial-group-session.model';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

describe('TutorialGroupSessionRowButtonsComponent', () => {
    let fixture: ComponentFixture<TutorialGroupSessionRowButtonsComponent>;
    let component: TutorialGroupSessionRowButtonsComponent;
    let sessionService: TutorialGroupSessionService;
    const course = { id: 1 } as Course;
    let tutorialGroup: TutorialGroup;
    let tutorialGroupSession: TutorialGroupSession;
    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [
                TutorialGroupSessionRowButtonsComponent,
                MockPipe(ArtemisDatePipe),
                MockPipe(ArtemisTranslatePipe),
                MockComponent(FaIconComponent),
                MockRouterLinkDirective,
                MockDirective(DeleteButtonDirective),
            ],
            providers: [MockProvider(TutorialGroupSessionService), { provide: NgbModal, useClass: MockNgbModalService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TutorialGroupSessionRowButtonsComponent);
                component = fixture.componentInstance;
                sessionService = TestBed.inject(TutorialGroupSessionService);
                tutorialGroupSession = generateExampleTutorialGroupSession({});
                tutorialGroup = generateExampleTutorialGroup({});
                setInputValues();
                fixture.detectChanges();
            });
    });

    const setInputValues = () => {
        component.course = course;
        component.tutorialGroup = tutorialGroup;
        component.tutorialGroupSession = tutorialGroupSession;
    };

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        expect(component).toBeTruthy();
    });

    it('should open the edit dialog when the respective button is clicked', fakeAsync(() => {
        const modalService = TestBed.inject(NgbModal);
        const mockModalRef = {
            componentInstance: { tutorialGroupSession: undefined, course: undefined, tutorialGroup: undefined, initialize: () => {} },
            result: of(),
        };
        const modalOpenSpy = jest.spyOn(modalService, 'open').mockReturnValue(mockModalRef as unknown as NgbModalRef);
        const openDialogSpy = jest.spyOn(component, 'openEditSessionDialog');

        const editButton = fixture.debugElement.nativeElement.querySelector('#edit-' + tutorialGroupSession.id);
        editButton.click();

        fixture.whenStable().then(() => {
            expect(openDialogSpy).toHaveBeenCalledOnce();
            expect(modalOpenSpy).toHaveBeenCalledOnce();
            expect(modalOpenSpy).toHaveBeenCalledWith(EditTutorialGroupSessionComponent, { backdrop: 'static', scrollable: false, size: 'lg', animation: false });
            expect(mockModalRef.componentInstance.tutorialGroupSession).toEqual(tutorialGroupSession);
            expect(mockModalRef.componentInstance.course).toEqual(course);
            expect(mockModalRef.componentInstance.tutorialGroup).toEqual(tutorialGroup);
        });
    }));

    it('should open the cancellation / activation dialog when the respective button is clicked', fakeAsync(() => {
        const modalService = TestBed.inject(NgbModal);
        const mockModalRef = {
            componentInstance: { tutorialGroupSession: undefined, course: undefined, tutorialGroupId: undefined },
            result: { then: () => Promise.resolve() },
        };
        const modalOpenSpy = jest.spyOn(modalService, 'open').mockReturnValue(mockModalRef as unknown as NgbModalRef);
        const openDialogSpy = jest.spyOn(component, 'openCancellationDialog');
        const cancelButton = fixture.debugElement.nativeElement.querySelector('#cancel-activate-' + tutorialGroupSession.id);
        cancelButton.click();

        fixture.whenStable().then(() => {
            expect(openDialogSpy).toHaveBeenCalledOnce();
            expect(openDialogSpy).toHaveBeenCalledWith(tutorialGroupSession);
            expect(modalOpenSpy).toHaveBeenCalledOnce();
            expect(modalOpenSpy).toHaveBeenCalledWith(CancellationModalComponent, { animation: false, backdrop: 'static', scrollable: false, size: 'lg' });
            expect(mockModalRef.componentInstance.tutorialGroupSession).toEqual(tutorialGroupSession);
            expect(mockModalRef.componentInstance.course).toEqual(course);
            expect(mockModalRef.componentInstance.tutorialGroupId).toEqual(tutorialGroup.id);
        });
    }));

    it('should call delete and emit deleted event', () => {
        const deleteSpy = jest.spyOn(sessionService, 'delete').mockReturnValue(of(new HttpResponse<void>({})));
        const deleteEventSpy = jest.spyOn(component.tutorialGroupSessionDeleted, 'emit');
        component.deleteTutorialGroupSession();
        expect(deleteSpy).toHaveBeenCalledWith(course.id, tutorialGroup.id!, tutorialGroupSession.id);
        expect(deleteEventSpy).toHaveBeenCalledOnce();
    });
});
