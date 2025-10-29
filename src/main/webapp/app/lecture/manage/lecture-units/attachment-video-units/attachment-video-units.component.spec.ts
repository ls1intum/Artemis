import { Component, Input } from '@angular/core';
import { AttachmentVideoUnitsComponent, LectureUnitInformationDTO } from 'app/lecture/manage/lecture-units/attachment-video-units/attachment-video-units.component';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ComponentFixture, TestBed, discardPeriodicTasks, fakeAsync, tick } from '@angular/core/testing';
import { AlertService } from 'app/shared/service/alert.service';
import { ActivatedRoute, Router } from '@angular/router';
import { of } from 'rxjs';
import { AttachmentVideoUnitService } from 'app/lecture/manage/lecture-units/services/attachment-video-unit.service';
import { MockRouterLinkDirective } from 'test/helpers/mocks/directive/mock-router-link.directive';
import { MockAttachmentVideoUnitsService } from 'test/helpers/mocks/service/mock-attachment-video-units.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockComponent, MockDirective, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import dayjs from 'dayjs/esm';
import { HttpResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

@Component({ selector: 'jhi-lecture-unit-layout', template: '<ng-content />' })
class LectureUnitLayoutStubComponent {
    @Input()
    isLoading = false;
}

type AttachmentVideoUnitsInfoResponseType = {
    unitName: string;
    releaseDate?: dayjs.Dayjs;
    startPage: number;
    endPage: number;
};

type AttachmentVideoUnitsResponseType = {
    units: AttachmentVideoUnitsInfoResponseType[];
    numberOfPages: number;
};

describe('AttachmentVideoUnitsComponent', () => {
    let attachmentVideoUnitsComponentFixture: ComponentFixture<AttachmentVideoUnitsComponent>;
    let attachmentVideoUnitsComponent: AttachmentVideoUnitsComponent;

    let attachmentVideoUnitService: AttachmentVideoUnitService;
    let router: Router;

    const unit1: AttachmentVideoUnitsInfoResponseType = {
        unitName: 'Unit 1',
        releaseDate: dayjs().year(2022).month(3).date(5),
        startPage: 1,
        endPage: 20,
    };
    const unit2: AttachmentVideoUnitsInfoResponseType = {
        unitName: 'Unit 2',
        releaseDate: dayjs().year(2022).month(3).date(5),
        startPage: 21,
        endPage: 40,
    };
    const unit3: AttachmentVideoUnitsInfoResponseType = {
        unitName: 'Unit 3',
        releaseDate: dayjs().year(2022).month(3).date(5),
        startPage: 41,
        endPage: 60,
    };
    const units = [unit1, unit2, unit3];
    const numberOfPages = 60;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [FormsModule, MockModule(NgbTooltipModule), FaIconComponent],
            declarations: [
                AttachmentVideoUnitsComponent,
                LectureUnitLayoutStubComponent,
                MockComponent(FormDateTimePickerComponent),
                MockPipe(ArtemisTranslatePipe),
                MockDirective(TranslateDirective),
                MockRouterLinkDirective,
            ],
            providers: [
                MockProvider(AlertService),
                { provide: TranslateService, useClass: MockTranslateService },
                {
                    provide: AttachmentVideoUnitService,
                    useClass: MockAttachmentVideoUnitsService,
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
                                    },
                                }),
                                parent: {
                                    paramMap: of({
                                        get: (key: string) => {
                                            switch (key) {
                                                case 'courseId':
                                                    return 1;
                                            }
                                        },
                                    }),
                                },
                            },
                        },
                    },
                },
            ],
        }).compileComponents();

        jest.spyOn(TestBed.inject(Router), 'currentNavigation').mockReturnValue({
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

        attachmentVideoUnitsComponent.units = units;
        attachmentVideoUnitsComponent.numberOfPages = numberOfPages;

        attachmentVideoUnitService = TestBed.inject(AttachmentVideoUnitService);
        router = TestBed.inject(Router);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize with remove slides key phrases empty', () => {
        expect(attachmentVideoUnitsComponent.keyphrases).toMatch('');
    });

    it('should create attachment video units', fakeAsync(() => {
        const lectureUnitInformation: LectureUnitInformationDTO = {
            units: units,
            numberOfPages: numberOfPages,
            removeSlidesCommaSeparatedKeyPhrases: '',
        };
        const filename = 'filename-on-server';
        attachmentVideoUnitsComponent.filename = filename;

        const responseBody: AttachmentVideoUnitsResponseType = {
            units,
            numberOfPages,
        };

        const attachmentVideoUnitsResponse: HttpResponse<AttachmentVideoUnitsResponseType> = new HttpResponse({
            body: responseBody,
            status: 201,
        });
        const createAttachmentVideoUnitStub = jest.spyOn(attachmentVideoUnitService, 'createUnits').mockReturnValue(of(attachmentVideoUnitsResponse));
        const navigateSpy = jest.spyOn(router, 'navigate');

        attachmentVideoUnitsComponent.createAttachmentVideoUnits();
        attachmentVideoUnitsComponentFixture.detectChanges();
        expect(createAttachmentVideoUnitStub).toHaveBeenCalledWith(1, filename, lectureUnitInformation);
        expect(createAttachmentVideoUnitStub).toHaveBeenCalledOnce();
        expect(navigateSpy).toHaveBeenCalledOnce();
    }));

    it('should validate valid table correctly', () => {
        expect(attachmentVideoUnitsComponent.validUnitInformation()).toBeTrue();
        expect(attachmentVideoUnitsComponent.invalidUnitTableMessage).toBeUndefined();
    });

    it('should validate valid start page', () => {
        attachmentVideoUnitsComponent.units = [{ unitName: 'Unit 1', startPage: 0, endPage: 1 }];
        expect(attachmentVideoUnitsComponent.validUnitInformation()).toBeFalse();
        expect(attachmentVideoUnitsComponent.invalidUnitTableMessage).toBeDefined();

        attachmentVideoUnitsComponent.units = [{ unitName: 'Unit 1', startPage: numberOfPages + 10, endPage: 1 }];
        expect(attachmentVideoUnitsComponent.validUnitInformation()).toBeFalse();
        expect(attachmentVideoUnitsComponent.invalidUnitTableMessage).toBeDefined();

        // @ts-ignore
        attachmentVideoUnitsComponent.units = [{ unitName: 'Unit 1', startPage: null, endPage: 10 }];
        expect(attachmentVideoUnitsComponent.validUnitInformation()).toBeFalse();
        expect(attachmentVideoUnitsComponent.invalidUnitTableMessage).toBeDefined();

        attachmentVideoUnitsComponent.units = [{ unitName: 'Unit 1', startPage: 10, endPage: 1 }];
        expect(attachmentVideoUnitsComponent.validUnitInformation()).toBeFalse();
        expect(attachmentVideoUnitsComponent.invalidUnitTableMessage).toBeDefined();
    });

    it('should validate valid end page', () => {
        attachmentVideoUnitsComponent.units = [{ unitName: 'Unit 1', startPage: 1, endPage: numberOfPages + 10 }];
        expect(attachmentVideoUnitsComponent.validUnitInformation()).toBeFalse();
        expect(attachmentVideoUnitsComponent.invalidUnitTableMessage).toBeDefined();

        attachmentVideoUnitsComponent.units = [{ unitName: 'Unit 1', startPage: 1, endPage: 0 }];
        expect(attachmentVideoUnitsComponent.validUnitInformation()).toBeFalse();
        expect(attachmentVideoUnitsComponent.invalidUnitTableMessage).toBeDefined();

        // @ts-ignore
        attachmentVideoUnitsComponent.units = [{ unitName: 'Unit 1', startPage: 2, endPage: null }];
        expect(attachmentVideoUnitsComponent.validUnitInformation()).toBeFalse();
        expect(attachmentVideoUnitsComponent.invalidUnitTableMessage).toBeDefined();
    });

    it('should add row to table and delete row from table only if there are more then 1 rows in table', () => {
        attachmentVideoUnitsComponent.units = [{ unitName: '', startPage: 0, endPage: 0 }];
        attachmentVideoUnitsComponent.addRow();
        expect(attachmentVideoUnitsComponent.units).toHaveLength(2);
        attachmentVideoUnitsComponent.deleteRow(0);
        expect(attachmentVideoUnitsComponent.units).toHaveLength(1);
        expect(attachmentVideoUnitsComponent.deleteRow(0)).toBeFalse();

        expect(attachmentVideoUnitsComponent.validUnitInformation()).toBeFalse();
        expect(attachmentVideoUnitsComponent.invalidUnitTableMessage).toBeDefined();
    });

    it('should navigate to previous state', fakeAsync(() => {
        attachmentVideoUnitsComponentFixture.detectChanges();

        // ensure the method has valid data
        attachmentVideoUnitsComponent.courseId = 42;
        attachmentVideoUnitsComponent.lectureId = 1;

        // stub navigate so no real routing happens
        const navigateSpy = jest.spyOn(router, 'navigate').mockResolvedValue(true as any);

        const cancelSpy = jest.spyOn(attachmentVideoUnitsComponent, 'cancelSplit');
        attachmentVideoUnitsComponent.cancelSplit();
        expect(cancelSpy).toHaveBeenCalledOnce();
        expect(navigateSpy).toHaveBeenCalledOnce();
    }));

    it('should get slides to remove', fakeAsync(() => {
        const expectedSlideIndexes = [1, 2, 3];
        // slide indexes are increased by 1 for display in the client
        const expectedSlideNumbers = expectedSlideIndexes.map((n) => n + 1);
        const expectedResponse: HttpResponse<Array<number>> = new HttpResponse({
            body: expectedSlideIndexes,
            status: 200,
        });
        attachmentVideoUnitsComponent.searchTerm = 'key, phrases';
        const getSlidesToRemoveSpy = jest.spyOn(attachmentVideoUnitService, 'getSlidesToRemove').mockReturnValue(of(expectedResponse));
        tick(1000);
        expect(getSlidesToRemoveSpy).toHaveBeenCalledOnce();
        expect(attachmentVideoUnitsComponent.removedSlidesNumbers).toEqual(expectedSlideNumbers);
    }));

    it('should not get slides to remove if query is empty', fakeAsync(() => {
        attachmentVideoUnitsComponent.removedSlidesNumbers = [1, 2, 3];
        attachmentVideoUnitsComponent.searchTerm = '';
        const getSlidesToRemoveSpy = jest.spyOn(attachmentVideoUnitService, 'getSlidesToRemove');
        tick(1000);
        expect(getSlidesToRemoveSpy).not.toHaveBeenCalled();
        expect(attachmentVideoUnitsComponent.removedSlidesNumbers).toBeEmpty();
    }));

    it('should start uploading file again after timeout', fakeAsync(() => {
        const response1: HttpResponse<string> = new HttpResponse({
            body: 'filename-on-server',
            status: 200,
        });
        const response2: HttpResponse<LectureUnitInformationDTO> = new HttpResponse({
            body: {
                units: [],
                numberOfPages: 1,
                removeSlidesCommaSeparatedKeyPhrases: '',
            },
            status: 200,
        });

        const uploadSlidesSpy = jest.spyOn(attachmentVideoUnitService, 'uploadSlidesForProcessing').mockReturnValue(of(response1));
        attachmentVideoUnitService.getSplitUnitsData = jest.fn().mockReturnValue(of(response2));
        attachmentVideoUnitsComponent.ngOnInit();
        attachmentVideoUnitsComponentFixture.detectChanges();

        expect(uploadSlidesSpy).toHaveBeenCalledOnce();
        tick(1000 * 60 * attachmentVideoUnitsComponent.MINUTES_UNTIL_DELETION);
        expect(uploadSlidesSpy).toHaveBeenCalledTimes(2);
        discardPeriodicTasks();
    }));
});
