import { Component, Input } from '@angular/core';
import { AttachmentUnitsComponent } from 'app/lecture/lecture-unit/lecture-unit-management/attachment-units/attachment-units.component';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
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

@Component({ selector: 'jhi-lecture-unit-layout', template: '<ng-content></ng-content>' })
class LectureUnitLayoutStubComponent {
    @Input()
    isLoading = false;
}

describe('AttachmentUnitsComponent', () => {
    let attachmentUnitsComponentFixture: ComponentFixture<AttachmentUnitsComponent>;
    let attachmentUnitsComponent: AttachmentUnitsComponent;

    let activatedRoute: ActivatedRoute;
    let router: Router;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MockModule(NgbTooltipModule)],
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

        router = TestBed.get(Router);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should add row to table and delete row from table only if there are more then 1 rows in table', () => {
        attachmentUnitsComponent.units = [{ unitName: '', startPage: 0, endPage: 0 }];
        attachmentUnitsComponent.addRow();
        expect(attachmentUnitsComponent.units).toHaveLength(2);
        attachmentUnitsComponent.deleteRow(0);
        expect(attachmentUnitsComponent.units).toHaveLength(1);
        expect(attachmentUnitsComponent.deleteRow(0)).toBeFalse();
    });

    it('should navigate to previous state', fakeAsync(() => {
        activatedRoute = TestBed.inject(ActivatedRoute);
        attachmentUnitsComponentFixture.detectChanges();

        const navigateSpy = jest.spyOn(router, 'navigate');
        const previousState = jest.spyOn(attachmentUnitsComponent, 'previousState');
        attachmentUnitsComponent.previousState();
        expect(previousState).toHaveBeenCalledOnce();

        expect(navigateSpy).toHaveBeenCalledOnce();
    }));
});
