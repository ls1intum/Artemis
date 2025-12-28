import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { TutorialGroupSessionRowButtonsComponent } from 'app/tutorialgroup/manage/tutorial-group-sessions/tutorial-group-sessions-management/tutorial-group-session-row-buttons/tutorial-group-session-row-buttons.component';
import { TutorialGroupSessionService } from 'app/tutorialgroup/shared/service/tutorial-group-session.service';
import { TutorialGroupSession } from 'app/tutorialgroup/shared/entities/tutorial-group-session.model';
import { generateExampleTutorialGroupSession } from 'test/helpers/sample/tutorialgroup/tutorialGroupSessionExampleModels';
import { CancellationModalComponent } from 'app/tutorialgroup/manage/tutorial-group-sessions/tutorial-group-sessions-management/cancellation-modal/cancellation-modal.component';
import { Course } from 'app/core/course/shared/entities/course.model';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { generateExampleTutorialGroup } from 'test/helpers/sample/tutorialgroup/tutorialGroupExampleModels';
import { EditTutorialGroupSessionComponent } from 'app/tutorialgroup/manage/tutorial-group-sessions/crud/edit-tutorial-group-session/edit-tutorial-group-session.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockDialogService } from 'test/helpers/mocks/service/mock-dialog.service';
import { DialogService } from 'primeng/dynamicdialog';
import { TranslateService } from '@ngx-translate/core';
import { MockProvider } from 'ng-mocks';

describe('TutorialGroupSessionRowButtonsComponent', () => {
    let fixture: ComponentFixture<TutorialGroupSessionRowButtonsComponent>;
    let component: TutorialGroupSessionRowButtonsComponent;
    let sessionService: TutorialGroupSessionService;
    const course = { id: 1 } as Course;
    let tutorialGroup: TutorialGroup;
    let tutorialGroupSession: TutorialGroupSession;
    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: DialogService, useClass: MockDialogService },
                MockProvider(TutorialGroupSessionService),
            ],
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
        fixture.componentRef.setInput('course', course);
        fixture.componentRef.setInput('tutorialGroup', tutorialGroup);
        fixture.componentRef.setInput('tutorialGroupSession', tutorialGroupSession);
    };

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        expect(component).toBeTruthy();
    });

    it('should open the edit dialog when the respective button is clicked', fakeAsync(() => {
        const mockEditDialog = { open: jest.fn() } as unknown as EditTutorialGroupSessionComponent;
        jest.spyOn(component, 'editSessionDialog').mockReturnValue(mockEditDialog);
        const openDialogSpy = jest.spyOn(component, 'openEditSessionDialog');

        const editButton = fixture.debugElement.nativeElement.querySelector('#edit-' + tutorialGroupSession.id);
        editButton.click();

        fixture.whenStable().then(() => {
            expect(openDialogSpy).toHaveBeenCalledOnce();
            expect(mockEditDialog.open).toHaveBeenCalledOnce();
        });
    }));

    it('should open the cancellation / activation dialog when the respective button is clicked', fakeAsync(() => {
        const mockCancellationDialog = { open: jest.fn() } as unknown as CancellationModalComponent;
        jest.spyOn(component, 'cancellationDialog').mockReturnValue(mockCancellationDialog);
        const openDialogSpy = jest.spyOn(component, 'openCancellationDialog');
        const cancelButton = fixture.debugElement.nativeElement.querySelector('#cancel-activate-' + tutorialGroupSession.id);
        cancelButton.click();

        fixture.whenStable().then(() => {
            expect(openDialogSpy).toHaveBeenCalledOnce();
            expect(mockCancellationDialog.open).toHaveBeenCalledOnce();
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
