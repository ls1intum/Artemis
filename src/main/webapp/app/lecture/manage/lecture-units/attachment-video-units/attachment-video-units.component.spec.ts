import { MockInstance, afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Component, input } from '@angular/core';
import { AttachmentVideoUnitsComponent, SplitUnit } from 'app/lecture/manage/lecture-units/attachment-video-units/attachment-video-units.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AlertService } from 'app/foundation/service/alert.service';
import { ActivatedRoute, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AttachmentVideoUnitApi } from 'app/openapi/api/attachment-video-unit-api';
import type { LectureUnitSplitInformation } from 'app/openapi/model/lecture-unit-split-information';
import { MockRouterLinkDirective } from 'test/helpers/mocks/directive/mock-router-link.directive';
import { MockAttachmentVideoUnitApi } from 'test/helpers/mocks/service/mock-attachment-video-unit-api';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import dayjs from 'dayjs/esm';
import { HttpErrorResponse } from '@angular/common/http';
import { LectureUnitApi } from 'app/openapi/api/lecture-unit-api';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { Location } from '@angular/common';
import { By } from '@angular/platform-browser';

@Component({ selector: 'jhi-lecture-unit-layout', template: '<ng-content />', standalone: true })
class LectureUnitLayoutStubComponent {
    isLoading = input(false);
}

describe('AttachmentVideoUnitsComponent', () => {
    let attachmentVideoUnitsComponentFixture: ComponentFixture<AttachmentVideoUnitsComponent>;
    let attachmentVideoUnitsComponent: AttachmentVideoUnitsComponent;

    let attachmentVideoUnitApi: AttachmentVideoUnitApi;
    let router: Router;

    const unit1: SplitUnit = {
        unitName: 'Unit 1',
        releaseDate: dayjs().year(2022).month(3).date(5),
        startPage: 1,
        endPage: 20,
    };
    const unit2: SplitUnit = {
        unitName: 'Unit 2',
        releaseDate: dayjs().year(2022).month(3).date(5),
        startPage: 21,
        endPage: 40,
    };
    const unit3: SplitUnit = {
        unitName: 'Unit 3',
        releaseDate: dayjs().year(2022).month(3).date(5),
        startPage: 41,
        endPage: 60,
    };
    const units = [unit1, unit2, unit3];
    const numberOfPages = 60;

    beforeEach(async () => {
        TestBed.configureTestingModule({
            imports: [
                FormsModule,
                FaIconComponent,
                AttachmentVideoUnitsComponent,
                LectureUnitLayoutStubComponent,
                MockPipe(ArtemisTranslatePipe),
                MockDirective(TranslateDirective),
                MockRouterLinkDirective,
            ],
            providers: [
                MockProvider(AlertService),
                MockProvider(LectureUnitApi),
                { provide: TranslateService, useClass: MockTranslateService },
                {
                    provide: AttachmentVideoUnitApi,
                    useClass: MockAttachmentVideoUnitApi,
                },
                {
                    provide: ActivatedRoute,
                    useValue: {
                        parent: {
                            parent: {
                                paramMap: of({
                                    get: (key: string) => {
                                        switch (key) {
                                            case 'lectureId':
                                                return 1;
                                        }
                                        return null;
                                    },
                                }),
                                parent: {
                                    paramMap: of({
                                        get: (key: string) => {
                                            switch (key) {
                                                case 'courseId':
                                                    return 1;
                                            }
                                            return null;
                                        },
                                    }),
                                },
                            },
                        },
                    },
                },
            ],
        }).compileComponents();

        vi.spyOn(TestBed.inject(Router), 'currentNavigation').mockReturnValue({
            extras: {
                state: {
                    file: new File([''], 'testFile.pdf', { type: 'application/pdf' }),
                    fileName: 'testFile',
                },
            },
        } as any);

        attachmentVideoUnitsComponentFixture = TestBed.createComponent(AttachmentVideoUnitsComponent);
        attachmentVideoUnitsComponent = attachmentVideoUnitsComponentFixture.componentInstance;
        attachmentVideoUnitsComponentFixture.detectChanges();

        attachmentVideoUnitsComponent.units.set(units);
        attachmentVideoUnitsComponent.numberOfPages.set(numberOfPages);

        attachmentVideoUnitApi = TestBed.inject(AttachmentVideoUnitApi);
        router = TestBed.inject(Router);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize with remove slides key phrases empty', () => {
        expect(attachmentVideoUnitsComponent.keyphrases).toMatch('');
    });

    it('should create attachment video units', async () => {
        const lectureUnitInformation: LectureUnitSplitInformation = {
            units: units.map((unit) => ({ ...unit, releaseDate: unit.releaseDate!.toJSON() })),
            numberOfPages: numberOfPages,
            removeSlidesCommaSeparatedKeyPhrases: '',
        };
        const filename = 'filename-on-server';
        attachmentVideoUnitsComponent.filename = filename;

        const createAttachmentVideoUnitStub = vi.spyOn(attachmentVideoUnitApi, 'createAttachmentVideoUnits').mockReturnValue(of([]));
        const navigateSpy = vi.spyOn(router, 'navigate');

        attachmentVideoUnitsComponent.createAttachmentVideoUnits();
        attachmentVideoUnitsComponentFixture.detectChanges();
        expect(createAttachmentVideoUnitStub).toHaveBeenCalledWith(1, filename, lectureUnitInformation);
        expect(createAttachmentVideoUnitStub).toHaveBeenCalledTimes(1);
        expect(navigateSpy).toHaveBeenCalledTimes(1);
    });

    it('should validate valid table correctly', () => {
        expect(attachmentVideoUnitsComponent.validUnitInformation()).toBe(true);
        expect(attachmentVideoUnitsComponent.invalidUnitTableMessage()).toBeUndefined();
    });

    it('should validate valid start page', () => {
        attachmentVideoUnitsComponent.units.set([{ unitName: 'Unit 1', startPage: 0, endPage: 1 }]);
        expect(attachmentVideoUnitsComponent.validUnitInformation()).toBe(false);
        expect(attachmentVideoUnitsComponent.invalidUnitTableMessage()).toBeDefined();

        attachmentVideoUnitsComponent.units.set([{ unitName: 'Unit 1', startPage: numberOfPages + 10, endPage: 1 }]);
        expect(attachmentVideoUnitsComponent.validUnitInformation()).toBe(false);
        expect(attachmentVideoUnitsComponent.invalidUnitTableMessage()).toBeDefined();

        // @ts-ignore
        attachmentVideoUnitsComponent.units.set([{ unitName: 'Unit 1', startPage: null, endPage: 10 }]);
        expect(attachmentVideoUnitsComponent.validUnitInformation()).toBe(false);
        expect(attachmentVideoUnitsComponent.invalidUnitTableMessage()).toBeDefined();

        attachmentVideoUnitsComponent.units.set([{ unitName: 'Unit 1', startPage: 10, endPage: 1 }]);
        expect(attachmentVideoUnitsComponent.validUnitInformation()).toBe(false);
        expect(attachmentVideoUnitsComponent.invalidUnitTableMessage()).toBeDefined();
    });

    it('should validate valid end page', () => {
        attachmentVideoUnitsComponent.units.set([{ unitName: 'Unit 1', startPage: 1, endPage: numberOfPages + 10 }]);
        expect(attachmentVideoUnitsComponent.validUnitInformation()).toBe(false);
        expect(attachmentVideoUnitsComponent.invalidUnitTableMessage()).toBeDefined();

        attachmentVideoUnitsComponent.units.set([{ unitName: 'Unit 1', startPage: 1, endPage: 0 }]);
        expect(attachmentVideoUnitsComponent.validUnitInformation()).toBe(false);
        expect(attachmentVideoUnitsComponent.invalidUnitTableMessage()).toBeDefined();

        // @ts-ignore
        attachmentVideoUnitsComponent.units.set([{ unitName: 'Unit 1', startPage: 2, endPage: null }]);
        expect(attachmentVideoUnitsComponent.validUnitInformation()).toBe(false);
        expect(attachmentVideoUnitsComponent.invalidUnitTableMessage()).toBeDefined();
    });

    it('should add row to table and delete row from table only if there are more then 1 rows in table', () => {
        attachmentVideoUnitsComponent.units.set([{ unitName: '', startPage: 0, endPage: 0 }]);
        attachmentVideoUnitsComponent.addRow();
        expect(attachmentVideoUnitsComponent.units()).toHaveLength(2);
        attachmentVideoUnitsComponent.deleteRow(0);
        expect(attachmentVideoUnitsComponent.units()).toHaveLength(1);
        expect(attachmentVideoUnitsComponent.deleteRow(0)).toBe(false);

        expect(attachmentVideoUnitsComponent.validUnitInformation()).toBe(false);
        expect(attachmentVideoUnitsComponent.invalidUnitTableMessage()).toBeDefined();
    });

    it('should navigate to previous state', async () => {
        attachmentVideoUnitsComponentFixture.detectChanges();

        // ensure the method has valid data
        attachmentVideoUnitsComponent.courseId = 42;
        attachmentVideoUnitsComponent.lectureId = 1;

        // stub navigate so no real routing happens
        const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true as any);

        const cancelSpy = vi.spyOn(attachmentVideoUnitsComponent, 'cancelSplit');
        attachmentVideoUnitsComponent.cancelSplit();
        expect(cancelSpy).toHaveBeenCalledTimes(1);
        expect(navigateSpy).toHaveBeenCalledTimes(1);
    });

    it('should get slides to remove', async () => {
        const expectedSlideIndexes = [1, 2, 3];
        // slide indexes are increased by 1 for display in the client
        const expectedSlideNumbers = expectedSlideIndexes.map((n) => n + 1);
        attachmentVideoUnitsComponent.searchTerm = 'key, phrases';
        const getSlidesToRemoveSpy = vi.spyOn(attachmentVideoUnitApi, 'getSlidesToRemove').mockReturnValue(of(expectedSlideIndexes));
        await new Promise((resolve) => setTimeout(resolve, 1000));
        expect(getSlidesToRemoveSpy).toHaveBeenCalledTimes(1);
        expect(attachmentVideoUnitsComponent.removedSlidesNumbers()).toEqual(expectedSlideNumbers);
    });

    it('should not get slides to remove if query is empty', async () => {
        attachmentVideoUnitsComponent.removedSlidesNumbers.set([1, 2, 3]);
        attachmentVideoUnitsComponent.searchTerm = '';
        const getSlidesToRemoveSpy = vi.spyOn(attachmentVideoUnitApi, 'getSlidesToRemove');
        await new Promise((resolve) => setTimeout(resolve, 1000));
        expect(getSlidesToRemoveSpy).not.toHaveBeenCalled();
        expect(attachmentVideoUnitsComponent.removedSlidesNumbers()).toEqual([]);
    });

    it('should start uploading file again after timeout', async () => {
        vi.useFakeTimers();

        const uploadSlidesSpy = vi.spyOn(attachmentVideoUnitApi, 'uploadSlidesForProcessing').mockReturnValue(of('filename-on-server'));
        attachmentVideoUnitApi.getAttachmentVideoUnitsData = vi.fn().mockReturnValue(of({ units: [], numberOfPages: 1, removeSlidesCommaSeparatedKeyPhrases: '' }));
        attachmentVideoUnitsComponent.ngOnInit();
        attachmentVideoUnitsComponentFixture.detectChanges();

        expect(uploadSlidesSpy).toHaveBeenCalledTimes(1);

        // Advance time by the timeout duration (MINUTES_UNTIL_DELETION minutes)
        await vi.advanceTimersByTimeAsync(1000 * 60 * attachmentVideoUnitsComponent.MINUTES_UNTIL_DELETION);

        expect(uploadSlidesSpy).toHaveBeenCalledTimes(2);

        vi.useRealTimers();
    });

    it('should say how many sections Artemis found and offer to create that many items', () => {
        vi.spyOn(attachmentVideoUnitApi, 'getAttachmentVideoUnitsData').mockReturnValue(
            of({ units: units.map((unit) => ({ ...unit, releaseDate: unit.releaseDate!.toJSON() })), numberOfPages, removeSlidesCommaSeparatedKeyPhrases: '' }),
        );

        attachmentVideoUnitsComponent.ngOnInit();
        attachmentVideoUnitsComponentFixture.detectChanges();

        expect(attachmentVideoUnitsComponent.foundSections()).toBe(3);
        expect(attachmentVideoUnitsComponent.summaryKey()).toBe('artemisApp.attachmentVideoUnit.createAttachmentVideoUnits.split.summary');
        expect(attachmentVideoUnitsComponent.createLabelKey()).toBe('artemisApp.attachmentVideoUnit.createAttachmentVideoUnits.create');
        // The result is a message of the page, not part of its description.
        expect(attachmentVideoUnitsComponentFixture.debugElement.query(By.css('[data-testid="split-summary"]')).componentInstance.severity()).toBe('info');
        expect(attachmentVideoUnitsComponentFixture.nativeElement.querySelector('[data-testid="split-create"]')).not.toBeNull();
        // How the sections are found is explained next to the proposal, and nothing warns about a missing outline.
        expect(attachmentVideoUnitsComponentFixture.nativeElement.querySelector('[data-testid="split-detection-hint"]')).not.toBeNull();
    });

    it('should turn the release dates of the proposal into dates for the date pickers', () => {
        const proposal: LectureUnitSplitInformation = {
            units: [{ unitName: 'Introduction', releaseDate: '2026-10-01T08:00:00Z', startPage: 1, endPage: 3 }],
            numberOfPages: 9,
            removeSlidesCommaSeparatedKeyPhrases: '',
        };
        vi.spyOn(attachmentVideoUnitApi, 'getAttachmentVideoUnitsData').mockReturnValue(of(proposal));

        attachmentVideoUnitsComponent.ngOnInit();
        attachmentVideoUnitsComponentFixture.detectChanges();

        const releaseDate = attachmentVideoUnitsComponent.units()[0].releaseDate;
        expect(dayjs.isDayjs(releaseDate)).toBe(true);
        expect(releaseDate!.toISOString()).toBe('2026-10-01T08:00:00.000Z');
        expect(attachmentVideoUnitsComponentFixture.nativeElement.querySelector('[data-testid="split-create"]')).not.toBeNull();
    });

    it('should still give the number of slides and a neutral label when Artemis found no sections', () => {
        attachmentVideoUnitsComponent.foundSections.set(0);
        attachmentVideoUnitsComponent.units.set([]);
        attachmentVideoUnitsComponent.numberOfPages.set(22);

        expect(attachmentVideoUnitsComponent.summaryKey()).toBe('artemisApp.attachmentVideoUnit.createAttachmentVideoUnits.split.summaryNone');
        expect(attachmentVideoUnitsComponent.createLabelKey()).toBe('artemisApp.attachmentVideoUnit.createAttachmentVideoUnits.createEmpty');
        attachmentVideoUnitsComponentFixture.detectChanges();
        // Without an outline slide, the page says why nothing was proposed and what to do instead, as a warning rather than an error.
        const summary = attachmentVideoUnitsComponentFixture.debugElement.query(By.css('[data-testid="split-summary"]')).componentInstance;
        expect(summary.severity()).toBe('warn');
        expect(summary.text()).toContain('artemisApp.attachmentVideoUnit.createAttachmentVideoUnits.noUnitDetected');

        // Once the user added a row, the result is only information.
        attachmentVideoUnitsComponent.units.set([{ unitName: 'Introduction', startPage: 1, endPage: 3 } as SplitUnit]);
        attachmentVideoUnitsComponentFixture.detectChanges();
        expect(attachmentVideoUnitsComponentFixture.debugElement.query(By.css('[data-testid="split-summary"]')).componentInstance.severity()).toBe('info');
    });

    it('should not blame a missing outline slide when the user removed the proposed rows', () => {
        attachmentVideoUnitsComponent.foundSections.set(3);
        attachmentVideoUnitsComponent.units.set([]);
        attachmentVideoUnitsComponent.numberOfPages.set(22);
        attachmentVideoUnitsComponentFixture.detectChanges();

        expect(attachmentVideoUnitsComponentFixture.debugElement.query(By.css('[data-testid="split-summary"]')).componentInstance.severity()).toBe('info');
    });

    it('should show no result while the PDF is still being read', () => {
        attachmentVideoUnitsComponent.numberOfPages.set(0);
        attachmentVideoUnitsComponentFixture.detectChanges();

        expect(attachmentVideoUnitsComponentFixture.nativeElement.querySelector('[data-testid="split-summary"]')).toBeNull();
    });

    it('should not blame a missing outline slide when the proposal could not be loaded', () => {
        vi.spyOn(attachmentVideoUnitApi, 'getAttachmentVideoUnitsData').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
        // A page that opens has neither items nor a number of slides until the proposal arrives.
        attachmentVideoUnitsComponent.units.set([]);
        attachmentVideoUnitsComponent.numberOfPages.set(undefined!);

        attachmentVideoUnitsComponent.ngOnInit();
        attachmentVideoUnitsComponentFixture.detectChanges();

        expect(attachmentVideoUnitsComponentFixture.nativeElement.querySelector('[data-testid="split-summary"]')).toBeNull();
    });

    it('should let the user try again when the items cannot be created', () => {
        attachmentVideoUnitsComponent.filename = 'filename-on-server';
        vi.spyOn(attachmentVideoUnitApi, 'createAttachmentVideoUnits').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 400 })));
        const navigateSpy = vi.spyOn(router, 'navigate');

        attachmentVideoUnitsComponent.createAttachmentVideoUnits();

        expect(attachmentVideoUnitsComponent.isLoading()).toBe(false);
        expect(navigateSpy).not.toHaveBeenCalled();
    });

    describe('when the lecture editor opened the page', () => {
        let navigateSpy: MockInstance<Router['navigate']>;
        let deleteSpy: MockInstance<LectureUnitApi['deleteLectureUnit']>;
        let createSpy: MockInstance<AttachmentVideoUnitApi['createAttachmentVideoUnits']>;
        let backSpy: MockInstance<Location['back']>;
        let replaceStateSpy: MockInstance<Location['replaceState']>;

        function open(state: Record<string, unknown>, fromHistory = false) {
            const fullState = { file: new File(['%PDF'], 'Slides.pdf', { type: 'application/pdf' }), fileName: 'Slides.pdf', returnToEditor: true, ...state };
            if (fromHistory) {
                // After a reload, the router no longer passes the state, but the history entry still holds it.
                vi.spyOn(router, 'currentNavigation').mockReturnValue(null);
                vi.spyOn(TestBed.inject(Location), 'getState').mockReturnValue(fullState);
            } else {
                vi.spyOn(router, 'currentNavigation').mockReturnValue({ extras: { state: fullState } } as any);
            }
            attachmentVideoUnitsComponentFixture = TestBed.createComponent(AttachmentVideoUnitsComponent);
            attachmentVideoUnitsComponent = attachmentVideoUnitsComponentFixture.componentInstance;
            attachmentVideoUnitsComponentFixture.detectChanges();
            attachmentVideoUnitsComponent.units.set(units);
            attachmentVideoUnitsComponent.numberOfPages.set(numberOfPages);
            attachmentVideoUnitsComponent.filename = 'filename-on-server';
            // The route stub only has the lecture id where the component reads both.
            attachmentVideoUnitsComponent.courseId = 1;
            attachmentVideoUnitsComponentFixture.detectChanges();
        }

        beforeEach(() => {
            deleteSpy = vi.spyOn(TestBed.inject(LectureUnitApi), 'deleteLectureUnit').mockReturnValue(of(undefined));
            createSpy = vi.spyOn(attachmentVideoUnitApi, 'createAttachmentVideoUnits');
            navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);
            backSpy = vi.spyOn(TestBed.inject(Location), 'back').mockImplementation(() => {});
            replaceStateSpy = vi.spyOn(TestBed.inject(Location), 'replaceState').mockImplementation(() => {});
        });

        function expectBackToEditor() {
            // Going back leaves the editor in the history once, so its Close leaves at once.
            expect(backSpy).toHaveBeenCalledOnce();
            expect(navigateSpy).not.toHaveBeenCalled();
        }

        it('should remove the PDF item by default once its sections are items and go back to the editor', () => {
            open({ sourceUnit: { id: 9, name: 'Slides' } });

            expect(attachmentVideoUnitsComponentFixture.nativeElement.querySelector('[data-testid="split-remove-source"]')).not.toBeNull();
            expect(attachmentVideoUnitsComponent.removeSourceUnit()).toBe(true);
            attachmentVideoUnitsComponent.createAttachmentVideoUnits();

            expect(deleteSpy).toHaveBeenCalledExactlyOnceWith(9, 1);
            expect(createSpy.mock.invocationCallOrder[0]).toBeLessThan(deleteSpy.mock.invocationCallOrder[0]);
            expectBackToEditor();
        });

        it('should keep the PDF item when the user chooses so', () => {
            open({ sourceUnit: { id: 9, name: 'Slides' } });

            attachmentVideoUnitsComponent.removeSourceUnit.set(false);
            attachmentVideoUnitsComponent.createAttachmentVideoUnits();

            expect(deleteSpy).not.toHaveBeenCalled();
            expectBackToEditor();
        });

        it('should drop the file from the history entry of the finished split, so Forward does not offer to create the items again', () => {
            open({ sourceUnit: { id: 9, name: 'Slides' } });

            attachmentVideoUnitsComponent.createAttachmentVideoUnits();

            expect(replaceStateSpy).toHaveBeenCalledExactlyOnceWith(expect.any(String), '', { returnToEditor: true });
            expect(replaceStateSpy.mock.invocationCallOrder[0]).toBeLessThan(backSpy.mock.invocationCallOrder[0]);
        });

        it('should go back to the editor and report it when the PDF item cannot be removed', () => {
            open({ sourceUnit: { id: 9, name: 'Slides' } });
            deleteSpy.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));
            const errorSpy = vi.spyOn(TestBed.inject(AlertService), 'error');

            attachmentVideoUnitsComponent.createAttachmentVideoUnits();

            expect(errorSpy).toHaveBeenCalledExactlyOnceWith('error.http.403');
            expectBackToEditor();
            expect(attachmentVideoUnitsComponent.isLoading()).toBe(false);
        });

        it('should not offer to remove anything for a PDF that is no item of the lecture', () => {
            open({});

            expect(attachmentVideoUnitsComponentFixture.nativeElement.querySelector('[data-testid="split-remove-source"]')).toBeNull();
            attachmentVideoUnitsComponent.createAttachmentVideoUnits();

            expect(deleteSpy).not.toHaveBeenCalled();
            expectBackToEditor();
        });

        it('should keep the PDF item and stay when the items cannot be created', () => {
            open({ sourceUnit: { id: 9, name: 'Slides' } });
            createSpy.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 400 })));

            attachmentVideoUnitsComponent.createAttachmentVideoUnits();

            expect(deleteSpy).not.toHaveBeenCalled();
            expect(navigateSpy).not.toHaveBeenCalled();
            expect(backSpy).not.toHaveBeenCalled();
            expect(attachmentVideoUnitsComponent.isLoading()).toBe(false);
        });

        it('should go back to the editor on Cancel', () => {
            open({});

            attachmentVideoUnitsComponent.cancelSplit();

            expectBackToEditor();
            // Nothing was created, so the split may be opened again with Forward.
            expect(replaceStateSpy).not.toHaveBeenCalled();
        });

        it('should continue the split with the file of the history entry after a reload', () => {
            const uploadSpy = vi.spyOn(attachmentVideoUnitApi, 'uploadSlidesForProcessing');
            const warningSpy = vi.spyOn(TestBed.inject(AlertService), 'warning');

            open({ sourceUnit: { id: 9, name: 'Slides' } }, true);

            expect(warningSpy).not.toHaveBeenCalled();
            expect(uploadSpy).toHaveBeenCalledWith(1, expect.objectContaining({ name: 'Slides.pdf' }));
            expect(attachmentVideoUnitsComponentFixture.nativeElement.querySelector('[data-testid="split-remove-source"]')).not.toBeNull();
            attachmentVideoUnitsComponent.cancelSplit();
            expectBackToEditor();
        });

        it('should lead to the lecture editor when the page was opened without a file', () => {
            vi.spyOn(router, 'currentNavigation').mockReturnValue(null);
            vi.spyOn(TestBed.inject(Location), 'getState').mockReturnValue(undefined);
            const warningSpy = vi.spyOn(TestBed.inject(AlertService), 'warning');

            attachmentVideoUnitsComponentFixture = TestBed.createComponent(AttachmentVideoUnitsComponent);
            attachmentVideoUnitsComponent = attachmentVideoUnitsComponentFixture.componentInstance;
            attachmentVideoUnitsComponentFixture.detectChanges();

            expect(warningSpy).toHaveBeenCalledExactlyOnceWith('artemisApp.attachmentVideoUnit.createAttachmentVideoUnits.noFile');
            expect(attachmentVideoUnitsComponent.isLoading()).toBe(false);
            // The route stub only has the lecture id where the component reads both, so the course id is 0 here.
            expect(navigateSpy).toHaveBeenCalledExactlyOnceWith(['course-management', '0', 'lectures', '1', 'edit'], { replaceUrl: true });
            expect(backSpy).not.toHaveBeenCalled();
        });
    });
});
