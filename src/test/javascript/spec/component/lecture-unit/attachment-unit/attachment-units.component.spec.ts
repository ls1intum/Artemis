import { Component, Input } from '@angular/core';
import { AttachmentUnitsComponent, LectureUnitInformationDTO } from 'app/lecture/lecture-unit/lecture-unit-management/attachment-units/attachment-units.component';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ComponentFixture, TestBed, discardPeriodicTasks, fakeAsync, tick } from '@angular/core/testing';
import { AlertService } from 'app/core/util/alert.service';
import { ActivatedRoute, Router } from '@angular/router';
import { of } from 'rxjs';
import { AttachmentUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/attachmentUnit.service';
import { MockRouterLinkDirective } from '../../../helpers/mocks/directive/mock-router-link.directive';
import { MockAttachmentUnitsService } from '../../../helpers/mocks/service/mock-attachment-units.service';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { ArtemisTestModule } from '../../../test.module';
import { MockComponent, MockDirective, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import dayjs from 'dayjs/esm';
import { HttpResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';

@Component({ selector: 'jhi-lecture-unit-layout', template: '<ng-content />' })
class LectureUnitLayoutStubComponent {
    @Input()
    isLoading = false;
}

type AttachmentUnitsInfoResponseType = {
    unitName: string;
    releaseDate?: dayjs.Dayjs;
    startPage: number;
    endPage: number;
};

type AttachmentUnitsResponseType = {
    units: AttachmentUnitsInfoResponseType[];
    numberOfPages: number;
};

describe('AttachmentUnitsComponent', () => {
    let attachmentUnitsComponentFixture: ComponentFixture<AttachmentUnitsComponent>;
    let attachmentUnitsComponent: AttachmentUnitsComponent;

    let attachmentUnitService: AttachmentUnitService;
    let router: Router;

    const unit1: AttachmentUnitsInfoResponseType = {
        unitName: 'Unit 1',
        releaseDate: dayjs().year(2022).month(3).date(5),
        startPage: 1,
        endPage: 20,
    };
    const unit2: AttachmentUnitsInfoResponseType = {
        unitName: 'Unit 2',
        releaseDate: dayjs().year(2022).month(3).date(5),
        startPage: 21,
        endPage: 40,
    };
    const unit3: AttachmentUnitsInfoResponseType = {
        unitName: 'Unit 3',
        releaseDate: dayjs().year(2022).month(3).date(5),
        startPage: 41,
        endPage: 60,
    };
    const units = [unit1, unit2, unit3];
    const numberOfPages = 60;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, FormsModule, MockModule(NgbTooltipModule)],
            declarations: [
                AttachmentUnitsComponent,
                LectureUnitLayoutStubComponent,
                MockComponent(FormDateTimePickerComponent),
                MockPipe(ArtemisTranslatePipe),
                MockDirective(TranslateDirective),
                MockRouterLinkDirective,
            ],
            providers: [
                MockProvider(AlertService),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AttachmentUnitService, useClass: MockAttachmentUnitsService },
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
                MockProvider(Router),
            ],
        }).compileComponents();
    });

    beforeEach(() => {
        jest.spyOn(TestBed.inject(Router), 'getCurrentNavigation').mockReturnValue({
            extras: { state: { file: new File([''], 'testFile.pdf', { type: 'application/pdf' }), fileName: 'testFile' } },
        } as any);

        attachmentUnitsComponentFixture = TestBed.createComponent(AttachmentUnitsComponent);
        attachmentUnitsComponent = attachmentUnitsComponentFixture.componentInstance;
        attachmentUnitsComponentFixture.detectChanges();

        attachmentUnitsComponent.units = units;
        attachmentUnitsComponent.numberOfPages = numberOfPages;

        attachmentUnitService = TestBed.inject(AttachmentUnitService);
        router = TestBed.get(Router);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize with remove slides key phrases empty', () => {
        expect(attachmentUnitsComponent.keyphrases).toMatch('');
    });

    it('should create attachment units', fakeAsync(() => {
        const lectureUnitInformation: LectureUnitInformationDTO = { units: units, numberOfPages: numberOfPages, removeSlidesCommaSeparatedKeyPhrases: '' };
        const filename = 'filename-on-server';
        attachmentUnitsComponent.filename = filename;

        const responseBody: AttachmentUnitsResponseType = {
            units,
            numberOfPages,
        };

        const attachmentUnitsResponse: HttpResponse<AttachmentUnitsResponseType> = new HttpResponse({
            body: responseBody,
            status: 201,
        });
        const createAttachmentUnitStub = jest.spyOn(attachmentUnitService, 'createUnits').mockReturnValue(of(attachmentUnitsResponse));
        const navigateSpy = jest.spyOn(router, 'navigate');

        attachmentUnitsComponent.createAttachmentUnits();
        attachmentUnitsComponentFixture.detectChanges();
        expect(createAttachmentUnitStub).toHaveBeenCalledWith(1, filename, lectureUnitInformation);
        expect(createAttachmentUnitStub).toHaveBeenCalledOnce();
        expect(navigateSpy).toHaveBeenCalledOnce();
    }));

    it('should validate valid table correctly', () => {
        expect(attachmentUnitsComponent.validUnitInformation()).toBeTrue();
        expect(attachmentUnitsComponent.invalidUnitTableMessage).toBeUndefined();
    });

    it('should validate valid start page', () => {
        attachmentUnitsComponent.units = [{ unitName: 'Unit 1', startPage: 0, endPage: 1 }];
        expect(attachmentUnitsComponent.validUnitInformation()).toBeFalse();
        expect(attachmentUnitsComponent.invalidUnitTableMessage).toBeDefined();

        attachmentUnitsComponent.units = [{ unitName: 'Unit 1', startPage: numberOfPages + 10, endPage: 1 }];
        expect(attachmentUnitsComponent.validUnitInformation()).toBeFalse();
        expect(attachmentUnitsComponent.invalidUnitTableMessage).toBeDefined();

        // @ts-ignore
        attachmentUnitsComponent.units = [{ unitName: 'Unit 1', startPage: null, endPage: 10 }];
        expect(attachmentUnitsComponent.validUnitInformation()).toBeFalse();
        expect(attachmentUnitsComponent.invalidUnitTableMessage).toBeDefined();

        attachmentUnitsComponent.units = [{ unitName: 'Unit 1', startPage: 10, endPage: 1 }];
        expect(attachmentUnitsComponent.validUnitInformation()).toBeFalse();
        expect(attachmentUnitsComponent.invalidUnitTableMessage).toBeDefined();
    });

    it('should validate valid end page', () => {
        attachmentUnitsComponent.units = [{ unitName: 'Unit 1', startPage: 1, endPage: numberOfPages + 10 }];
        expect(attachmentUnitsComponent.validUnitInformation()).toBeFalse();
        expect(attachmentUnitsComponent.invalidUnitTableMessage).toBeDefined();

        attachmentUnitsComponent.units = [{ unitName: 'Unit 1', startPage: 1, endPage: 0 }];
        expect(attachmentUnitsComponent.validUnitInformation()).toBeFalse();
        expect(attachmentUnitsComponent.invalidUnitTableMessage).toBeDefined();

        // @ts-ignore
        attachmentUnitsComponent.units = [{ unitName: 'Unit 1', startPage: 2, endPage: null }];
        expect(attachmentUnitsComponent.validUnitInformation()).toBeFalse();
        expect(attachmentUnitsComponent.invalidUnitTableMessage).toBeDefined();
    });

    it('should add row to table and delete row from table only if there are more then 1 rows in table', () => {
        attachmentUnitsComponent.units = [{ unitName: '', startPage: 0, endPage: 0 }];
        attachmentUnitsComponent.addRow();
        expect(attachmentUnitsComponent.units).toHaveLength(2);
        attachmentUnitsComponent.deleteRow(0);
        expect(attachmentUnitsComponent.units).toHaveLength(1);
        expect(attachmentUnitsComponent.deleteRow(0)).toBeFalse();

        expect(attachmentUnitsComponent.validUnitInformation()).toBeFalse();
        expect(attachmentUnitsComponent.invalidUnitTableMessage).toBeDefined();
    });

    it('should navigate to previous state', fakeAsync(() => {
        attachmentUnitsComponentFixture.detectChanges();

        const navigateSpy = jest.spyOn(router, 'navigate');
        const previousState = jest.spyOn(attachmentUnitsComponent, 'cancelSplit');
        attachmentUnitsComponent.cancelSplit();
        expect(previousState).toHaveBeenCalledOnce();
        expect(navigateSpy).toHaveBeenCalledOnce();
    }));

    it('should get slides to remove', fakeAsync(() => {
        const expectedSlideIndexes = [1, 2, 3];
        // slide indexes are increased by 1 for display in the frontend
        const expectedSlideNumbers = expectedSlideIndexes.map((n) => n + 1);
        const expectedResponse: HttpResponse<Array<number>> = new HttpResponse({
            body: expectedSlideIndexes,
            status: 200,
        });
        attachmentUnitsComponent.searchTerm = 'key, phrases';
        const getSlidesToRemoveSpy = jest.spyOn(attachmentUnitService, 'getSlidesToRemove').mockReturnValue(of(expectedResponse));
        tick(1000);
        expect(getSlidesToRemoveSpy).toHaveBeenCalledOnce();
        expect(attachmentUnitsComponent.removedSlidesNumbers).toEqual(expectedSlideNumbers);
    }));

    it('should not get slides to remove if query is empty', fakeAsync(() => {
        attachmentUnitsComponent.removedSlidesNumbers = [1, 2, 3];
        attachmentUnitsComponent.searchTerm = '';
        const getSlidesToRemoveSpy = jest.spyOn(attachmentUnitService, 'getSlidesToRemove');
        tick(1000);
        expect(getSlidesToRemoveSpy).not.toHaveBeenCalled();
        expect(attachmentUnitsComponent.removedSlidesNumbers).toBeEmpty();
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

        const uploadSlidesSpy = jest.spyOn(attachmentUnitService, 'uploadSlidesForProcessing').mockReturnValue(of(response1));
        attachmentUnitService.getSplitUnitsData = jest.fn().mockReturnValue(of(response2));
        attachmentUnitsComponent.ngOnInit();
        attachmentUnitsComponentFixture.detectChanges();

        expect(uploadSlidesSpy).toHaveBeenCalledOnce();
        tick(1000 * 60 * attachmentUnitsComponent.MINUTES_UNTIL_DELETION);
        expect(uploadSlidesSpy).toHaveBeenCalledTimes(2);
        discardPeriodicTasks();
    }));
});
