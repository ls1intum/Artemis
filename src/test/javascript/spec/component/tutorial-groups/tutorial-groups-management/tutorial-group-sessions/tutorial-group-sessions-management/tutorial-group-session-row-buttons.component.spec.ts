import { ComponentFixture, fakeAsync, TestBed } from '@angular/core/testing';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { MockRouterLinkDirective } from '../../../../../helpers/mocks/directive/mock-router-link.directive';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { MockRouter } from '../../../../../helpers/mocks/mock-router';
import { Router } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { TutorialGroupSessionRowButtonsComponent } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-group-sessions/tutorial-group-sessions-management/tutorial-group-session-row-buttons/tutorial-group-session-row-buttons.component';
import { TutorialGroupSessionService } from 'app/course/tutorial-groups/services/tutorial-group-session.service';
import { TutorialGroupSession } from 'app/entities/tutorial-group/tutorial-group-session.model';
import { generateExampleTutorialGroupSession } from '../../../helpers/tutorialGroupSessionExampleModels';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { NgbModal, NgbModalRef, NgbPopover } from '@ng-bootstrap/ng-bootstrap';
import { MockNgbModalService } from '../../../../../helpers/mocks/service/mock-ngb-modal.service';
import { CancellationModalComponent } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-group-sessions/tutorial-group-sessions-management/cancellation-modal/cancellation-modal.component';
import { Course } from 'app/entities/course.model';

describe('TutorialGroupSessionRowButtonsComponent', () => {
    let fixture: ComponentFixture<TutorialGroupSessionRowButtonsComponent>;
    let component: TutorialGroupSessionRowButtonsComponent;
    let sessionService: TutorialGroupSessionService;
    const course = { id: 1 } as Course;
    const tutorialGroupId = 1;
    let tutorialGroupSession: TutorialGroupSession;

    const router = new MockRouter();

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [
                TutorialGroupSessionRowButtonsComponent,
                MockPipe(ArtemisDatePipe),
                MockPipe(ArtemisTranslatePipe),
                MockComponent(FaIconComponent),
                MockRouterLinkDirective,
                MockDirective(DeleteButtonDirective),
                MockDirective(NgbPopover),
            ],
            providers: [MockProvider(TutorialGroupSessionService), { provide: Router, useValue: router }, { provide: NgbModal, useClass: MockNgbModalService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TutorialGroupSessionRowButtonsComponent);
                component = fixture.componentInstance;
                sessionService = TestBed.inject(TutorialGroupSessionService);
                tutorialGroupSession = generateExampleTutorialGroupSession({});
                setInputValues();
            });
    });

    const setInputValues = () => {
        component.course = course;
        component.tutorialGroupId = tutorialGroupId;
        component.tutorialGroupSession = tutorialGroupSession;
    };

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).toBeTruthy();
    });

    it('should navigate to edit page when edit button is clicked', fakeAsync(() => {
        fixture.detectChanges();
        const navigateSpy = jest.spyOn(router, 'navigateByUrl');

        const editButton = fixture.debugElement.nativeElement.querySelector('#edit-' + tutorialGroupSession.id);
        editButton.click();

        fixture.whenStable().then(() => {
            expect(navigateSpy).toHaveBeenCalledOnce();
            expect(navigateSpy).toHaveBeenCalledWith(['/course-management', course.id, 'tutorial-groups', tutorialGroupId, 'sessions', tutorialGroupSession.id, 'edit']);
        });
    }));

    it('should open the cancellation / activation dialog when the respective button is clicked', fakeAsync(() => {
        const modalService = TestBed.inject(NgbModal);
        const mockModalRef = {
            componentInstance: { tutorialGroupSession: undefined, course: undefined, tutorialGroupId: undefined },
            result: { then: () => undefined },
        };
        const modalOpenSpy = jest.spyOn(modalService, 'open').mockReturnValue(mockModalRef as unknown as NgbModalRef);

        fixture.detectChanges();
        const openDialogSpy = jest.spyOn(component, 'openCancellationModal');

        const cancelButton = fixture.debugElement.nativeElement.querySelector('#cancel-activate-' + tutorialGroupSession.id);
        cancelButton.click();

        fixture.whenStable().then(() => {
            expect(openDialogSpy).toHaveBeenCalledOnce();
            expect(openDialogSpy).toHaveBeenCalledWith(tutorialGroupSession);
            expect(modalOpenSpy).toHaveBeenCalledOnce();
            expect(modalOpenSpy).toHaveBeenCalledWith(CancellationModalComponent);
            expect(mockModalRef.componentInstance.tutorialGroupSession).toEqual(tutorialGroupSession);
            expect(mockModalRef.componentInstance.course).toEqual(course);
            expect(mockModalRef.componentInstance.tutorialGroupId).toEqual(tutorialGroupId);
        });
    }));

    it('should call delete and emit deleted event', () => {
        const deleteSpy = jest.spyOn(sessionService, 'delete').mockReturnValue(of(new HttpResponse<void>({})));
        const deleteEventSpy = jest.spyOn(component.tutorialGroupSessionDeleted, 'emit');

        fixture.detectChanges();
        component.deleteTutorialGroupSession();
        expect(deleteSpy).toHaveBeenCalledWith(course.id, tutorialGroupId, tutorialGroupSession.id);
        expect(deleteEventSpy).toHaveBeenCalledOnce();
    });
});
