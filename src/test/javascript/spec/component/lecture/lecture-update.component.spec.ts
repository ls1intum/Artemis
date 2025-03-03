import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { By } from '@angular/platform-browser';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { Lecture } from 'app/entities/lecture.model';
import { LectureUpdateComponent } from 'app/lecture/lecture-update.component';
import { LectureService } from 'app/lecture/lecture.service';
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
import { DocumentationButtonComponent } from 'app/shared/components/documentation-button/documentation-button.component';
import { LectureTitleChannelNameComponent } from 'app/lecture/lecture-title-channel-name.component';
import { MarkdownEditorMonacoComponent } from 'app/shared/markdown-editor/monaco/markdown-editor-monaco.component';
import { CustomNotIncludedInValidatorDirective } from 'app/shared/validators/custom-not-included-in-validator.directive';
import { OwlDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import { TitleChannelNameComponent } from 'app/shared/form/title-channel-name/title-channel-name.component';
import { LectureUpdatePeriodComponent } from 'app/lecture/lecture-period/lecture-period.component';
import { LectureUnitManagementComponent } from 'app/lecture/lecture-unit/lecture-unit-management/lecture-unit-management.component';
import { FormStatusBarComponent } from 'app/forms/form-status-bar/form-status-bar.component';

import { LectureAttachmentsComponent } from 'app/lecture/lecture-attachments.component';
import { LectureUpdateUnitsComponent } from 'app/lecture/lecture-units/lecture-units.component';
import { UnitCreationCardComponent } from 'app/lecture/lecture-unit/lecture-unit-management/unit-creation-card/unit-creation-card.component';
import { signal } from '@angular/core';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';

describe('LectureUpdateComponent', () => {
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
            imports: [FormsModule, MockModule(NgbTooltipModule), MockModule(OwlDateTimeModule)],
            declarations: [
                LectureUpdateComponent,
                LectureTitleChannelNameComponent,
                TitleChannelNameComponent,
                FormDateTimePickerComponent,
                LectureAttachmentsComponent,
                LectureUpdateUnitsComponent,
                LectureUpdatePeriodComponent,
                MockComponent(LectureUnitManagementComponent),
                MockComponent(FormStatusBarComponent),
                MockComponent(MarkdownEditorMonacoComponent),
                MockComponent(DocumentationButtonComponent),
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockPipe(HtmlForMarkdownPipe),
                MockRouterLinkDirective,
                MockComponent(UnitCreationCardComponent),
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
                { provide: AccountService, useClass: MockAccountService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .compileComponents()
            .then(() => {
                lectureUpdateComponentFixture = TestBed.createComponent(LectureUpdateComponent);
                lectureUpdateComponent = lectureUpdateComponentFixture.componentInstance;

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

        expect(createSpy).toHaveBeenCalledOnce();
        expect(createSpy).toHaveBeenCalledWith({ title: 'test1', channelName: 'test1' });
    });

    it('should edit a lecture', fakeAsync(() => {
        activatedRoute.parent!.data = of({ course: { id: 1 }, lecture: { id: 6 } });
        const navigateSpy = jest.spyOn(router, 'navigate');

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

        const expectedPath = ['course-management', 1, 'lectures', 6];
        expect(navigateSpy).toHaveBeenCalledWith(expectedPath);

        expect(updateSpy).toHaveBeenCalledOnce();
        expect(updateSpy).toHaveBeenCalledWith({ id: 6, title: 'test1Updated', channelName: 'test1Updated' });
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

    describe('updateFormStatusBar', () => {
        it('should update form status bar correctly in edit mode', () => {
            lectureUpdateComponent.isEditMode.set(true);
            lectureUpdateComponent.titleSection = signal({
                titleChannelNameComponent: () => ({
                    isFormValidSignal: () => true,
                }),
            } as any);
            lectureUpdateComponent.lecturePeriodSection = signal({
                isPeriodSectionValid: () => true,
            } as any);
            lectureUpdateComponent.attachmentsSection = signal({
                isFormValid: () => true,
            } as any);
            lectureUpdateComponent.unitSection = signal({
                isUnitConfigurationValid: () => true,
            } as any);

            lectureUpdateComponent.updateFormStatusBar();

            expect(lectureUpdateComponent.formStatusSections).toEqual([
                { title: 'artemisApp.lecture.sections.title', valid: true },
                { title: 'artemisApp.lecture.sections.period', valid: true },
                { title: 'artemisApp.lecture.sections.attachments', valid: true },
                { title: 'artemisApp.lecture.sections.units', valid: true },
            ]);
        });

        it('should update form status bar correctly in create mode', () => {
            lectureUpdateComponent.isEditMode.set(false);
            lectureUpdateComponent.titleSection = signal({
                titleChannelNameComponent: () => ({
                    isFormValidSignal: () => false,
                }),
            } as any);
            lectureUpdateComponent.lecturePeriodSection = signal({
                isPeriodSectionValid: () => true,
            } as any);

            lectureUpdateComponent.updateFormStatusBar();

            expect(lectureUpdateComponent.formStatusSections).toEqual([
                { title: 'artemisApp.lecture.sections.title', valid: false },
                { title: 'artemisApp.lecture.sections.period', valid: true },
            ]);
        });

        it('should handle invalid sections correctly', () => {
            lectureUpdateComponent.isEditMode.set(true);
            lectureUpdateComponent.titleSection = signal({
                titleChannelNameComponent: () => ({
                    isFormValidSignal: () => false,
                }),
            } as any);
            lectureUpdateComponent.lecturePeriodSection = signal({
                isPeriodSectionValid: () => false,
            } as any);
            lectureUpdateComponent.attachmentsSection = signal({
                isFormValid: () => false,
            } as any);
            lectureUpdateComponent.unitSection = signal({
                isUnitConfigurationValid: () => false,
            } as any);

            lectureUpdateComponent.updateFormStatusBar();

            expect(lectureUpdateComponent.formStatusSections).toEqual([
                { title: 'artemisApp.lecture.sections.title', valid: false },
                { title: 'artemisApp.lecture.sections.period', valid: false },
                { title: 'artemisApp.lecture.sections.attachments', valid: false },
                { title: 'artemisApp.lecture.sections.units', valid: false },
            ]);
        });
    });
});
