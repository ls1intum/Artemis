import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockPipe, MockProvider } from 'ng-mocks';
import { AlertService } from 'app/core/util/alert.service';
import { of } from 'rxjs';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { HttpResponse } from '@angular/common/http';
import { By } from '@angular/platform-browser';
import { LoadingIndicatorContainerStubComponent } from '../../../../../helpers/stubs/loading-indicator-container-stub.component';
import { EditTutorialGroupSessionComponent } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-group-sessions/crud/edit-tutorial-group-session/edit-tutorial-group-session.component';
import { TutorialGroupSessionFormStubComponent } from '../../../stubs/tutorial-group-session-form-stub.component';
import { TutorialGroupSessionService } from 'app/course/tutorial-groups/services/tutorial-group-session.service';
import { TutorialGroupSession } from 'app/entities/tutorial-group/tutorial-group-session.model';
import {
    formDataToTutorialGroupSessionDTO,
    generateExampleTutorialGroupSession,
    tutorialGroupSessionToTutorialGroupSessionFormData,
} from '../../../helpers/tutorialGroupSessionExampleModels';
import { Course } from 'app/entities/course.model';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { generateExampleTutorialGroup } from '../../../helpers/tutorialGroupExampleModels';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';

describe('EditTutorialGroupSessionComponent', () => {
    let fixture: ComponentFixture<EditTutorialGroupSessionComponent>;
    let component: EditTutorialGroupSessionComponent;
    let sessionService: TutorialGroupSessionService;
    let exampleSession: TutorialGroupSession;
    let exampleTutorialGroup: TutorialGroup;
    let activeModal: NgbActiveModal;

    const timeZone = 'Europe/Berlin';
    const course = {
        id: 1,
        timeZone,
    } as Course;

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [EditTutorialGroupSessionComponent, LoadingIndicatorContainerStubComponent, TutorialGroupSessionFormStubComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [MockProvider(TutorialGroupSessionService), MockProvider(AlertService), MockProvider(NgbActiveModal)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(EditTutorialGroupSessionComponent);
                component = fixture.componentInstance;
                activeModal = TestBed.inject(NgbActiveModal);
                sessionService = TestBed.inject(TutorialGroupSessionService);
                exampleSession = generateExampleTutorialGroupSession({});
                exampleTutorialGroup = generateExampleTutorialGroup({});
                component.course = course;
                component.tutorialGroupSession = exampleSession;
                component.tutorialGroup = exampleTutorialGroup;
                component.initialize();
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).not.toBeNull();
    });

    it('should set form data correctly', () => {
        fixture.detectChanges();
        const formStub: TutorialGroupSessionFormStubComponent = fixture.debugElement.query(By.directive(TutorialGroupSessionFormStubComponent)).componentInstance;
        expect(component.formData).toEqual(tutorialGroupSessionToTutorialGroupSessionFormData(exampleSession, timeZone));
        expect(formStub.formData).toEqual(component.formData);
    });

    it('should send PUT request upon form submission and navigate', () => {
        fixture.detectChanges();

        const changedSession: TutorialGroupSession = {
            ...exampleSession,
            location: 'Changed',
        };

        const updateResponse: HttpResponse<TutorialGroupSession> = new HttpResponse({
            body: changedSession,
            status: 200,
        });

        const updatedStub = jest.spyOn(sessionService, 'update').mockReturnValue(of(updateResponse));
        const closeSpy = jest.spyOn(activeModal, 'close');

        const sessionForm: TutorialGroupSessionFormStubComponent = fixture.debugElement.query(By.directive(TutorialGroupSessionFormStubComponent)).componentInstance;

        const formData = tutorialGroupSessionToTutorialGroupSessionFormData(changedSession, timeZone);

        sessionForm.formSubmitted.emit(formData);

        expect(updatedStub).toHaveBeenCalledOnce();
        expect(updatedStub).toHaveBeenCalledWith(course.id!, exampleTutorialGroup.id!, exampleSession.id!, formDataToTutorialGroupSessionDTO(formData));
        expect(closeSpy).toHaveBeenCalledOnce();
    });
});
