import { vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DetailOverviewListComponent, DetailOverviewSection, DetailType } from 'app/shared-ui/detail-overview-list/detail-overview-list.component';
import { ModelingExerciseService } from 'app/modeling/manage/services/modeling-exercise.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { MockAlertService } from 'test/helpers/mocks/service/mock-alert.service';
import { of, throwError } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { UMLModel } from '@tumaet/apollon';
import { Detail } from 'app/shared-ui/detail-overview-list/detail.model';
import { provideRouter } from '@angular/router';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { Component, signal } from '@angular/core';
import { By } from '@angular/platform-browser';
import { DetailOverviewNavigationBarComponent } from '../detail-overview-navigation-bar/detail-overview-navigation-bar.component';

@Component({
    imports: [DetailOverviewListComponent],
    template: `
        <ng-template #tools><button type="button" (click)="actions = actions + 1">Suggest competencies</button></ng-template>
        <ng-template #administration><button type="button" (click)="resets = resets + 1">Reset</button></ng-template>
        <jhi-detail-overview-list
            [sections]="sections"
            [sectionActions]="{ 'headline.1': tools }"
            [footerSection]="canAdminister() ? { headline: 'artemisApp.exerciseDetail.administration', content: administration } : undefined"
        />
    `,
})
class SectionActionsHostComponent {
    readonly sections = sections;
    actions = 0;
    readonly canAdminister = signal(false);
    resets = 0;
}

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
                provideRouter([]),
                { provide: ProfileService, useClass: MockProfileService },
                { provide: ModelingExerciseService, useValue: { convertToPdf: vi.fn() } },
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
        expect(component.headlines()).toStrictEqual([{ id: 'headline-1', translationKey: 'headline.1' }]);
        expect(component.headlinesRecord()).toStrictEqual({ 'headline.1': 'headline-1' });
        expect(DetailOverviewListComponent).not.toBeNull();
    });

    it('places working contextual actions next to their section without hiding its details', () => {
        fixture.componentRef.setInput('sections', sections);
        const host = TestBed.createComponent(SectionActionsHostComponent);
        host.detectChanges();
        const element = host.nativeElement as HTMLElement;
        const button = element.querySelector('.detail-section-heading button') as HTMLButtonElement;

        expect(button).not.toBeNull();
        expect(element.querySelector('dl')?.textContent).toContain('text');
        button.click();
        expect(host.componentInstance.actions).toBe(1);
    });

    it('includes the permitted administration section in navigation and renders working actions below the details', () => {
        fixture.componentRef.setInput('sections', sections);
        const host = TestBed.createComponent(SectionActionsHostComponent);
        host.componentInstance.canAdminister.set(true);
        host.detectChanges();
        const element = host.nativeElement as HTMLElement;
        const heading = element.querySelector('#artemisApp-exerciseDetail-administration');
        expect(heading?.tagName).toBe('H2');
        expect(element.querySelectorAll('.detail-section-heading h2')).toHaveLength(2);
        const navigation = host.debugElement.query(By.directive(DetailOverviewNavigationBarComponent)).componentInstance as DetailOverviewNavigationBarComponent;
        expect(navigation.sectionHeadlines().at(-1)).toEqual({ id: 'artemisApp-exerciseDetail-administration', translationKey: 'artemisApp.exerciseDetail.administration' });
        const reset = element.querySelector('.section-detail-list:last-of-type button') as HTMLButtonElement;
        expect(reset.textContent).toBe('Reset');
        expect(element.querySelector('dl')!.compareDocumentPosition(reset) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
        reset.click();
        expect(host.componentInstance.resets).toBe(1);

        host.componentInstance.canAdminister.set(false);
        host.detectChanges();
        expect(element.querySelector('#artemisApp-exerciseDetail-administration')).toBeNull();
        expect(element.textContent).not.toContain('Reset');
        expect(host.debugElement.query(By.directive(DetailOverviewNavigationBarComponent))).toBeNull();
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
        const downloadSpy = vi.spyOn(modelingService, 'convertToPdf').mockReturnValue(of(new HttpResponse({ body: new Blob() })));
        component.downloadApollonDiagramAsPDf({} as UMLModel, 'title');
        expect(downloadSpy).toHaveBeenCalledOnce();
    });

    it('should error on download apollon Diagram fail', () => {
        vi.spyOn(modelingService, 'convertToPdf').mockReturnValue(throwError(() => new HttpResponse({ body: new Blob() })));
        const errorSpy = vi.spyOn(alertService, 'error');
        component.downloadApollonDiagramAsPDf({} as UMLModel, 'title');
        expect(errorSpy).toHaveBeenCalledOnce();
    });
});
