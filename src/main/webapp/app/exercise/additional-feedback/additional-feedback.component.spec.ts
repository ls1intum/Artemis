import { beforeEach, describe, expect, it } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockProvider } from 'ng-mocks';
import { AdditionalFeedbackComponent } from 'app/exercise/additional-feedback/additional-feedback.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { LocaleConversionService } from 'app/foundation/service/locale-conversion.service';

describe('AdditionalFeedbackComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<AdditionalFeedbackComponent>;
    let comp: AdditionalFeedbackComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [AdditionalFeedbackComponent],
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
