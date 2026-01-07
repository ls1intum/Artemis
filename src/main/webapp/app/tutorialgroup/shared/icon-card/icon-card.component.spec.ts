import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { faCircleInfo, faCoffee } from '@fortawesome/free-solid-svg-icons';
import { IconCardComponent } from 'app/tutorialgroup/shared/icon-card/icon-card.component';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('IconCardComponent', () => {
    setupTestBed({ zoneless: true });

    let component: IconCardComponent;
    let fixture: ComponentFixture<IconCardComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [IconCardComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(IconCardComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).not.toBeNull();
    });

    it('should display the default headerIcon and headline', () => {
        expect(component.headerIcon()).toBe(faCircleInfo);
        expect(component.headline()).toBe('Title');
    });

    it('should display custom headerIcon and headline when inputs are set', () => {
        fixture.componentRef.setInput('headerIcon', faCoffee);
        fixture.componentRef.setInput('headline', 'Test');

        fixture.detectChanges();

        expect(component.headerIcon()).toBe(faCoffee);
        expect(component.headline()).toBe('Test');
    });
});
