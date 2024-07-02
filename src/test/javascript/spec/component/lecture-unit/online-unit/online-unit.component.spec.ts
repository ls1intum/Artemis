import { OnlineUnitComponent } from 'app/overview/course-lectures/online-unit/online-unit.component';
import { OnlineUnit } from 'app/entities/lecture-unit/onlineUnit.model';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClient } from '@angular/common/http';
import { ScienceService } from 'app/shared/science/science.service';
import { MockScienceService } from '../../../helpers/mocks/service/mock-science-service';
import { SafeResourceUrlPipe } from 'app/shared/pipes/safe-resource-url.pipe';

describe('OnlineUnitComponent', () => {
    let scienceService: ScienceService;

    let component: OnlineUnitComponent;
    let fixture: ComponentFixture<OnlineUnitComponent>;

    let windowSpy: jest.SpyInstance;

    const onlineUnit: OnlineUnit = {
        id: 1,
        name: 'Test Online Unit',
        content: 'Test content',
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
        windowSpy = jest.spyOn(window, 'open').mockImplementation(() => {
            return null;
        });

        fixture = TestBed.createComponent(OnlineUnitComponent);
        component = fixture.componentInstance;

        fixture.componentRef.setInput('lectureUnit', onlineUnit);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        expect(component).toBeTruthy();
    });

    it('should handle isolated view', async () => {
        const handleIsolatedViewSpy = jest.spyOn(component, 'handleIsolatedView');

        fixture.detectChanges();

        const viewIsolatedButton = fixture.debugElement.query(By.css('#view-isolated-button'));
        viewIsolatedButton.nativeElement.click();

        expect(handleIsolatedViewSpy).toHaveBeenCalledOnce();
        expect(windowSpy).toHaveBeenCalledOnce();
        expect(windowSpy).toHaveBeenCalledWith(onlineUnit.source, '_blank');
    });

    it('should log event on isolated view', () => {
        const logEventSpy = jest.spyOn(scienceService, 'logEvent');

        component.handleIsolatedView();

        expect(logEventSpy).toHaveBeenCalledOnce();
    });

    it('should call completion on isolated view', async () => {
        const onCompletionEmitSpy = jest.spyOn(component.onCompletion, 'emit');

        component.handleIsolatedView();

        expect(onCompletionEmitSpy).toHaveBeenCalledOnce();
    });
});
