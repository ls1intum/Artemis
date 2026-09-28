import { HttpErrorResponse, HttpResponse, provideHttpClient } from '@angular/common/http';
import { MarkdownDirective } from 'app/foundation/directives/markdown.directive';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { FormsModule } from '@angular/forms';
import { By } from '@angular/platform-browser';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { LectureCreationMode, LectureUpdateComponent } from 'app/lecture/manage/lecture-update/lecture-update.component';
import { Course, CourseInformationSharingConfiguration } from 'app/course/shared/entities/course.model';
import { LectureService } from 'app/lecture/manage/services/lecture.service';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import dayjs from 'dayjs/esm';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { Subject, of, throwError } from 'rxjs';
import { TumAetUiConfirmationService } from '@tumaet/ui-angular';
import { CourseTitleBarService } from 'app/course/shared/services/course-title-bar.service';
import { MockRouterLinkDirective } from 'test/helpers/mocks/directive/mock-router-link.directive';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { DocumentationButtonComponent } from 'app/shared-ui/components/buttons/documentation-button/documentation-button.component';
import { LectureTitleChannelNameComponent } from 'app/lecture/manage/lecture-title-channel-name/lecture-title-channel-name.component';
import { MarkdownEditorMonacoComponent } from 'app/editor/markdown-editor/monaco/markdown-editor-monaco.component';
import { LectureTimelineComponent } from 'app/lecture/manage/lecture-period/lecture-timeline.component';
import { LectureUnitManagementComponent } from 'app/lecture/manage/lecture-units/management/lecture-unit-management.component';
import { LectureUpdateUnitsComponent } from 'app/lecture/manage/lecture-units/lecture-units.component';
import { UnitCreationCardComponent } from 'app/lecture/manage/lecture-units/unit-creation-card/unit-creation-card.component';
import { signal } from '@angular/core';
import { Location } from '@angular/common';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { FontAwesomeTestingModule } from '@fortawesome/angular-fontawesome/testing';
import { CalendarService } from 'app/calendar/shared/service/calendar.service';
import { PdfDropZoneComponent } from '../pdf-drop-zone/pdf-drop-zone.component';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import { PDF_UPLOAD_CONFIRMATION_STATE_KEY, PdfUploadConfirmation } from 'app/lecture/manage/lecture-update/pdf-upload-confirmation.model';
import { MockWebsocketService } from 'test/helpers/mocks/service/mock-websocket.service';

describe('LectureUpdateComponent', () => {
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
                FontAwesomeTestingModule,
                LectureUpdateComponent,
                MockComponent(LectureTitleChannelNameComponent),
                MockComponent(LectureUpdateUnitsComponent),
                MockComponent(LectureTimelineComponent),
                MockComponent(LectureUnitManagementComponent),
                MockComponent(MarkdownEditorMonacoComponent),
                MockComponent(DocumentationButtonComponent),
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockDirective(MarkdownDirective),
                MockRouterLinkDirective,
                MockComponent(UnitCreationCardComponent),
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
                { provide: WebsocketService, useClass: MockWebsocketService },
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

    async function configureActiveRouteMockAndCompileComponents(parentData: any = { course: { id: 1 } }, navigationState: Record<string, unknown> = { existingLectures: [] }) {
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

        router = TestBed.inject(Router);
        // The page reads the navigation while it is created and the history entry when it initializes.
        vi.spyOn(router, 'currentNavigation').mockReturnValue({
            extras: { state: navigationState },
        } as any);
        TestBed.inject(Location).replaceState('/', '', navigationState);

        lectureUpdateComponentFixture = TestBed.createComponent(LectureUpdateComponent);
        lectureUpdateComponent = lectureUpdateComponentFixture.componentInstance;

        lectureService = TestBed.inject(LectureService);
        TestBed.inject(ActivatedRoute);
    }

    async function configureValidLectureUpdateForm() {
        await configureActiveRouteMockAndCompileComponents({ course: { id: 1 }, lecture: { id: 6, title: 'Test Lecture', channelName: 'test-lecture' } });
        lectureUpdateComponent.titleSection = signal({ isValid: () => true } as any);
        lectureUpdateComponent.unitSection = signal({
            isUnitConfigurationValid: () => true,
        } as any);
        lectureUpdateComponentFixture.detectChanges();
        await lectureUpdateComponentFixture.whenStable();

        lectureUpdateComponent.isPeriodValid.set(true);
        lectureUpdateComponentFixture.detectChanges();
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

        // Saving the details keeps the editor open, so the content below can still be edited.
        expect(navigateSpy).not.toHaveBeenCalled();
        expect(lectureUpdateComponent.lastSavedAt()).toBeDefined();
        expect(lectureUpdateComponent.lectureOnInit.title).toBe('test1Updated');
        expect(lectureUpdateComponent.isChangeMadeToTitleOrPeriodSection()).toBe(false);
        expect(lectureUpdateComponent.shouldDisplayDismissWarning).toBe(true);

        expect(updateSpy).toHaveBeenCalledTimes(1);
        expect(updateSpy).toHaveBeenCalledWith(expect.objectContaining({ id: 6, title: 'test1Updated', channelName: 'test1Updated' }));
        expect(refreshSpy).toHaveBeenCalledTimes(1);
    });

    it('should open the editor of a created lecture in place of the creation page', async () => {
        await configureActiveRouteMockAndCompileComponents();
        lectureUpdateComponent.isEditMode.set(false);
        lectureUpdateComponent.lecture.set({ title: 'test1', channelName: 'lecture-test1' } as Lecture);
        const navigateSpy = vi.spyOn(router, 'navigate');
        vi.spyOn(lectureService, 'create').mockReturnValue(of(new HttpResponse({ body: { id: 3, title: 'test1', course: { id: 1 } } as Lecture })));

        lectureUpdateComponent.save();
        await lectureUpdateComponentFixture.whenStable();

        expect(navigateSpy).toHaveBeenCalledWith(['course-management', 1, 'lectures', 3, 'edit'], {
            replaceUrl: true,
            state: { lectureDetailsSavedAt: expect.any(String) },
        });
        // The editor of the new lecture takes over, so the creation page is left without asking.
        expect(lectureUpdateComponent.shouldDisplayDismissWarning).toBe(false);
    });

    it('should keep its own lecture after saving and count what was typed meanwhile as unsaved', async () => {
        const course = { id: 1, courseInformationSharingConfiguration: CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING } as Course;
        await configureActiveRouteMockAndCompileComponents({ course, lecture: { id: 6, title: 'Old title', channelName: 'lecture-old-title', course } });
        lectureUpdateComponentFixture.detectChanges();
        const response = new Subject<HttpResponse<Lecture>>();
        vi.spyOn(lectureService, 'update').mockReturnValue(response);

        lectureUpdateComponent.onLectureChange({ id: 6, title: 'New title', channelName: 'lecture-new-title', course } as Lecture);
        lectureUpdateComponent.save();
        lectureUpdateComponent.onLectureChange({ id: 6, title: 'Newer title', channelName: 'lecture-newer-title', course } as Lecture);
        // The response describes the course only in part, without its communication settings.
        response.next(new HttpResponse({ body: { id: 6, title: 'New title', channelName: 'lecture-new-title', course: { id: 1 } } as Lecture }));

        expect(lectureUpdateComponent.lecture().course?.courseInformationSharingConfiguration).toBe(CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING);
        expect(lectureUpdateComponent.lecture().title).toBe('Newer title');
        expect(lectureUpdateComponent.lectureOnInit.title).toBe('New title');
        expect(lectureUpdateComponent.changedSections()).toEqual(['artemisApp.lecture.sections.title']);
    });

    it('should offer the sections in the title bar, but not while a series is created', async () => {
        await configureValidLectureUpdateForm();
        const titleBar = TestBed.inject(CourseTitleBarService);
        expect(titleBar.actionsTemplate()).toBeDefined();

        lectureUpdateComponent.isEditMode.set(false);
        lectureUpdateComponent.selectedCreateLectureOption.set(LectureCreationMode.SERIES);
        lectureUpdateComponentFixture.detectChanges();

        expect(titleBar.actionsTemplate()).toBeUndefined();
    });

    it('should not ask about the single lecture form while a series is created', async () => {
        await configureActiveRouteMockAndCompileComponents();
        lectureUpdateComponent.isEditMode.set(false);
        lectureUpdateComponent.isChangeMadeToTitleOrPeriodSection.set(true);
        expect(lectureUpdateComponent.hasUnsavedChanges()).toBe(true);

        lectureUpdateComponent.selectedCreateLectureOption.set(LectureCreationMode.SERIES);

        expect(lectureUpdateComponent.hasUnsavedChanges()).toBe(false);
    });

    it('should confirm in the editor of a created lecture when it was saved, once', async () => {
        await configureActiveRouteMockAndCompileComponents(
            { course: { id: 1 }, lecture: { id: 3, title: 'test1', course: { id: 1 } } },
            { lectureDetailsSavedAt: '2026-10-01T09:30:00.000Z', navigationId: 3 },
        );
        lectureUpdateComponentFixture.detectChanges();

        expect(lectureUpdateComponent.lastSavedAt()?.toISOString()).toBe('2026-10-01T09:30:00.000Z');
        // A reload or a later step back to this page must not show the time again.
        expect(TestBed.inject(Location).getState()).toEqual({ navigationId: 3 });
    });

    it('should close the editor of a created lecture to the lecture list, since going back could leave Artemis', async () => {
        await configureActiveRouteMockAndCompileComponents(
            { course: { id: 1 }, lecture: { id: 3, title: 'test1', course: { id: 1 } } },
            { lectureDetailsSavedAt: '2026-10-01T09:30:00.000Z' },
        );
        lectureUpdateComponentFixture.detectChanges();
        const navigateSpy = vi.spyOn(router, 'navigate');

        lectureUpdateComponent.previousState();

        expect(navigateSpy).toHaveBeenCalledWith(['course-management', '1', 'lectures']);
    });

    it('should ask again before leaving when saving failed', async () => {
        await configureActiveRouteMockAndCompileComponents({ course: { id: 1 }, lecture: { id: 6 } });
        lectureUpdateComponentFixture.detectChanges();
        vi.spyOn(lectureService, 'update').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));

        lectureUpdateComponent.save();
        await lectureUpdateComponentFixture.whenStable();

        expect(lectureUpdateComponent.isSaving()).toBe(false);
        expect(lectureUpdateComponent.shouldDisplayDismissWarning).toBe(true);
    });

    it('should mark the details as changed when the title component reports a change', async () => {
        await configureActiveRouteMockAndCompileComponents({ course: { id: 1 }, lecture: { id: 6, title: 'Old title' } });
        lectureUpdateComponentFixture.detectChanges();

        lectureUpdateComponent.onLectureChange({ id: 6, title: 'New title' } as Lecture);

        expect(lectureUpdateComponent.lecture().title).toBe('New title');
        expect(lectureUpdateComponent.isChangeMadeToTitleOrPeriodSection()).toBe(true);
        expect(lectureUpdateComponent.changedSections()).toEqual(['artemisApp.lecture.sections.title']);
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

    it('should disable saving when the timeline is invalid', async () => {
        await configureValidLectureUpdateForm();

        lectureUpdateComponent.isChangeMadeToTitleOrPeriodSection.set(true);
        lectureUpdateComponentFixture.detectChanges();

        expect(lectureUpdateComponent.saveBlockedReason()).toBeUndefined();
        const saveButton = lectureUpdateComponentFixture.debugElement.query(By.css('#save-entity')).nativeElement as HTMLButtonElement;
        expect(saveButton.getAttribute('aria-disabled')).toBe('false');

        lectureUpdateComponent.isPeriodValid.set(false);
        lectureUpdateComponentFixture.detectChanges();

        expect(saveButton.getAttribute('aria-disabled')).toBe('true');
        expect(lectureUpdateComponent.saveBlockedReason()).toBe('artemisApp.lecture.editFooter.periodInvalid');
        const saveSpy = vi.spyOn(lectureUpdateComponent, 'save');
        saveButton.click();
        expect(saveSpy).not.toHaveBeenCalled();
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
        it('should store the emitted period validity and update the period change state when dates change', async () => {
            await configureActiveRouteMockAndCompileComponents();
            lectureUpdateComponentFixture.detectChanges();
            lectureUpdateComponent.lectureOnInit = { startDate: dayjs(), endDate: dayjs().add(1, 'day') } as Lecture;
            lectureUpdateComponent.lecture.set({ startDate: dayjs().add(2, 'days'), endDate: dayjs().add(3, 'days') } as Lecture);
            const timeline = lectureUpdateComponentFixture.debugElement.query(By.directive(LectureTimelineComponent)).componentInstance as LectureTimelineComponent;

            timeline.periodValidChange.emit(false);
            timeline.datesChanged.emit();

            expect(lectureUpdateComponent.isPeriodValid()).toBe(false);
            expect(lectureUpdateComponent.isChangeMadeToTitleOrPeriodSection()).toBe(true);
            expect(lectureUpdateComponent.changedSections()).toEqual(['artemisApp.lecture.sections.period']);
        });

        it('should detect changes made to the period section', async () => {
            await configureActiveRouteMockAndCompileComponents();
            lectureUpdateComponent.lecture.set({ startDate: dayjs().add(2, 'day'), endDate: dayjs().add(3, 'day') } as Lecture);
            lectureUpdateComponent.lectureOnInit = { startDate: dayjs(), endDate: dayjs() } as Lecture;
            expect(lectureUpdateComponent.isChangeMadeToPeriodSection()).toBe(true);

            lectureUpdateComponent.lecture.set({
                startDate: lectureUpdateComponent.lectureOnInit.startDate,
                endDate: lectureUpdateComponent.lectureOnInit.endDate,
            } as Lecture);
            expect(lectureUpdateComponent.isChangeMadeToPeriodSection()).toBe(false);
        });

        it('should not consider resetting an undefined date as a change', async () => {
            await configureActiveRouteMockAndCompileComponents();
            lectureUpdateComponent.lecture.set({ startDate: dayjs().add(2, 'day'), endDate: dayjs().add(3, 'day') } as Lecture);
            lectureUpdateComponent.lectureOnInit = { startDate: undefined, endDate: undefined } as Lecture;
            expect(lectureUpdateComponent.isChangeMadeToPeriodSection()).toBe(true);

            lectureUpdateComponent.lecture.set({
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
            lectureUpdateComponent.titleSection = signal({ isValid: () => true } as any);
            lectureUpdateComponent.isPeriodValid.set(true);
            lectureUpdateComponent.unitSection = signal({
                isUnitConfigurationValid: () => true,
            } as any);

            lectureUpdateComponent.updateFormStatusBar();

            expect(lectureUpdateComponent.formStatusSections()).toEqual([
                { title: 'artemisApp.lecture.sections.title', valid: true },
                { title: 'artemisApp.lecture.sections.period', valid: true },
                { title: 'artemisApp.lecture.sections.units', valid: true },
            ]);
        });

        it('should update form status bar correctly in create mode', async () => {
            await configureActiveRouteMockAndCompileComponents();
            lectureUpdateComponent.isEditMode.set(false);
            lectureUpdateComponent.titleSection = signal({ isValid: () => false } as any);
            lectureUpdateComponent.isPeriodValid.set(true);

            lectureUpdateComponent.updateFormStatusBar();

            expect(lectureUpdateComponent.formStatusSections()).toEqual([
                { title: 'artemisApp.lecture.sections.title', valid: false },
                { title: 'artemisApp.lecture.sections.period', valid: true },
            ]);
        });

        it('should handle invalid sections correctly', async () => {
            await configureActiveRouteMockAndCompileComponents();
            lectureUpdateComponent.isEditMode.set(true);
            lectureUpdateComponent.titleSection = signal({ isValid: () => false } as any);
            lectureUpdateComponent.isPeriodValid.set(false);
            lectureUpdateComponent.unitSection = signal({
                isUnitConfigurationValid: () => false,
            } as any);

            lectureUpdateComponent.updateFormStatusBar();

            expect(lectureUpdateComponent.formStatusSections()).toEqual([
                { title: 'artemisApp.lecture.sections.title', valid: false },
                { title: 'artemisApp.lecture.sections.period', valid: false },
                { title: 'artemisApp.lecture.sections.units', valid: false },
            ]);
        });
    });

    describe('confirmDiscardChanges', () => {
        async function requestDecision() {
            await configureActiveRouteMockAndCompileComponents({ course: { id: 1 }, lecture: { id: 6, title: 'Old title' } });
            lectureUpdateComponentFixture.detectChanges();
            lectureUpdateComponent.onLectureChange({ id: 6, title: 'New title' } as Lecture);
            const confirmationService = lectureUpdateComponentFixture.debugElement.injector.get(TumAetUiConfirmationService);

            const decisions: boolean[] = [];
            lectureUpdateComponent.confirmDiscardChanges().subscribe((decision) => decisions.push(decision));
            return { decisions, request: confirmationService.request(undefined) };
        }

        it('should close a dialog nobody waits for anymore', async () => {
            await configureActiveRouteMockAndCompileComponents({ course: { id: 1 }, lecture: { id: 6, title: 'Old title' } });
            lectureUpdateComponentFixture.detectChanges();
            const confirmationService = lectureUpdateComponentFixture.debugElement.injector.get(TumAetUiConfirmationService);

            const subscription = lectureUpdateComponent.confirmDiscardChanges().subscribe();
            expect(confirmationService.request(undefined)).toBeDefined();
            subscription.unsubscribe();

            expect(confirmationService.request(undefined)).toBeUndefined();
        });

        it('should name the changed sections and discard the changes on accept', async () => {
            const { decisions, request } = await requestDecision();

            expect(request?.message).toContain('artemisApp.lecture.dismissChangesModal.message');
            expect(request?.acceptLabel).toContain('entity.action.discardChanges');
            request?.accept();

            expect(decisions).toEqual([true]);
        });

        it('should keep editing on reject', async () => {
            const { decisions, request } = await requestDecision();

            request?.reject?.();

            expect(decisions).toEqual([false]);
        });

        it('should ask before content that could not be saved is left, and name the content', async () => {
            await configureActiveRouteMockAndCompileComponents({ course: { id: 1 }, lecture: { id: 6, title: 'Old title' } });
            const hasUnsavedContent = signal(false);
            lectureUpdateComponent.unitSection = signal({ isUnitConfigurationValid: () => true, hasUnsavedContent } as any);
            lectureUpdateComponentFixture.detectChanges();
            expect(lectureUpdateComponent.hasUnsavedChanges()).toBe(false);

            hasUnsavedContent.set(true);

            expect(lectureUpdateComponent.hasUnsavedChanges()).toBe(true);
            // The footer names only the details, which its Save button saves.
            expect(lectureUpdateComponent.changedSections()).toEqual([]);
            const instantSpy = vi.spyOn(TestBed.inject(TranslateService), 'instant');
            lectureUpdateComponent.confirmDiscardChanges().subscribe();
            expect(instantSpy).toHaveBeenCalledWith('artemisApp.lecture.dismissChangesModal.message', { sections: 'artemisApp.lecture.sections.units' });
        });
    });

    describe('PDF upload confirmation', () => {
        const confirmation: PdfUploadConfirmation = { lectureCreated: true, fileNames: ['Introduction.pdf'], releaseDate: '2026-10-01T08:00:00.000Z' };

        it('should confirm the lecture created from a dropped PDF until it is dismissed', async () => {
            await configureActiveRouteMockAndCompileComponents(
                { course: { id: 1 }, lecture: { id: 6, title: 'Introduction' } },
                { [PDF_UPLOAD_CONFIRMATION_STATE_KEY]: confirmation },
            );
            lectureUpdateComponentFixture.detectChanges();

            expect(lectureUpdateComponent.pdfUploadConfirmationText()).toEqual(
                expect.objectContaining({
                    title: 'artemisApp.lecture.pdfUpload.createdTitle',
                    body: 'artemisApp.lecture.pdfUpload.createdSingle',
                    params: { title: 'Introduction', fileName: 'Introduction.pdf', count: 1 },
                    release: 'artemisApp.lecture.pdfUpload.releaseSingle',
                }),
            );
            expect(lectureUpdateComponentFixture.nativeElement.querySelector('[data-testid="pdf-upload-confirmation"]')).toBeTruthy();

            const location = TestBed.inject(Location);
            location.replaceState('/', '', { [PDF_UPLOAD_CONFIRMATION_STATE_KEY]: confirmation, navigationId: 2 });
            lectureUpdateComponent.dismissPdfUploadConfirmation();
            lectureUpdateComponentFixture.detectChanges();

            expect(lectureUpdateComponentFixture.nativeElement.querySelector('[data-testid="pdf-upload-confirmation"]')).toBeFalsy();
            // Reloading the page must not confirm the upload a second time.
            expect(location.getState()).toEqual({ navigationId: 2 });
        });

        it('should word the confirmation for several files added to an existing lecture', async () => {
            const addedFiles: PdfUploadConfirmation = { lectureCreated: false, fileNames: ['a.pdf', 'b.pdf'] };
            await configureActiveRouteMockAndCompileComponents(
                { course: { id: 1 }, lecture: { id: 6, title: 'Introduction' } },
                { [PDF_UPLOAD_CONFIRMATION_STATE_KEY]: addedFiles },
            );
            lectureUpdateComponentFixture.detectChanges();

            expect(lectureUpdateComponent.pdfUploadConfirmationText()).toEqual(
                expect.objectContaining({ title: 'artemisApp.lecture.pdfUpload.addedTitle', body: 'artemisApp.lecture.pdfUpload.addedMultiple', release: undefined }),
            );
        });
    });
});
