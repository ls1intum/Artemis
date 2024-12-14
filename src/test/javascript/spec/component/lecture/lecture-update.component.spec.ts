import { HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { By } from '@angular/platform-browser';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { Lecture } from 'app/entities/lecture.model';
import { LectureUpdateComponent } from 'app/lecture/lecture-update.component';
import { LectureService } from 'app/lecture/lecture.service';
import { LectureUpdateWizardComponent } from 'app/lecture/wizard-mode/lecture-update-wizard.component';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import dayjs from 'dayjs/esm';
import { MockComponent, MockDirective, MockModule, MockPipe } from 'ng-mocks';
import { of } from 'rxjs';
import { MockRouterLinkDirective } from '../../helpers/mocks/directive/mock-router-link.directive';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { ArtemisTestModule } from '../../test.module';
import { DocumentationButtonComponent } from 'app/shared/components/documentation-button/documentation-button.component';
import { LectureTitleChannelNameComponent } from 'app/lecture/lecture-title-channel-name.component';
import { MarkdownEditorMonacoComponent } from 'app/shared/markdown-editor/monaco/markdown-editor-monaco.component';
import { CustomNotIncludedInValidatorDirective } from '../../../../../main/webapp/app/shared/validators/custom-not-included-in-validator.directive';
import { ArtemisSharedModule } from '../../../../../main/webapp/app/shared/shared.module';
import { OwlDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import { TitleChannelNameComponent } from '../../../../../main/webapp/app/shared/form/title-channel-name/title-channel-name.component';
import { LectureUpdatePeriodComponent } from '../../../../../main/webapp/app/lecture/lecture-period/lecture-period.component';
import { LectureUnitManagementComponent } from '../../../../../main/webapp/app/lecture/lecture-unit/lecture-unit-management/lecture-unit-management.component';

describe('LectureUpdateComponent', () => {
    let lectureUpdateWizardComponentFixture: ComponentFixture<LectureUpdateWizardComponent>;
    let lectureUpdateWizardComponent: LectureUpdateWizardComponent;

    let lectureService: LectureService;
    let lectureUpdateComponentFixture: ComponentFixture<LectureUpdateComponent>;
    let lectureUpdateComponent: LectureUpdateComponent;
    let router: Router;
    let activatedRoute: ActivatedRoute;

    let pastLecture: Lecture;

    beforeEach(() => {
        const yesterday = dayjs().subtract(1, 'day');

        pastLecture = new Lecture();
        pastLecture.id = 6;
        pastLecture.title = 'past lecture';
        pastLecture.endDate = yesterday;

        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, ArtemisSharedModule, FormsModule, MockModule(NgbTooltipModule), MockModule(OwlDateTimeModule)],
            declarations: [
                LectureUpdateComponent,
                LectureTitleChannelNameComponent,
                TitleChannelNameComponent,
                FormDateTimePickerComponent,
                LectureUpdatePeriodComponent,
                MockComponent(LectureUpdateWizardComponent),
                MockComponent(LectureUnitManagementComponent),
                MockComponent(MarkdownEditorMonacoComponent),
                MockComponent(DocumentationButtonComponent),
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockPipe(HtmlForMarkdownPipe),
                MockRouterLinkDirective,
                MockDirective(CustomNotIncludedInValidatorDirective),
            ],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: Router, useClass: MockRouter },
                {
                    provide: ActivatedRoute,
                    useValue: {
                        parent: {
                            data: of({ course: { id: 1 } }),
                        },
                        queryParams: of({
                            params: {},
                        }),
                        snapshot: {
                            paramMap: convertToParamMap({
                                courseId: '1',
                            }),
                        },
                    },
                },
            ],
        })
            .compileComponents()
            .then(() => {
                lectureUpdateComponentFixture = TestBed.createComponent(LectureUpdateComponent);
                lectureUpdateComponent = lectureUpdateComponentFixture.componentInstance;

                lectureUpdateWizardComponentFixture = TestBed.createComponent(LectureUpdateWizardComponent);
                lectureUpdateWizardComponent = lectureUpdateWizardComponentFixture.componentInstance;

                lectureService = TestBed.inject(LectureService);
                router = TestBed.get(Router);
                activatedRoute = TestBed.inject(ActivatedRoute);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should create lecture', () => {
        lectureUpdateComponent.lecture.set({ title: 'test1', channelName: 'test1' } as Lecture);
        const navigateSpy = jest.spyOn(router, 'navigate');

        const createSpy = jest.spyOn(lectureService, 'create').mockReturnValue(
            of(
                new HttpResponse({
                    body: {
                        id: 3,
                        title: 'test1',
                        course: {
                            id: 1,
                        },
                    } as Lecture,
                }),
            ),
        );

        lectureUpdateComponent.save();
        lectureUpdateComponentFixture.detectChanges();

        const expectedPath = ['course-management', 1, 'lectures', 3];
        expect(navigateSpy).toHaveBeenCalledWith(expectedPath);

        expect(createSpy).toHaveBeenCalledOnce();
        expect(createSpy).toHaveBeenCalledWith({ title: 'test1', channelName: 'test1' });
    });

    it('should create lecture in wizard mode', () => {
        lectureUpdateComponent.lecture.set({ title: '', channelName: '' } as Lecture);
        lectureUpdateComponent.isShowingWizardMode = true;
        lectureUpdateComponent.wizardComponent = lectureUpdateWizardComponent;

        const createSpy = jest.spyOn(lectureService, 'create').mockReturnValue(
            of<HttpResponse<Lecture>>(
                new HttpResponse({
                    body: {
                        title: 'test1',
                        course: {
                            id: 1,
                        },
                    } as Lecture,
                }),
            ),
        );

        const findSpy = jest.spyOn(lectureService, 'findWithDetails').mockReturnValue(
            of<HttpResponse<Lecture>>(
                new HttpResponse({
                    body: {
                        id: 3,
                        title: 'test1',
                        course: {
                            id: 1,
                        },
                    } as Lecture,
                }),
            ),
        );

        const onLectureCreationSucceededSpy = jest.spyOn(lectureUpdateWizardComponent, 'onLectureCreationSucceeded');

        lectureUpdateComponent.save();

        expect(createSpy).toHaveBeenCalledOnce();
        expect(createSpy).toHaveBeenCalledWith({ title: '', channelName: '' });

        expect(findSpy).toHaveBeenCalledOnce();
        expect(onLectureCreationSucceededSpy).toHaveBeenCalledOnce();
    });

    it('should edit a lecture', fakeAsync(() => {
        activatedRoute.parent!.data = of({ course: { id: 1 }, lecture: { id: 6 } });

        lectureUpdateComponentFixture.detectChanges();
        lectureUpdateComponent.lecture.set({ id: 6, title: 'test1Updated', channelName: 'test1Updated' } as Lecture);

        const updateSpy = jest.spyOn(lectureService, 'update').mockReturnValue(
            of<HttpResponse<Lecture>>(
                new HttpResponse({
                    body: {
                        id: 6,
                        title: 'test1Updated',
                        course: {
                            id: 1,
                        },
                    } as Lecture,
                }),
            ),
        );

        lectureUpdateComponent.save();
        tick();
        lectureUpdateComponentFixture.detectChanges();

        expect(updateSpy).toHaveBeenCalledOnce();
        expect(updateSpy).toHaveBeenCalledWith({ id: 6, title: 'test1Updated', channelName: 'test1Updated' });
    }));

    it('should switch to wizard mode', fakeAsync(() => {
        lectureUpdateComponent.isShowingWizardMode = false;
        const wizardModeButton = jest.spyOn(lectureUpdateComponent, 'toggleWizardMode');
        lectureUpdateComponent.toggleWizardMode();
        tick();
        expect(wizardModeButton).toHaveBeenCalledOnce();
        expect(lectureUpdateComponent.isShowingWizardMode).toBeTrue();
    }));

    it('should be in wizard mode', fakeAsync(() => {
        activatedRoute = TestBed.inject(ActivatedRoute);
        activatedRoute.queryParams = of({
            shouldBeInWizardMode: true,
        });

        lectureUpdateComponent.ngOnInit();
        lectureUpdateComponentFixture.detectChanges();
        tick();

        expect(lectureUpdateComponent.isShowingWizardMode).toBeTrue();
    }));

    it('should select process units checkbox', fakeAsync(() => {
        lectureUpdateComponent.processUnitMode = false;
        const selectProcessUnit = jest.spyOn(lectureUpdateComponent, 'onSelectProcessUnit');
        lectureUpdateComponent.onSelectProcessUnit();
        tick();
        expect(selectProcessUnit).toHaveBeenCalledOnce();
        expect(lectureUpdateComponent.processUnitMode).toBeTrue();
    }));

    it('should navigate to previous state', fakeAsync(() => {
        activatedRoute = TestBed.inject(ActivatedRoute);
        activatedRoute.parent!.data = of({ course: { id: 1 }, lecture: { id: 6, title: '', course: { id: 1 } } });

        lectureUpdateComponent.ngOnInit();
        lectureUpdateComponentFixture.detectChanges();

        const navigateSpy = jest.spyOn(router, 'navigate');
        const previousState = jest.spyOn(lectureUpdateComponent, 'previousState');
        lectureUpdateComponent.previousState();
        tick();
        expect(previousState).toHaveBeenCalledOnce();

        const expectedPath = ['course-management', '1', 'lectures', '6'];
        expect(navigateSpy).toHaveBeenCalledWith(expectedPath);
    }));

    it('should create a lecture and then redirect to unit split', fakeAsync(() => {
        lectureUpdateComponent.file = new File([''], 'testFile.pdf', { type: 'application/pdf' });
        lectureUpdateComponent.fileName = 'testFile';
        lectureUpdateComponent.processUnitMode = true;
        lectureUpdateComponent.lecture.set({ title: 'test1', channelName: 'test1' } as Lecture);
        const navigateSpy = jest.spyOn(router, 'navigate');

        const createSpy = jest.spyOn(lectureService, 'create').mockReturnValue(
            of<HttpResponse<Lecture>>(
                new HttpResponse({
                    body: {
                        id: 3,
                        title: 'test1',
                        course: {
                            id: 1,
                        },
                    } as Lecture,
                }),
            ),
        );

        const proceedToUnitSplitSpy = jest.spyOn(lectureUpdateComponent, 'proceedToUnitSplit');
        lectureUpdateComponent.proceedToUnitSplit();
        tick();

        expect(createSpy).toHaveBeenCalledOnce();
        expect(createSpy).toHaveBeenCalledWith({ title: 'test1', channelName: 'test1' });
        expect(proceedToUnitSplitSpy).toHaveBeenCalledOnce();
        expect(lectureUpdateComponent.processUnitMode).toBeTrue();

        const expectedPath = ['course-management', 1, 'lectures', 3, 'unit-management', 'attachment-units', 'process'];
        expect(navigateSpy).toHaveBeenCalledWith(expectedPath, { state: { file: lectureUpdateComponent.file, fileName: lectureUpdateComponent.fileName } });
    }));

    it('should call onFileChange on changed file', () => {
        lectureUpdateComponent.processUnitMode = false;
        lectureUpdateComponentFixture.detectChanges();
        expect(lectureUpdateComponentFixture.debugElement.nativeElement.querySelector('#fileInput')).toBeFalsy();

        const onFileChangeStub = jest.spyOn(lectureUpdateComponent, 'onFileChange');

        const processUnit = lectureUpdateComponentFixture.debugElement.query(By.css('input[name="processUnit"]')).nativeElement;
        processUnit.click();
        expect(processUnit.checked).toBeTruthy();
        lectureUpdateComponent.processUnitMode = true;
        lectureUpdateComponentFixture.autoDetectChanges();
        const fileInput = lectureUpdateComponentFixture.debugElement.nativeElement.querySelector('#fileInput');
        expect(lectureUpdateComponentFixture.debugElement.nativeElement.querySelector('#fileInput')).toBeTruthy();
        fileInput.dispatchEvent(new Event('change'));
        expect(onFileChangeStub).toHaveBeenCalledOnce();
    });

    it('should set lecture visible date, start date and end date correctly', fakeAsync(() => {
        activatedRoute = TestBed.inject(ActivatedRoute);
        activatedRoute.parent!.data = of({ course: { id: 1 }, lecture: { id: 6 } });

        lectureUpdateComponentFixture.detectChanges();
        lectureUpdateComponent.lecture.set({ id: 6, title: 'test1Updated' } as Lecture);

        const setDatesSpy = jest.spyOn(lectureUpdateComponent, 'onDatesValuesChanged');

        lectureUpdateComponent.lecture().visibleDate = dayjs().year(2022).month(3).date(7);
        lectureUpdateComponent.lecture().startDate = dayjs().year(2022).month(3).date(5);
        lectureUpdateComponent.lecture().endDate = dayjs().year(2022).month(3).date(1);

        lectureUpdateComponent.onDatesValuesChanged();

        expect(setDatesSpy).toHaveBeenCalledOnce();
        expect(lectureUpdateComponent.lecture().startDate).toEqual(lectureUpdateComponent.lecture().endDate);
        expect(lectureUpdateComponent.lecture().startDate).toEqual(lectureUpdateComponent.lecture().visibleDate);

        lectureUpdateComponentFixture.detectChanges();
        tick();

        lectureUpdateComponent.lecture().startDate = undefined;
        lectureUpdateComponent.lecture().endDate = undefined;
        lectureUpdateComponent.lecture().visibleDate = undefined;

        lectureUpdateComponent.onDatesValuesChanged();

        expect(setDatesSpy).toHaveBeenCalledTimes(2);
        expect(lectureUpdateComponent.lecture().startDate).toBeUndefined();
        expect(lectureUpdateComponent.lecture().endDate).toBeUndefined();
        expect(lectureUpdateComponent.lecture().visibleDate).toBeUndefined();

        lectureUpdateComponentFixture.detectChanges();
        tick();

        lectureUpdateComponent.lecture().visibleDate = dayjs().year(2022).month(1).date(1);
        lectureUpdateComponent.lecture().startDate = dayjs().year(2022).month(1).date(2);
        lectureUpdateComponent.lecture().endDate = dayjs().year(2022).month(1).date(3);

        lectureUpdateComponent.onDatesValuesChanged();

        expect(setDatesSpy).toHaveBeenCalledTimes(3);
        if (lectureUpdateComponent.lecture().visibleDate && lectureUpdateComponent.lecture().startDate) {
            expect(lectureUpdateComponent.lecture().visibleDate!.toDate()).toBeBefore(lectureUpdateComponent.lecture().startDate!.toDate());
        } else {
            throw new Error('visibleDate and startDate should not be undefined');
        }

        if (lectureUpdateComponent.lecture().startDate && lectureUpdateComponent.lecture().endDate) {
            expect(lectureUpdateComponent.lecture().startDate!.toDate()).toBeBefore(lectureUpdateComponent.lecture().endDate!.toDate());
        } else {
            throw new Error('startDate and endDate should not be undefined');
        }
    }));

    describe('isChangeMadeToTitleSection', () => {
        it('should detect changes made to the title section', () => {
            lectureUpdateComponent.lecture.set({ title: 'new title', channelName: 'new channel', description: 'new description' } as Lecture);
            lectureUpdateComponent.lectureOnInit = { title: 'old title', channelName: 'old channel', description: 'old description' } as Lecture;
            expect(lectureUpdateComponent.isChangeMadeToTitleSection()).toBeTrue();

            lectureUpdateComponent.lecture.set({
                title: lectureUpdateComponent.lectureOnInit.title,
                channelName: lectureUpdateComponent.lectureOnInit.channelName,
                description: lectureUpdateComponent.lectureOnInit.description,
            } as Lecture);
            expect(lectureUpdateComponent.isChangeMadeToTitleSection()).toBeFalse();
        });

        it('should handle undefined from description properly', () => {
            lectureUpdateComponent.lecture.set({ title: 'new title', channelName: 'new channel', description: 'new description' } as Lecture);
            lectureUpdateComponent.lectureOnInit = { title: 'old title', channelName: 'old channel', description: undefined } as Lecture;
            expect(lectureUpdateComponent.isChangeMadeToTitleSection()).toBeTrue();

            lectureUpdateComponent.lecture.set({
                title: lectureUpdateComponent.lectureOnInit.title,
                channelName: lectureUpdateComponent.lectureOnInit.channelName,
                description: '', // will be an empty string if the user clears the input, but was loaded with undefined in that case
            } as Lecture);
            expect(lectureUpdateComponent.isChangeMadeToTitleSection()).toBeFalse();
        });
    });

    describe('isChangeMadeToPeriodSection', () => {
        it('should detect changes made to the period section', () => {
            lectureUpdateComponent.lecture.set({ visibleDate: dayjs().add(1, 'day'), startDate: dayjs().add(2, 'day'), endDate: dayjs().add(3, 'day') } as Lecture);
            lectureUpdateComponent.lectureOnInit = { visibleDate: dayjs(), startDate: dayjs(), endDate: dayjs() } as Lecture;
            expect(lectureUpdateComponent.isChangeMadeToPeriodSection()).toBeTrue();

            lectureUpdateComponent.lecture.set({
                visibleDate: lectureUpdateComponent.lectureOnInit.visibleDate,
                startDate: lectureUpdateComponent.lectureOnInit.startDate,
                endDate: lectureUpdateComponent.lectureOnInit.endDate,
            } as Lecture);
            expect(lectureUpdateComponent.isChangeMadeToPeriodSection()).toBeFalse();
        });

        it('should not consider resetting an undefined date as a change', () => {
            lectureUpdateComponent.lecture.set({ visibleDate: dayjs().add(1, 'day'), startDate: dayjs().add(2, 'day'), endDate: dayjs().add(3, 'day') } as Lecture);
            lectureUpdateComponent.lectureOnInit = { visibleDate: undefined, startDate: undefined, endDate: undefined } as Lecture;
            expect(lectureUpdateComponent.isChangeMadeToPeriodSection()).toBeTrue();

            lectureUpdateComponent.lecture.set({
                visibleDate: dayjs('undefined'),
                startDate: dayjs('undefined'),
                endDate: dayjs('undefined'),
            } as Lecture);
            expect(lectureUpdateComponent.isChangeMadeToPeriodSection()).toBeFalse();
        });
    });
});
