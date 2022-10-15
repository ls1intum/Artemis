import { ComponentFixture, fakeAsync, TestBed } from '@angular/core/testing';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe, MockProvider } from 'ng-mocks';
import { AlertService } from 'app/core/util/alert.service';
import { CancellationModalComponent } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-group-sessions/tutorial-group-sessions-management/cancellation-modal/cancellation-modal.component';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { TutorialGroupSessionService } from 'app/course/tutorial-groups/services/tutorial-group-session.service';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { generateExampleTutorialGroupSession } from '../../../helpers/tutorialGroupSessionExampleModels';
import { TutorialGroupSession, TutorialGroupSessionStatus } from 'app/entities/tutorial-group/tutorial-group-session.model';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';

describe('CancellationModalComponent', () => {
    let fixture: ComponentFixture<CancellationModalComponent>;
    let component: CancellationModalComponent;
    const courseId = 1;
    const tutorialGroupId = 2;
    const tutorialGroupSessionId = 3;
    const tutorialGroupSession = generateExampleTutorialGroupSession(tutorialGroupSessionId);
    let tutorialGroupSessionService: TutorialGroupSessionService;
    let modal: NgbActiveModal;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [FormsModule, ReactiveFormsModule],
            declarations: [CancellationModalComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [MockProvider(NgbActiveModal), MockProvider(TutorialGroupSessionService), MockProvider(AlertService)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CancellationModalComponent);
                component = fixture.componentInstance;
                component.courseId = courseId;
                component.tutorialGroupId = tutorialGroupId;
                component.tutorialGroupSession = tutorialGroupSession;

                tutorialGroupSessionService = TestBed.inject(TutorialGroupSessionService);
                modal = TestBed.inject(NgbActiveModal);

                fixture.detectChanges();
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        expect(component).not.toBeNull();
    });

    it('should call cancel when the activate cancel button is clicked with a active session', fakeAsync(() => {
        const cancelSessionSpy = jest.spyOn(tutorialGroupSessionService, 'cancel').mockReturnValue(of(new HttpResponse<TutorialGroupSession>({ body: tutorialGroupSession })));
        const closeModalSpy = jest.spyOn(modal, 'close');

        component!.reasonControl!.setValue('National Holiday');
        fixture.detectChanges();
        // click button with id cancel-activate-button
        const button = fixture.debugElement.nativeElement.querySelector('#cancel-activate-button');
        button.click();

        fixture.whenStable().then(() => {
            expect(cancelSessionSpy).toHaveBeenCalledOnce();
            expect(cancelSessionSpy).toHaveBeenCalledWith(courseId, tutorialGroupId, tutorialGroupSessionId, 'National Holiday');
            expect(closeModalSpy).toHaveBeenCalledOnce();
            expect(closeModalSpy).toHaveBeenCalledWith('confirmed');
        });
    }));

    it('should call activate when the activate cancel button is clicked with a cancelled session', fakeAsync(() => {
        const activateSesssionSpy = jest.spyOn(tutorialGroupSessionService, 'activate').mockReturnValue(of(new HttpResponse<TutorialGroupSession>({ body: tutorialGroupSession })));
        const closeModalSpy = jest.spyOn(modal, 'close');

        component!.reasonControl!.setValue('National Holiday');
        fixture.detectChanges();
        component.tutorialGroupSession.status = TutorialGroupSessionStatus.CANCELLED;
        // click button with id cancel-activate-button
        const button = fixture.debugElement.nativeElement.querySelector('#cancel-activate-button');
        button.click();

        fixture.whenStable().then(() => {
            expect(activateSesssionSpy).toHaveBeenCalledOnce();
            expect(activateSesssionSpy).toHaveBeenCalledWith(courseId, tutorialGroupId, tutorialGroupSessionId);
            expect(closeModalSpy).toHaveBeenCalledOnce();
            expect(closeModalSpy).toHaveBeenCalledWith('confirmed');
        });
    }));
});
