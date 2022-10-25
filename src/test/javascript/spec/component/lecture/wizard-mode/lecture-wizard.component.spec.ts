import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { MockPipe, MockProvider } from 'ng-mocks';
import { ActivatedRoute, Router } from '@angular/router';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { of } from 'rxjs';
import { Lecture } from 'app/entities/lecture.model';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { Course } from 'app/entities/course.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { LectureUpdateWizardComponent } from 'app/lecture/wizard-mode/lecture-update-wizard.component';
import { ArtemisNavigationUtilService } from 'app/utils/navigation.utils';
import { CourseManagementService } from 'app/course/manage/course-management.service';

describe('LectureWizardComponent', () => {
    let wizardComponentFixture: ComponentFixture<LectureUpdateWizardComponent>;
    let wizardComponent: LectureUpdateWizardComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [LectureUpdateWizardComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [
                MockProvider(ArtemisNavigationUtilService),
                MockProvider(CourseManagementService),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: Router, useClass: MockRouter },
                {
                    provide: ActivatedRoute,
                    useValue: { queryParams: of({}) },
                },
            ],
            schemas: [],
        })
            .compileComponents()
            .then(() => {
                wizardComponentFixture = TestBed.createComponent(LectureUpdateWizardComponent);
                wizardComponent = wizardComponentFixture.componentInstance;

                const course = new Course();
                course.id = 2;

                wizardComponent.lecture = new Lecture();
                wizardComponent.lecture.id = 1;
                wizardComponent.lecture.course = course;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize and set step with given lecture', fakeAsync(() => {
        wizardComponentFixture.detectChanges();
        expect(wizardComponent).not.toBeNull();

        wizardComponentFixture.whenStable().then(() => {
            expect(wizardComponent.currentStep).toBe(5);
        });
    }));

    it('should initialize and set step without given lecture', fakeAsync(() => {
        wizardComponent.lecture.id = undefined;

        wizardComponentFixture.detectChanges();
        expect(wizardComponent).not.toBeNull();

        wizardComponentFixture.whenStable().then(() => {
            expect(wizardComponent.currentStep).toBe(1);
        });
    }));

    it('should initialize and set given step', fakeAsync(() => {
        const route = TestBed.inject(ActivatedRoute);
        route.queryParams = of({ step: 3 });

        wizardComponentFixture.detectChanges();
        expect(wizardComponent).not.toBeNull();

        wizardComponentFixture.whenStable().then(() => {
            expect(wizardComponent.currentStep).toBe(3);
        });
    }));

    it('should increase the step when clicked', fakeAsync(() => {
        const route = TestBed.inject(ActivatedRoute);
        route.queryParams = of({ step: 1 });

        wizardComponentFixture.detectChanges();

        wizardComponentFixture.whenStable().then(() => {
            expect(wizardComponent.currentStep).toBe(1);
            wizardComponent.next();
            expect(wizardComponent.currentStep).toBe(2);
        });
    }));

    it('should save the lecture when finishing the last step', fakeAsync(() => {
        wizardComponent.saveLectureFunction = () => {};
        const saveStub = jest.spyOn(wizardComponent, 'saveLectureFunction');

        wizardComponentFixture.detectChanges();

        wizardComponentFixture.whenStable().then(() => {
            expect(wizardComponent.currentStep).toBe(5);
            wizardComponent.next();
            expect(saveStub).toHaveBeenCalledOnce();
        });
    }));

    it('should increase the step after lecture created', fakeAsync(() => {
        wizardComponentFixture.detectChanges();

        wizardComponentFixture.whenStable().then(() => {
            wizardComponent.currentStep = 2;
            wizardComponent.onLectureCreationSucceeded();
            expect(wizardComponent.currentStep).toBe(3);
        });
    }));

    it('should return is completed for smaller step', fakeAsync(() => {
        wizardComponentFixture.detectChanges();

        wizardComponentFixture.whenStable().then(() => {
            wizardComponent.currentStep = 2;
            const result = wizardComponent.isCompleted(1);
            expect(result).toBeTrue();
        });
    }));

    it('should not return is completed for bigger step', fakeAsync(() => {
        wizardComponentFixture.detectChanges();

        wizardComponentFixture.whenStable().then(() => {
            wizardComponent.currentStep = 2;
            const result = wizardComponent.isCompleted(3);
            expect(result).toBeFalse();
        });
    }));

    it('should return is current for same step', fakeAsync(() => {
        wizardComponentFixture.detectChanges();

        wizardComponentFixture.whenStable().then(() => {
            wizardComponent.currentStep = 2;
            const result = wizardComponent.isCurrent(2);
            expect(result).toBeTrue();
        });
    }));

    it('should not return is current for different step', fakeAsync(() => {
        wizardComponentFixture.detectChanges();

        wizardComponentFixture.whenStable().then(() => {
            wizardComponent.currentStep = 2;
            const result = wizardComponent.isCurrent(1);
            expect(result).toBeFalse();
        });
    }));

    it('should return correct icon for last step', fakeAsync(() => {
        wizardComponentFixture.detectChanges();

        wizardComponentFixture.whenStable().then(() => {
            wizardComponent.currentStep = 5;
            const result = wizardComponent.getNextIcon();
            expect(result).toBe(wizardComponent.faCheck);
        });
    }));

    it('should return correct icon for not last step', fakeAsync(() => {
        wizardComponentFixture.detectChanges();

        wizardComponentFixture.whenStable().then(() => {
            wizardComponent.currentStep = 3;
            const result = wizardComponent.getNextIcon();
            expect(result).toBe(wizardComponent.faArrowRight);
        });
    }));

    it('should return correct text for last step', fakeAsync(() => {
        wizardComponentFixture.detectChanges();

        wizardComponentFixture.whenStable().then(() => {
            wizardComponent.currentStep = 5;
            const result = wizardComponent.getNextText();
            expect(result).toBe('entity.action.finish');
        });
    }));

    it('should return correct text for not last step', fakeAsync(() => {
        wizardComponentFixture.detectChanges();

        wizardComponentFixture.whenStable().then(() => {
            wizardComponent.currentStep = 3;
            const result = wizardComponent.getNextText();
            expect(result).toBe('artemisApp.lecture.home.nextStepLabel');
        });
    }));

    it('should toggle wizard when lecture not created', fakeAsync(() => {
        wizardComponent.toggleModeFunction = () => {};
        const toggleStub = jest.spyOn(wizardComponent, 'toggleModeFunction');

        wizardComponentFixture.detectChanges();

        wizardComponentFixture.whenStable().then(() => {
            wizardComponent.currentStep = 1;
            wizardComponent.toggleWizardMode();
            expect(toggleStub).toHaveBeenCalledOnce();
        });
    }));

    it('should navigate when toggling wizard after lecture was created', fakeAsync(() => {
        const router = TestBed.inject(Router);
        const navigateStub = jest.spyOn(router, 'navigate');

        wizardComponentFixture.detectChanges();

        wizardComponentFixture.whenStable().then(() => {
            wizardComponent.currentStep = 3;
            wizardComponent.toggleWizardMode();
            expect(navigateStub).toHaveBeenCalledTimes(2); // 1 from init to clear the params and 1 from toggling
        });
    }));
});
