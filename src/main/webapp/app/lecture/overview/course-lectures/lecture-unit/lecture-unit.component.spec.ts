import { LectureUnitComponent } from 'app/lecture/overview/course-lectures/lecture-unit/lecture-unit.component';
import { LectureUnit } from 'app/lecture/shared/entities/lecture-unit/lectureUnit.model';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { faFile, faVideo } from '@fortawesome/free-solid-svg-icons';
import { TranslateService } from '@ngx-translate/core';
import { By } from '@angular/platform-browser';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { Router } from '@angular/router';

describe('LectureUnitComponent', () => {
    let component: LectureUnitComponent;
    let fixture: ComponentFixture<LectureUnitComponent>;
    let router: Router;

    const lectureUnit: LectureUnit = {
        id: 1,
        name: 'Test Lecture Unit',
        completed: true,
        visibleToStudents: true,
    };

    beforeEach(async () => {
        const routerMock = {
            url: '/courses/1',
        };

        await TestBed.configureTestingModule({
            imports: [LectureUnitComponent],
            providers: [
                {
                    provide: TranslateService,
                    useClass: MockTranslateService,
                },
                {
                    provide: Router,
                    useValue: routerMock,
                },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(LectureUnitComponent);
        component = fixture.componentInstance;
        router = TestBed.inject(Router);

        fixture.componentRef.setInput('courseId', 1);
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

    it('should handle original version view', async () => {
        const handleOriginalVersionViewSpy = jest.spyOn(component, 'handleOriginalVersionView');
        const onShowOriginalVersionEmitSpy = jest.spyOn(component.onShowOriginalVersion, 'emit');

        fixture.componentRef.setInput('showOriginalVersionButton', true);
        fixture.detectChanges();

        const event = new MouseEvent('click');
        const button = fixture.debugElement.query(By.css('#view-original-version-button'));

        expect(button).not.toBeNull();

        button.nativeElement.dispatchEvent(event);

        expect(handleOriginalVersionViewSpy).toHaveBeenCalledOnce();
        expect(onShowOriginalVersionEmitSpy).toHaveBeenCalledOnce();
    });

    it('should emit correct completion status when toggling completion', () => {
        fixture.detectChanges();

        // Initial state: completed = true, so toggling should emit false
        const onCompletionEmitSpy = jest.spyOn(component.onCompletion, 'emit');
        const event = new MouseEvent('click');
        event.stopPropagation = jest.fn();

        component.toggleCompletion(event);

        expect(event.stopPropagation).toHaveBeenCalled();
        expect(onCompletionEmitSpy).toHaveBeenCalledWith(false);

        // Change to not completed, toggling should emit true
        fixture.componentRef.setInput('lectureUnit', { ...lectureUnit, completed: false });
        fixture.detectChanges();

        component.toggleCompletion(event);
        expect(onCompletionEmitSpy).toHaveBeenCalledWith(true);
    });

    it('should update isCollapsed signal when toggleCollapse is called', () => {
        fixture.detectChanges();

        // Initially collapsed
        expect(component.isCollapsed()).toBeTrue();

        // Toggle to expanded
        component.toggleCollapse();
        expect(component.isCollapsed()).toBeFalse();

        // Toggle back to collapsed
        component.toggleCollapse();
        expect(component.isCollapsed()).toBeTrue();
    });

    it('should emit collapse state when toggleCollapse is called', () => {
        const onCollapseEmitSpy = jest.spyOn(component.onCollapse, 'emit');
        fixture.detectChanges();

        component.toggleCollapse();
        expect(onCollapseEmitSpy).toHaveBeenCalledWith(false);

        component.toggleCollapse();
        expect(onCollapseEmitSpy).toHaveBeenCalledWith(true);
    });

    it('should compute isVisibleToStudents correctly', () => {
        fixture.componentRef.setInput('lectureUnit', { ...lectureUnit, visibleToStudents: true });
        fixture.detectChanges();
        expect(component.isVisibleToStudents()).toBeTrue();

        fixture.componentRef.setInput('lectureUnit', { ...lectureUnit, visibleToStudents: false });
        fixture.detectChanges();
        expect(component.isVisibleToStudents()).toBeFalse();
    });

    it('should compute isStudentPath correctly when URL starts with /courses', () => {
        Object.defineProperty(router, 'url', {
            value: '/courses/1',
            writable: true,
        });
        fixture.detectChanges();
        expect(component.isStudentPath()).toBeTrue();
    });

    it('should compute isStudentPath correctly when URL does not start with /courses', () => {
        Object.defineProperty(router, 'url', {
            value: '/admin/courses',
            writable: true,
        });
        fixture.detectChanges();
        expect(component.isStudentPath()).toBeFalse();
    });

    it('should stop propagation when handleIsolatedView is called', () => {
        const onShowIsolatedEmitSpy = jest.spyOn(component.onShowIsolated, 'emit');
        const event = new MouseEvent('click');
        event.stopPropagation = jest.fn();

        component.handleIsolatedView(event);

        expect(event.stopPropagation).toHaveBeenCalled();
        expect(onShowIsolatedEmitSpy).toHaveBeenCalled();
    });

    it('should stop propagation when handleOriginalVersionView is called', () => {
        const onShowOriginalVersionEmitSpy = jest.spyOn(component.onShowOriginalVersion, 'emit');
        const event = new MouseEvent('click');
        event.stopPropagation = jest.fn();

        component.handleOriginalVersionView(event);

        expect(event.stopPropagation).toHaveBeenCalled();
        expect(onShowOriginalVersionEmitSpy).toHaveBeenCalled();
    });

    it('should use custom viewIsolatedButtonLabel when provided', () => {
        const customLabel = 'Custom Label';
        fixture.componentRef.setInput('viewIsolatedButtonLabel', customLabel);
        fixture.detectChanges();

        const button = fixture.debugElement.query(By.css('#view-isolated-button'));
        expect(button).not.toBeNull();
    });

    it('should use custom viewIsolatedButtonIcon when provided', () => {
        fixture.componentRef.setInput('viewIsolatedButtonIcon', faFile);
        fixture.detectChanges();

        const icon = fixture.debugElement.query(By.css('#view-isolated-button fa-icon'));
        expect(icon).not.toBeNull();
    });

    it('should not show completion checkbox when isPresentationMode is true', () => {
        fixture.componentRef.setInput('isPresentationMode', true);
        fixture.detectChanges();

        const checkbox = fixture.debugElement.query(By.css('#completed-checkbox'));
        expect(checkbox).toBeNull();
    });

    it('should not show completion checkbox when visibleToStudents is false', () => {
        fixture.componentRef.setInput('lectureUnit', { ...lectureUnit, visibleToStudents: false });
        fixture.detectChanges();

        const checkbox = fixture.debugElement.query(By.css('#completed-checkbox'));
        expect(checkbox).toBeNull();
    });

    it('should not show completion checkbox when completed is undefined', () => {
        fixture.componentRef.setInput('lectureUnit', { ...lectureUnit, completed: undefined });
        fixture.detectChanges();

        const checkbox = fixture.debugElement.query(By.css('#completed-checkbox'));
        expect(checkbox).toBeNull();
    });

    it('should show not released badge when visibleToStudents is false', () => {
        fixture.componentRef.setInput('lectureUnit', {
            ...lectureUnit,
            visibleToStudents: false,
            releaseDate: new Date('2024-01-01'),
        });
        fixture.detectChanges();

        const badge = fixture.debugElement.query(By.css('.badge.bg-warning'));
        expect(badge).not.toBeNull();
    });

    it('should not show original version button when isStudentPath is true', () => {
        Object.defineProperty(router, 'url', {
            value: '/courses/1',
            writable: true,
        });
        fixture.componentRef.setInput('showOriginalVersionButton', true);
        fixture.detectChanges();

        const button = fixture.debugElement.query(By.css('#view-original-version-button'));
        expect(button).toBeNull();
    });

    it('should show original version button when isStudentPath is false and showOriginalVersionButton is true', () => {
        Object.defineProperty(router, 'url', {
            value: '/admin/courses',
            writable: true,
        });
        fixture.componentRef.setInput('showOriginalVersionButton', true);
        fixture.detectChanges();

        const button = fixture.debugElement.query(By.css('#view-original-version-button'));
        expect(button).not.toBeNull();
    });

    it('should show content when not collapsed', () => {
        fixture.detectChanges();
        // Initially collapsed
        let body = fixture.debugElement.query(By.css('#lecture-unit-body'));
        expect(body).toBeNull();

        // Toggle to expanded
        component.toggleCollapse();
        fixture.detectChanges();

        body = fixture.debugElement.query(By.css('#lecture-unit-body'));
        expect(body).not.toBeNull();
    });
});
