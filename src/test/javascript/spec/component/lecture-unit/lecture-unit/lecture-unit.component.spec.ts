import { LectureUnitComponent } from 'app/overview/course-lectures/lecture-unit/lecture-unit.component';
import { LectureUnit } from 'app/entities/lecture-unit/lectureUnit.model';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { faVideo } from '@fortawesome/free-solid-svg-icons';
import { TranslateService } from '@ngx-translate/core';
import { By } from '@angular/platform-browser';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';

describe('LectureUnitComponent', () => {
    let component: LectureUnitComponent;
    let fixture: ComponentFixture<LectureUnitComponent>;

    const lectureUnit: LectureUnit = {
        id: 1,
        name: 'Test Lecture Unit',
        content: 'Test content',
        completed: true,
        visibleToStudents: true,
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [LectureUnitComponent],
            providers: [
                {
                    provide: TranslateService,
                    useClass: MockTranslateService,
                },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(LectureUnitComponent);
        component = fixture.componentInstance;

        fixture.componentRef.setInput('lectureUnit', lectureUnit);
        fixture.componentRef.setInput('showViewIsolatedButton', true);
        fixture.componentRef.setInput('isPresentationMode', false);
        fixture.componentRef.setInput('icon', faVideo);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        expect(component).toBeTruthy();
    });

    it('should handle isolated view', async () => {
        const emitSpy = jest.spyOn(component.onShowIsolated, 'emit');
        const handleIsolatedViewSpy = jest.spyOn(component, 'handleIsolatedView');

        fixture.detectChanges();

        const viewIsolatedButton = fixture.debugElement.query(By.css('#view-isolated-button'));
        viewIsolatedButton.nativeElement.click();

        expect(handleIsolatedViewSpy).toHaveBeenCalledOnce();
        expect(emitSpy).toHaveBeenCalledOnce();
    });

    it('should toggle completion', async () => {
        const toggleCompletionSpy = jest.spyOn(component, 'toggleCompletion');
        const onCompletionEmitSpy = jest.spyOn(component.onCompletion, 'emit');

        fixture.detectChanges();

        const completedCheckbox = fixture.debugElement.query(By.css('#completed-checkbox'));
        completedCheckbox.nativeElement.click();

        expect(toggleCompletionSpy).toHaveBeenCalledOnce();
        expect(onCompletionEmitSpy).toHaveBeenCalledOnce();
    });

    it('should toggle collapse', async () => {
        const toggleCollapseSpy = jest.spyOn(component, 'toggleCollapse');
        const onCollapseEmitSpy = jest.spyOn(component.onCollapse, 'emit');

        fixture.detectChanges();

        const collapseButton = fixture.debugElement.query(By.css('#lecture-unit-toggle-button'));
        collapseButton.nativeElement.click();

        expect(toggleCollapseSpy).toHaveBeenCalledOnce();
        expect(onCollapseEmitSpy).toHaveBeenCalledOnce();
    });
});
