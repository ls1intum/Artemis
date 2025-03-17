import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockProvider } from 'ng-mocks';
import { AlertService } from 'app/shared/service/alert.service';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { By } from '@angular/platform-browser';
import { CreateTutorialGroupSessionComponent } from 'app/tutorialgroup/manage/tutorial-group-sessions/crud/create-tutorial-group-session/create-tutorial-group-session.component';
import { TutorialGroupSessionService } from 'app/course/tutorial-groups/services/tutorial-group-session.service';
import {
    formDataToTutorialGroupSessionDTO,
    generateExampleTutorialGroupSession,
    tutorialGroupSessionToTutorialGroupSessionFormData,
} from '../../../helpers/tutorialGroupSessionExampleModels';
import { TutorialGroupSession } from 'app/entities/tutorial-group/tutorial-group-session.model';
import { generateExampleTutorialGroup } from '../../../helpers/tutorialGroupExampleModels';
import { Course } from 'app/entities/course.model';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { TutorialGroupSessionFormComponent } from 'app/tutorialgroup/manage/tutorial-group-sessions/crud/tutorial-group-session-form/tutorial-group-session-form.component';
import { OwlNativeDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import '@angular/localize/init';
import { MockTranslateService } from '../../../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('CreateTutorialGroupSessionComponent', () => {
    let fixture: ComponentFixture<CreateTutorialGroupSessionComponent>;
    let component: CreateTutorialGroupSessionComponent;
    let tutorialGroupSessionService: TutorialGroupSessionService;
    const course = { id: 2, timeZone: 'Europe/Berlin' } as Course;
    let tutorialGroup: TutorialGroup;
    let activeModal: NgbActiveModal;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [OwlNativeDateTimeModule],
            providers: [
                MockProvider(TutorialGroupSessionService),
                MockProvider(AlertService),
                MockProvider(NgbActiveModal),
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();
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

        const sessionForm: TutorialGroupSessionFormComponent = fixture.debugElement.query(By.directive(TutorialGroupSessionFormComponent)).componentInstance;

        const formData = tutorialGroupSessionToTutorialGroupSessionFormData(exampleSession, 'Europe/Berlin');

        sessionForm.formSubmitted.emit(formData);

        expect(createStub).toHaveBeenCalledOnce();
        expect(createStub).toHaveBeenCalledWith(course.id!, tutorialGroup.id!, formDataToTutorialGroupSessionDTO(formData));
        expect(closeSpy).toHaveBeenCalledOnce();
    });
});
