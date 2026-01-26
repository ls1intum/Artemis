import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { FormsModule } from '@angular/forms';
import { By } from '@angular/platform-browser';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { LectureUpdateComponent } from 'app/lecture/manage/lecture-update/lecture-update.component';
import { LectureService } from 'app/lecture/manage/services/lecture.service';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import dayjs from 'dayjs/esm';
import { MockComponent, MockDirective, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { of } from 'rxjs';
import { MockRouterLinkDirective } from 'test/helpers/mocks/directive/mock-router-link.directive';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { DocumentationButtonComponent } from 'app/shared/components/buttons/documentation-button/documentation-button.component';
import { LectureTitleChannelNameComponent } from 'app/lecture/manage/lecture-title-channel-name/lecture-title-channel-name.component';
import { MarkdownEditorMonacoComponent } from 'app/shared/markdown-editor/monaco/markdown-editor-monaco.component';
import { CustomNotIncludedInValidatorDirective } from 'app/shared/validators/custom-not-included-in-validator.directive';
import { OwlDateTimeModule, OwlNativeDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import { TitleChannelNameComponent } from 'app/shared/form/title-channel-name/title-channel-name.component';
import { LectureUpdatePeriodComponent } from 'app/lecture/manage/lecture-period/lecture-period.component';
import { LectureUnitManagementComponent } from 'app/lecture/manage/lecture-units/management/lecture-unit-management.component';
import { LectureAttachmentsComponent } from 'app/lecture/manage/lecture-attachments/lecture-attachments.component';
import { LectureUpdateUnitsComponent } from 'app/lecture/manage/lecture-units/lecture-units.component';
import { UnitCreationCardComponent } from 'app/lecture/manage/lecture-units/unit-creation-card/unit-creation-card.component';
import { signal } from '@angular/core';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { FormStatusBarComponent } from 'app/shared/form/form-status-bar/form-status-bar.component';
import { FontAwesomeTestingModule } from '@fortawesome/angular-fontawesome/testing';
import { CalendarService } from 'app/core/calendar/shared/service/calendar.service';
import { PdfDropZoneComponent } from '../pdf-drop-zone/pdf-drop-zone.component';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';

describe('LectureUpdateComponent', () => {
    setupTestBed({ zoneless: true });

    let lectureService: LectureService;
    let lectureUpdateComponentFixture: ComponentFixture<LectureUpdateComponent>;
    let lectureUpdateComponent: LectureUpdateComponent;
    let router: Router;

    let pastLecture: Lecture;

    beforeEach(() => {
        // Mock scrollIntoView which is not available in the test environment
        HTMLElement.prototype.scrollIntoView = vi.fn();
        const yesterday = dayjs().subtract(1, 'day');

        pastLecture = new Lecture();
        pastLecture.id = 6;
        pastLecture.title = 'past lecture';
        pastLecture.endDate = yesterday;

        TestBed.configureTestingModule({
            imports: [
                FormsModule,
                MockModule(NgbTooltipModule),
                OwlDateTimeModule,
                OwlNativeDateTimeModule,
                FontAwesomeTestingModule,
                LectureUpdateComponent,
                MockComponent(LectureTitleChannelNameComponent),
                MockComponent(TitleChannelNameComponent),
                MockComponent(FormDateTimePickerComponent),
                MockComponent(LectureAttachmentsComponent),
                MockComponent(LectureUpdateUnitsComponent),
                MockComponent(LectureUpdatePeriodComponent),
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
                MockComponent(PdfDropZoneComponent),
            ],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: Router, useClass: MockRouter },
                { provide: AccountService, useClass: MockAccountService },
                provideHttpClient(),
                provideHttpClientTesting(),
                MockProvider(CalendarService),
                { provide: ProfileService, useClass: MockProfileService },
            ],
        });
    });

    afterEach(() => {
        // Destroy the fixture to prevent NG0953 warnings from outputs emitting after destruction
        if (lectureUpdateComponentFixture) {
            lectureUpdateComponentFixture.destroy();
        }
        vi.restoreAllMocks();
    });

    async function configureActiveRouteMockAndCompileComponents(parentData: any = { course: { id: 1 } }) {
        const activatedRouteMock = {
            parent: {
                data: of(parentData),
                paramMap: of(convertToParamMap({ courseId: '1' })),
                snapshot: {
                    paramMap: convertToParamMap({ courseId: '1' }),
                },
            },
            queryParams: of({}),
            snapshot: {
                paramMap: convertToParamMap({ courseId: '1' }),
            },
            data: of(parentData),
        };

        TestBed.overrideProvider(ActivatedRoute, { useValue: activatedRouteMock });

        await TestBed.compileComponents();

        lectureUpdateComponentFixture = TestBed.createComponent(LectureUpdateComponent);
        lectureUpdateComponent = lectureUpdateComponentFixture.componentInstance;

        lectureService = TestBed.inject(LectureService);
        router = TestBed.inject(Router);
        vi.spyOn(router, 'currentNavigation').mockReturnValue({
            extras: { state: { existingLectures: [] } },
        } as any);
        TestBed.inject(ActivatedRoute);
    }

    it('should create lecture', async () => {
        await configureActiveRouteMockAndCompileComponents();
        lectureUpdateComponent.lecture.set({ title: 'test1', channelName: 'test1' } as Lecture);

        const createSpy = vi.spyOn(lectureService, 'create').mockReturnValue(
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
        const calendarService = TestBed.inject(CalendarService);
        const refreshSpy = vi.spyOn(calendarService, 'reloadEvents');

        lectureUpdateComponent.save();
        await lectureUpdateComponentFixture.whenStable();

        expect(createSpy).toHaveBeenCalledTimes(1);
        expect(createSpy).toHaveBeenCalledWith({ title: 'test1', channelName: 'test1' });
        expect(refreshSpy).toHaveBeenCalledTimes(1);
    });

    it('should edit a lecture', async () => {
        await configureActiveRouteMockAndCompileComponents({ course: { id: 1 }, lecture: { id: 6 } });
        const navigateSpy = vi.spyOn(router, 'navigate');

        await lectureUpdateComponentFixture.whenStable();
        lectureUpdateComponent.lecture.set({ id: 6, title: 'test1Updated', channelName: 'test1Updated' } as Lecture);

        const updateSpy = vi.spyOn(lectureService, 'update').mockReturnValue(
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
        const calendarService = TestBed.inject(CalendarService);
        const refreshSpy = vi.spyOn(calendarService, 'reloadEvents');

        lectureUpdateComponent.save();
        await lectureUpdateComponentFixture.whenStable();

        const expectedPath = ['course-management', 1, 'lectures', 6];
        expect(navigateSpy).toHaveBeenCalledWith(expectedPath);

        expect(updateSpy).toHaveBeenCalledTimes(1);
        expect(updateSpy).toHaveBeenCalledWith({ id: 6, title: 'test1Updated', channelName: 'test1Updated', isTutorialLecture: false });
        expect(refreshSpy).toHaveBeenCalledTimes(1);
    });

    it('should select process units checkbox', async () => {
        await configureActiveRouteMockAndCompileComponents();
        lectureUpdateComponent.processUnitMode = false;
        const selectProcessUnit = vi.spyOn(lectureUpdateComponent, 'onSelectProcessUnit');
        lectureUpdateComponent.onSelectProcessUnit();
        expect(selectProcessUnit).toHaveBeenCalledTimes(1);
        expect(lectureUpdateComponent.processUnitMode).toBe(true);
    });

    it('should navigate to previous state', async () => {
        await configureActiveRouteMockAndCompileComponents({ course: { id: 1 }, lecture: { id: 6, title: '', course: { id: 1 } } });

        lectureUpdateComponent.ngOnInit();
        await lectureUpdateComponentFixture.whenStable();

        const navigateSpy = vi.spyOn(router, 'navigate');
        const previousState = vi.spyOn(lectureUpdateComponent, 'previousState');
        lectureUpdateComponent.previousState();

        expect(previousState).toHaveBeenCalledTimes(1);

        const expectedPath = ['course-management', '1', 'lectures', '6'];
        expect(navigateSpy).toHaveBeenCalledWith(expectedPath);
    });

    it('should create a lecture and then redirect to unit split', async () => {
        await configureActiveRouteMockAndCompileComponents();
        // Ensure ngOnInit runs first (which sets processUnitMode = false)
        lectureUpdateComponentFixture.detectChanges();
        await lectureUpdateComponentFixture.whenStable();

        lectureUpdateComponent.file = new File([''], 'testFile.pdf', { type: 'application/pdf' });
        lectureUpdateComponent.fileName = 'testFile';
        // Set processUnitMode after initialization to prevent ngOnInit from resetting it
        lectureUpdateComponent.processUnitMode = true;
        lectureUpdateComponent.lecture.set({ title: 'test1', channelName: 'test1', isTutorialLecture: false } as Lecture);
        const navigateSpy = vi.spyOn(router, 'navigate');

        const createSpy = vi.spyOn(lectureService, 'create').mockReturnValue(
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

        const proceedToUnitSplitSpy = vi.spyOn(lectureUpdateComponent, 'proceedToUnitSplit');
        lectureUpdateComponent.proceedToUnitSplit();
        await lectureUpdateComponentFixture.whenStable();

        expect(createSpy).toHaveBeenCalledTimes(1);
        expect(createSpy).toHaveBeenCalledWith({ title: 'test1', channelName: 'test1', isTutorialLecture: false });
        expect(proceedToUnitSplitSpy).toHaveBeenCalledTimes(1);
        expect(lectureUpdateComponent.processUnitMode).toBe(true);

        const expectedPath = ['course-management', 1, 'lectures', 3, 'unit-management', 'attachment-video-units', 'process'];
        expect(navigateSpy).toHaveBeenCalledWith(expectedPath, { state: { file: lectureUpdateComponent.file, fileName: lectureUpdateComponent.fileName } });
    });

    it('should call onFileChange on changed file', async () => {
        await configureActiveRouteMockAndCompileComponents();
        lectureUpdateComponent.processUnitMode = false;
        await lectureUpdateComponentFixture.whenStable();
        expect(lectureUpdateComponentFixture.debugElement.nativeElement.querySelector('#fileInput')).toBeFalsy();

        const onFileChangeStub = vi.spyOn(lectureUpdateComponent, 'onFileChange');

        const processUnit = lectureUpdateComponentFixture.debugElement.query(By.css('input[name="processUnit"]')).nativeElement;
        processUnit.checked = true;
        processUnit.dispatchEvent(new Event('change'));
        lectureUpdateComponentFixture.detectChanges();
        lectureUpdateComponent.processUnitMode = true;
        lectureUpdateComponentFixture.autoDetectChanges();
        const fileInput = lectureUpdateComponentFixture.debugElement.nativeElement.querySelector('#fileInput');
        expect(lectureUpdateComponentFixture.debugElement.nativeElement.querySelector('#fileInput')).toBeTruthy();
        fileInput.dispatchEvent(new Event('change'));
        expect(onFileChangeStub).toHaveBeenCalledTimes(1);
    });

    it('should set lecture visible date, start date and end date correctly', async () => {
        await configureActiveRouteMockAndCompileComponents({ course: { id: 1 }, lecture: { id: 6 } });

        await lectureUpdateComponentFixture.whenStable();
        lectureUpdateComponent.lecture.set({ id: 6, title: 'test1Updated' } as Lecture);

        const setDatesSpy = vi.spyOn(lectureUpdateComponent, 'onDatesValuesChanged');

        lectureUpdateComponent.lecture().visibleDate = dayjs().year(2022).month(3).date(7);
        lectureUpdateComponent.lecture().startDate = dayjs().year(2022).month(3).date(5);
        lectureUpdateComponent.lecture().endDate = dayjs().year(2022).month(3).date(1);

        lectureUpdateComponent.onDatesValuesChanged();

        expect(setDatesSpy).toHaveBeenCalledTimes(1);
        expect(lectureUpdateComponent.lecture().startDate).toEqual(lectureUpdateComponent.lecture().endDate);
        expect(lectureUpdateComponent.lecture().startDate).toEqual(lectureUpdateComponent.lecture().visibleDate);

        await lectureUpdateComponentFixture.whenStable();

        lectureUpdateComponent.lecture().startDate = undefined;
        lectureUpdateComponent.lecture().endDate = undefined;
        lectureUpdateComponent.lecture().visibleDate = undefined;

        lectureUpdateComponent.onDatesValuesChanged();

        expect(setDatesSpy).toHaveBeenCalledTimes(2);
        expect(lectureUpdateComponent.lecture().startDate).toBeUndefined();
        expect(lectureUpdateComponent.lecture().endDate).toBeUndefined();
        expect(lectureUpdateComponent.lecture().visibleDate).toBeUndefined();

        await lectureUpdateComponentFixture.whenStable();

        lectureUpdateComponent.lecture().visibleDate = dayjs().year(2022).month(1).date(1);
        lectureUpdateComponent.lecture().startDate = dayjs().year(2022).month(1).date(2);
        lectureUpdateComponent.lecture().endDate = dayjs().year(2022).month(1).date(3);

        lectureUpdateComponent.onDatesValuesChanged();

        expect(setDatesSpy).toHaveBeenCalledTimes(3);
        if (lectureUpdateComponent.lecture().visibleDate && lectureUpdateComponent.lecture().startDate) {
            expect(lectureUpdateComponent.lecture().visibleDate!.toDate() < lectureUpdateComponent.lecture().startDate!.toDate()).toBe(true);
        } else {
            throw new Error('visibleDate and startDate should not be undefined');
        }

        if (lectureUpdateComponent.lecture().startDate && lectureUpdateComponent.lecture().endDate) {
            expect(lectureUpdateComponent.lecture().startDate!.toDate() < lectureUpdateComponent.lecture().endDate!.toDate()).toBe(true);
        } else {
            throw new Error('startDate and endDate should not be undefined');
        }
    });

    describe('isChangeMadeToTitleSection', () => {
        it('should detect changes made to the title section', async () => {
            await configureActiveRouteMockAndCompileComponents();
            lectureUpdateComponent.lecture.set({ title: 'new title', channelName: 'new channel', description: 'new description' } as Lecture);
            lectureUpdateComponent.lectureOnInit = { title: 'old title', channelName: 'old channel', description: 'old description' } as Lecture;
            expect(lectureUpdateComponent.isChangeMadeToTitleSection()).toBe(true);

            lectureUpdateComponent.lecture.set({
                title: lectureUpdateComponent.lectureOnInit.title,
                channelName: lectureUpdateComponent.lectureOnInit.channelName,
                description: lectureUpdateComponent.lectureOnInit.description,
            } as Lecture);
            expect(lectureUpdateComponent.isChangeMadeToTitleSection()).toBe(false);
        });

        it('should handle undefined from description properly', async () => {
            await configureActiveRouteMockAndCompileComponents();
            lectureUpdateComponent.lecture.set({ title: 'new title', channelName: 'new channel', description: 'new description' } as Lecture);
            lectureUpdateComponent.lectureOnInit = { title: 'old title', channelName: 'old channel', description: undefined } as Lecture;
            expect(lectureUpdateComponent.isChangeMadeToTitleSection()).toBe(true);

            lectureUpdateComponent.lecture.set({
                title: lectureUpdateComponent.lectureOnInit.title,
                channelName: lectureUpdateComponent.lectureOnInit.channelName,
                description: '',
            } as Lecture);
            expect(lectureUpdateComponent.isChangeMadeToTitleSection()).toBe(false);
        });
    });

    describe('isChangeMadeToPeriodSection', () => {
        it('should detect changes made to the period section', async () => {
            await configureActiveRouteMockAndCompileComponents();
            lectureUpdateComponent.lecture.set({ visibleDate: dayjs().add(1, 'day'), startDate: dayjs().add(2, 'day'), endDate: dayjs().add(3, 'day') } as Lecture);
            lectureUpdateComponent.lectureOnInit = { visibleDate: dayjs(), startDate: dayjs(), endDate: dayjs() } as Lecture;
            expect(lectureUpdateComponent.isChangeMadeToPeriodSection()).toBe(true);

            lectureUpdateComponent.lecture.set({
                visibleDate: lectureUpdateComponent.lectureOnInit.visibleDate,
                startDate: lectureUpdateComponent.lectureOnInit.startDate,
                endDate: lectureUpdateComponent.lectureOnInit.endDate,
            } as Lecture);
            expect(lectureUpdateComponent.isChangeMadeToPeriodSection()).toBe(false);
        });

        it('should not consider resetting an undefined date as a change', async () => {
            await configureActiveRouteMockAndCompileComponents();
            lectureUpdateComponent.lecture.set({ visibleDate: dayjs().add(1, 'day'), startDate: dayjs().add(2, 'day'), endDate: dayjs().add(3, 'day') } as Lecture);
            lectureUpdateComponent.lectureOnInit = { visibleDate: undefined, startDate: undefined, endDate: undefined } as Lecture;
            expect(lectureUpdateComponent.isChangeMadeToPeriodSection()).toBe(true);

            lectureUpdateComponent.lecture.set({
                visibleDate: dayjs('undefined'),
                startDate: dayjs('undefined'),
                endDate: dayjs('undefined'),
            } as Lecture);
            expect(lectureUpdateComponent.isChangeMadeToPeriodSection()).toBe(false);
        });
    });

    describe('updateFormStatusBar', () => {
        it('should update form status bar correctly in edit mode', async () => {
            await configureActiveRouteMockAndCompileComponents();
            lectureUpdateComponent.isEditMode.set(true);
            lectureUpdateComponent.titleSection = signal({
                titleChannelNameComponent: () => ({
                    isValid: () => true,
                }),
            } as any);
            lectureUpdateComponent.lecturePeriodSection = signal({
                isPeriodSectionValid: () => true,
            } as any);
            lectureUpdateComponent.unitSection = signal({
                isUnitConfigurationValid: () => true,
            } as any);

            lectureUpdateComponent.updateFormStatusBar();

            expect(lectureUpdateComponent.formStatusSections).toEqual([
                { title: 'artemisApp.lecture.sections.title', valid: true },
                { title: 'artemisApp.lecture.sections.period', valid: true },
                { title: 'artemisApp.lecture.sections.units', valid: true },
            ]);
        });

        it('should update form status bar correctly in create mode', async () => {
            await configureActiveRouteMockAndCompileComponents();
            lectureUpdateComponent.isEditMode.set(false);
            lectureUpdateComponent.titleSection = signal({
                titleChannelNameComponent: () => ({
                    isValid: () => false,
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

        it('should handle invalid sections correctly', async () => {
            await configureActiveRouteMockAndCompileComponents();
            lectureUpdateComponent.isEditMode.set(true);
            lectureUpdateComponent.titleSection = signal({
                titleChannelNameComponent: () => ({
                    isValid: () => false,
                }),
            } as any);
            lectureUpdateComponent.lecturePeriodSection = signal({
                isPeriodSectionValid: () => false,
            } as any);
            lectureUpdateComponent.unitSection = signal({
                isUnitConfigurationValid: () => false,
            } as any);

            lectureUpdateComponent.updateFormStatusBar();

            expect(lectureUpdateComponent.formStatusSections).toEqual([
                { title: 'artemisApp.lecture.sections.title', valid: false },
                { title: 'artemisApp.lecture.sections.period', valid: false },
                { title: 'artemisApp.lecture.sections.units', valid: false },
            ]);
        });
    });
});
