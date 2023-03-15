// eslint-disable-next-line max-len
import { HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { MockPipe, MockProvider } from 'ng-mocks';
import { of } from 'rxjs';

import { LoadingIndicatorContainerStubComponent } from '../../../../../helpers/stubs/loading-indicator-container-stub.component';
import { generateExampleTutorialGroup } from '../../../helpers/tutorialGroupExampleModels';
import {
    formDataToTutorialGroupSessionDTO,
    generateExampleTutorialGroupSession,
    tutorialGroupSessionToTutorialGroupSessionFormData,
} from '../../../helpers/tutorialGroupSessionExampleModels';
import { TutorialGroupSessionFormStubComponent } from '../../../stubs/tutorial-group-session-form-stub.component';
import { AlertService } from 'app/core/util/alert.service';
import { TutorialGroupSessionService } from 'app/course/tutorial-groups/services/tutorial-group-session.service';
import { CreateTutorialGroupSessionComponent } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-group-sessions/crud/create-tutorial-group-session/create-tutorial-group-session.component';
import { Course } from 'app/entities/course.model';
import { TutorialGroupSession } from 'app/entities/tutorial-group/tutorial-group-session.model';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

describe('CreateTutorialGroupSessionComponent', () => {
    let fixture: ComponentFixture<CreateTutorialGroupSessionComponent>;
    let component: CreateTutorialGroupSessionComponent;
    let tutorialGroupSessionService: TutorialGroupSessionService;
    const course = { id: 2, timeZone: 'Europe/Berlin' } as Course;
    let tutorialGroup: TutorialGroup;
    let activeModal: NgbActiveModal;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [CreateTutorialGroupSessionComponent, LoadingIndicatorContainerStubComponent, TutorialGroupSessionFormStubComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [MockProvider(TutorialGroupSessionService), MockProvider(AlertService), MockProvider(NgbActiveModal)],
        })
            .compileComponents()
            .then(() => {
                tutorialGroup = generateExampleTutorialGroup({ id: 1 });
                activeModal = TestBed.inject(NgbActiveModal);
                fixture = TestBed.createComponent(CreateTutorialGroupSessionComponent);
                component = fixture.componentInstance;
                component.course = course;
                component.tutorialGroup = tutorialGroup;
                component.initialize();

                tutorialGroupSessionService = TestBed.inject(TutorialGroupSessionService);
                fixture.detectChanges();
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        expect(component).not.toBeNull();
    });

    it('should send POST request upon form submission and close modal', () => {
        const exampleSession = generateExampleTutorialGroupSession({});
        delete exampleSession.id;

        const createResponse: HttpResponse<TutorialGroupSession> = new HttpResponse({
            body: exampleSession,
            status: 201,
        });

        const createStub = jest.spyOn(tutorialGroupSessionService, 'create').mockReturnValue(of(createResponse));
        const closeSpy = jest.spyOn(activeModal, 'close');

        const sessionForm: TutorialGroupSessionFormStubComponent = fixture.debugElement.query(By.directive(TutorialGroupSessionFormStubComponent)).componentInstance;

        const formData = tutorialGroupSessionToTutorialGroupSessionFormData(exampleSession, 'Europe/Berlin');

        sessionForm.formSubmitted.emit(formData);

        expect(createStub).toHaveBeenCalledOnce();
        expect(createStub).toHaveBeenCalledWith(course.id!, tutorialGroup.id!, formDataToTutorialGroupSessionDTO(formData));
        expect(closeSpy).toHaveBeenCalledOnce();
    });
});
