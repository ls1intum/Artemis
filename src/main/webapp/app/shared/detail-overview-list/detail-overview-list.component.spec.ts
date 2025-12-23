import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DetailOverviewListComponent, DetailOverviewSection, DetailType } from 'app/shared/detail-overview-list/detail-overview-list.component';
import { ModelingExerciseService } from 'app/modeling/manage/services/modeling-exercise.service';
import { AlertService } from 'app/shared/service/alert.service';
import { MockAlertService } from 'test/helpers/mocks/service/mock-alert.service';
import { of, throwError } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { UMLModel } from '@ls1intum/apollon';
import { Detail } from 'app/shared/detail-overview-list/detail.model';
import { Router } from '@angular/router';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

const sections: DetailOverviewSection[] = [
    {
        headline: 'headline.1',
        details: [
            {
                type: DetailType.Text,
                title: 'text',
                data: { text: 'text' },
            },
            false,
        ],
    },
];

describe('DetailOverviewList', () => {
    let component: DetailOverviewListComponent;
    let fixture: ComponentFixture<DetailOverviewListComponent>;
    let modelingService: ModelingExerciseService;
    let alertService: AlertService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                { provide: AlertService, useClass: MockAlertService },
                { provide: Router, useClass: MockRouter },
                { provide: ProfileService, useClass: MockProfileService },
                { provide: ModelingExerciseService, useValue: { convertToPdf: jest.fn() } },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .compileComponents()
            .then(() => {
                modelingService = TestBed.inject(ModelingExerciseService);
                alertService = TestBed.inject(AlertService);
            });

        fixture = TestBed.createComponent(DetailOverviewListComponent);
        component = fixture.componentInstance;
    });

    it('should initialize and destroy', () => {
        fixture.componentRef.setInput('sections', sections);
        fixture.detectChanges();
        expect(component.headlines).toStrictEqual([{ id: 'headline-1', translationKey: 'headline.1' }]);
        expect(component.headlinesRecord).toStrictEqual({ 'headline.1': 'headline-1' });
        expect(DetailOverviewListComponent).not.toBeNull();
    });

    it('should escape all falsy values', () => {
        fixture.componentRef.setInput('sections', [
            {
                headline: 'some-section',
                details: [
                    null as any as Detail,
                    undefined,
                    false,
                    {
                        type: DetailType.Text,
                        title: 'title',
                        data: { text: 'A Title' },
                    },
                ],
            },
        ]);
        fixture.changeDetectorRef.detectChanges();
        const detailListTitleDOMElements = fixture.nativeElement.querySelectorAll('dt[id^=detail-title]');
        expect(detailListTitleDOMElements).toHaveLength(1);
        const titleDetailTitle = fixture.nativeElement.querySelector('dt[id=detail-title-title]');
        const titleDetailValue = fixture.nativeElement.querySelector('dd[id=detail-value-title]');
        expect(titleDetailTitle).toBeDefined();
        expect(titleDetailValue).toBeDefined();
        expect(titleDetailTitle.textContent).toContain('title');
        expect(titleDetailValue.textContent).toContain('A Title');
    });

    it('should download apollon Diagram', () => {
        const downloadSpy = jest.spyOn(modelingService, 'convertToPdf').mockReturnValue(of(new HttpResponse({ body: new Blob() })));
        component.downloadApollonDiagramAsPDf({} as UMLModel, 'title');
        expect(downloadSpy).toHaveBeenCalledOnce();
    });

    it('should error on download apollon Diagram fail', () => {
        jest.spyOn(modelingService, 'convertToPdf').mockReturnValue(throwError(() => new HttpResponse({ body: new Blob() })));
        const errorSpy = jest.spyOn(alertService, 'error');
        component.downloadApollonDiagramAsPDf({} as UMLModel, 'title');
        expect(errorSpy).toHaveBeenCalledOnce();
    });
});
