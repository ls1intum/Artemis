import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockProvider, MockPipe, MockComponent } from 'ng-mocks';
import { AdditionalFeedbackComponent } from 'app/shared/additional-feedback/additional-feedback.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { LocaleConversionService } from 'app/shared/service/locale-conversion.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

describe('AdditionalFeedbackComponent', () => {
    let fixture: ComponentFixture<AdditionalFeedbackComponent>;
    let comp: AdditionalFeedbackComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [AdditionalFeedbackComponent, MockPipe(ArtemisTranslatePipe), MockComponent(FaIconComponent)],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                MockProvider(LocaleConversionService, {
                    toLocaleString: (value: number) => {
                        return isNaN(value) ? '-' : value.toString();
                    },
                }),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(AdditionalFeedbackComponent);
                comp = fixture.componentInstance;
            });
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(comp).toBeDefined();
    });

    it('should translate points', () => {
        expect(comp.pointTranslation(1.5)).toBe('artemisApp.assessment.detail.points.many');
        expect(comp.pointTranslation(1)).toBe('artemisApp.assessment.detail.points.one');
    });
});
