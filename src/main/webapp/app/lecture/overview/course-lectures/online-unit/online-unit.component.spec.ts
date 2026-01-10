import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { OnlineUnitComponent } from 'app/lecture/overview/course-lectures/online-unit/online-unit.component';
import { OnlineUnit } from 'app/lecture/shared/entities/lecture-unit/onlineUnit.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClient } from '@angular/common/http';
import { ScienceService } from 'app/shared/science/science.service';
import { MockScienceService } from 'test/helpers/mocks/service/mock-science-service';
import { SafeResourceUrlPipe } from 'app/shared/pipes/safe-resource-url.pipe';

describe('OnlineUnitComponent', () => {
    setupTestBed({ zoneless: true });

    let scienceService: ScienceService;

    let component: OnlineUnitComponent;
    let fixture: ComponentFixture<OnlineUnitComponent>;

    let windowSpy: ReturnType<typeof vi.spyOn>;

    const onlineUnit: OnlineUnit = {
        id: 1,
        name: 'Test Online Unit',
        completed: true,
        visibleToStudents: true,
        source: 'https://www.google.com',
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [OnlineUnitComponent],
            providers: [
                provideHttpClient(),
                {
                    provide: TranslateService,
                    useClass: MockTranslateService,
                },
                { provide: SafeResourceUrlPipe, useClass: SafeResourceUrlPipe },
                { provide: ScienceService, useClass: MockScienceService },
            ],
        }).compileComponents();

        scienceService = TestBed.inject(ScienceService);
        windowSpy = vi.spyOn(window, 'open').mockImplementation(() => {
            return null;
        });

        fixture = TestBed.createComponent(OnlineUnitComponent);
        component = fixture.componentInstance;

        fixture.componentRef.setInput('lectureUnit', onlineUnit);
        fixture.componentRef.setInput('courseId', 1);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        expect(component).toBeTruthy();
    });

    it('should handle isolated view', async () => {
        const handleIsolatedViewSpy = vi.spyOn(component, 'handleIsolatedView');

        fixture.detectChanges();

        const viewIsolatedButton = fixture.debugElement.query(By.css('#view-isolated-button'));
        viewIsolatedButton.nativeElement.click();

        expect(handleIsolatedViewSpy).toHaveBeenCalledTimes(1);
        expect(windowSpy).toHaveBeenCalledTimes(1);
        expect(windowSpy).toHaveBeenCalledWith(onlineUnit.source, '_blank');
    });

    it('should log event on isolated view', () => {
        const logEventSpy = vi.spyOn(scienceService, 'logEvent');

        component.handleIsolatedView();

        expect(logEventSpy).toHaveBeenCalledTimes(1);
    });

    it('should call completion on isolated view', async () => {
        const onCompletionEmitSpy = vi.spyOn(component.onCompletion, 'emit');

        component.handleIsolatedView();

        expect(onCompletionEmitSpy).toHaveBeenCalledTimes(1);
    });
});
