import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { MockComponent, MockDirective, MockModule, MockPipe, MockProvider } from 'ng-mocks';
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
import { LectureUpdateWizardStepComponent } from 'app/lecture/wizard-mode/lecture-update-wizard-step.component';
import { LectureUpdateWizardUnitsComponent } from 'app/lecture/wizard-mode/lecture-wizard-units.component';
import { LectureUpdateWizardAttachmentsComponent } from 'app/lecture/wizard-mode/lecture-wizard-attachments.component';
import { LectureUpdateWizardTitleComponent } from 'app/lecture/wizard-mode/lecture-wizard-title.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import dayjs from 'dayjs/esm';
import { LectureUpdatePeriodComponent } from '../../../../../../main/webapp/app/lecture/lecture-period/lecture-period.component';
import { ArtemisTestModule } from '../../../test.module';
import { ArtemisSharedModule } from '../../../../../../main/webapp/app/shared/shared.module';
import { FormsModule } from '../../../../../../main/webapp/app/forms/forms.module';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { OwlDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import { LectureUpdateComponent } from '../../../../../../main/webapp/app/lecture/lecture-update.component';
import { LectureTitleChannelNameComponent } from '../../../../../../main/webapp/app/lecture/lecture-title-channel-name.component';
import { TitleChannelNameComponent } from '../../../../../../main/webapp/app/shared/form/title-channel-name/title-channel-name.component';
import { FormDateTimePickerComponent } from '../../../../../../main/webapp/app/shared/date-time-picker/date-time-picker.component';
import { MarkdownEditorMonacoComponent } from '../../../../../../main/webapp/app/shared/markdown-editor/monaco/markdown-editor-monaco.component';
import { CustomNotIncludedInValidatorDirective } from '../../../../../../main/webapp/app/shared/validators/custom-not-included-in-validator.directive';

describe('LectureWizardComponent', () => {
    let wizardComponentFixture: ComponentFixture<LectureUpdateWizardComponent>;
    let wizardComponent: LectureUpdateWizardComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, ArtemisSharedModule, FormsModule, MockModule(NgbTooltipModule), MockModule(OwlDateTimeModule)],
            declarations: [
                LectureUpdateWizardComponent,
                LectureUpdateComponent,
                LectureTitleChannelNameComponent,
                TitleChannelNameComponent,
                FormDateTimePickerComponent,
                LectureUpdatePeriodComponent,
                LectureUpdateWizardTitleComponent,
                MockComponent(MarkdownEditorMonacoComponent),
                MockPipe(ArtemisTranslatePipe),
                MockComponent(LectureUpdateWizardStepComponent),
                MockComponent(LectureUpdateWizardUnitsComponent),
                MockComponent(LectureUpdateWizardAttachmentsComponent),
                MockComponent(FaIconComponent),
                MockDirective(TranslateDirective),
                MockDirective(CustomNotIncludedInValidatorDirective),
            ],
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
            expect(wizardComponent.currentStep).toBe(4);
        });
    }));

    it('should initialize and set step without given lecture', fakeAsync(() => {
        wizardComponent.lecture.id = undefined;

        wizardComponentFixture.detectChanges();
        expect(wizardComponent).not.toBeNull();

        tick();

        wizardComponentFixture.whenStable().then(() => {
            expect(wizardComponent.currentStep).toBe(1);
        });
    }));

    it('should initialize and set step without given lecture but preset date', fakeAsync(() => {
        wizardComponent.lecture.id = undefined;
        wizardComponent.lecture.startDate = dayjs().year(2010).month(3).date(5);

        wizardComponentFixture.detectChanges();
        expect(wizardComponent).not.toBeNull();

        tick();

        wizardComponentFixture.whenStable().then(() => {
            expect(wizardComponent.currentStep).toBe(2);
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
            expect(wizardComponent.currentStep).toBe(4);
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
