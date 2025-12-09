import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockProvider } from 'ng-mocks';
import { AlertService } from 'app/shared/service/alert.service';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { By } from '@angular/platform-browser';
import { EditTutorialGroupSessionComponent } from 'app/tutorialgroup/manage/tutorial-group-sessions/crud/edit-tutorial-group-session/edit-tutorial-group-session.component';
import { TutorialGroupSessionService } from 'app/tutorialgroup/shared/service/tutorial-group-session.service';
import { TutorialGroupSession } from 'app/tutorialgroup/shared/entities/tutorial-group-session.model';
import {
    formDataToTutorialGroupSessionDTO,
    generateExampleTutorialGroupSession,
    tutorialGroupSessionToTutorialGroupSessionFormData,
} from 'test/helpers/sample/tutorialgroup/tutorialGroupSessionExampleModels';
import { Course } from 'app/core/course/shared/entities/course.model';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { generateExampleTutorialGroup } from 'test/helpers/sample/tutorialgroup/tutorialGroupExampleModels';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { TutorialGroupSessionFormComponent } from 'app/tutorialgroup/manage/tutorial-group-sessions/crud/tutorial-group-session-form/tutorial-group-session-form.component';
import { OwlNativeDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import '@angular/localize/init';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { expectComponentRendered } from '../../../../../../../../test/javascript/spec/helpers/sample/tutorialgroup/tutorialGroupFormsUtils';

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
            imports: [OwlNativeDateTimeModule],
            providers: [
                MockProvider(TutorialGroupSessionService),
                MockProvider(AlertService),
                MockProvider(NgbActiveModal),
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(EditTutorialGroupSessionComponent);
        component = fixture.componentInstance;
        activeModal = TestBed.inject(NgbActiveModal);
        sessionService = TestBed.inject(TutorialGroupSessionService);
        exampleSession = generateExampleTutorialGroupSession({});
        exampleTutorialGroup = generateExampleTutorialGroup({});
        fixture.componentRef.setInput('course', course);
        fixture.componentRef.setInput('tutorialGroupSession', exampleSession);
        fixture.componentRef.setInput('tutorialGroup', exampleTutorialGroup);
        component.initialize();
        fixture.detectChanges();
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        expect(component).not.toBeNull();
    });

    it('should set form data correctly', () => {
        const formStub = expectComponentRendered<TutorialGroupSessionFormComponent>(fixture, 'jhi-tutorial-group-session-form');
        fixture.detectChanges();
        expect(component.formData).toEqual(tutorialGroupSessionToTutorialGroupSessionFormData(exampleSession, timeZone));
        expect(formStub.formData()).toEqual(component.formData);
    });

    it('should send PUT request upon form submission and navigate', () => {
        const changedSession: TutorialGroupSession = Object.assign({}, exampleSession, { location: 'Changed' });

        const updateResponse: HttpResponse<TutorialGroupSession> = new HttpResponse({
            body: changedSession,
            status: 200,
        });

        const updatedStub = jest.spyOn(sessionService, 'update').mockReturnValue(of(updateResponse));
        const closeSpy = jest.spyOn(activeModal, 'close');

        const sessionForm: TutorialGroupSessionFormComponent = fixture.debugElement.query(By.directive(TutorialGroupSessionFormComponent)).componentInstance;

        const formData = tutorialGroupSessionToTutorialGroupSessionFormData(changedSession, timeZone);

        sessionForm.formSubmitted.emit(formData);

        expect(updatedStub).toHaveBeenCalledOnce();
        expect(updatedStub).toHaveBeenCalledWith(course.id!, exampleTutorialGroup.id!, exampleSession.id!, formDataToTutorialGroupSessionDTO(formData));
        expect(closeSpy).toHaveBeenCalledOnce();
    });
});
