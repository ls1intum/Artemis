import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { LectureUnitComponent } from 'app/lecture/overview/course-lectures/lecture-unit/lecture-unit.component';
import { LectureUnit } from 'app/lecture/shared/entities/lecture-unit/lectureUnit.model';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { faVideo } from '@fortawesome/free-solid-svg-icons';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockComponent } from 'ng-mocks';
import { CompetencyContributionComponent } from 'app/atlas/shared/competency-contribution/competency-contribution.component';

describe('LectureUnitComponent', () => {
    setupTestBed({ zoneless: true });

    let component: LectureUnitComponent;
    let fixture: ComponentFixture<LectureUnitComponent>;

    const lectureUnit: LectureUnit = {
        id: 1,
        name: 'Test Lecture Unit',
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
        })
            .overrideComponent(LectureUnitComponent, {
                remove: { imports: [CompetencyContributionComponent] },
                add: { imports: [MockComponent(CompetencyContributionComponent)] },
            })
            .compileComponents();

        fixture = TestBed.createComponent(LectureUnitComponent);
        component = fixture.componentInstance;

        fixture.componentRef.setInput('courseId', 1);
        fixture.componentRef.setInput('lectureUnit', lectureUnit);
        fixture.componentRef.setInput('showViewIsolatedButton', true);
        fixture.componentRef.setInput('isPresentationMode', false);
        fixture.componentRef.setInput('icon', faVideo);
    });

    afterEach(() => {
        vi.useRealTimers();
        vi.restoreAllMocks();
    });

    it('initializes correctly', () => {
        expect(component).toBeTruthy();
    });

    it('handleIsolatedView emits and stops propagation', () => {
        const emitSpy = vi.spyOn(component.onShowIsolated, 'emit');
        const event = { stopPropagation: vi.fn() } as unknown as Event;

        component.handleIsolatedView(event);

        expect(event.stopPropagation).toHaveBeenCalled();
        expect(emitSpy).toHaveBeenCalledOnce();
    });

    it('handleOriginalVersionView emits and stops propagation', () => {
        const emitSpy = vi.spyOn(component.onShowOriginalVersion, 'emit');
        const event = { stopPropagation: vi.fn() } as unknown as Event;

        component.handleOriginalVersionView(event);

        expect(event.stopPropagation).toHaveBeenCalled();
        expect(emitSpy).toHaveBeenCalledOnce();
    });

    it('handleFullscreen emits and stops propagation', () => {
        const emitSpy = vi.spyOn(component.onFullscreen, 'emit');
        const event = { stopPropagation: vi.fn() } as unknown as Event;

        component.handleFullscreen(event);

        expect(event.stopPropagation).toHaveBeenCalled();
        expect(emitSpy).toHaveBeenCalledOnce();
    });

    it('toggleCompletion emits inverse completion state', () => {
        const emitSpy = vi.spyOn(component.onCompletion, 'emit');
        const event = { stopPropagation: vi.fn() } as unknown as Event;

        component.toggleCompletion(event);

        expect(event.stopPropagation).toHaveBeenCalled();
        expect(emitSpy).toHaveBeenCalledWith(false);
    });

    it('toggleCollapse expands and emits collapse state', async () => {
        const collapseSpy = vi.spyOn(component.onCollapse, 'emit');
        const scrollSpy = vi.fn();
        (fixture.nativeElement as any).scrollIntoView = scrollSpy;

        fixture.detectChanges();
        component.toggleCollapse();
        await fixture.whenStable();

        expect(component.isCollapsed()).toBe(false);
        expect(collapseSpy).toHaveBeenCalledWith(false);
        expect(scrollSpy).toHaveBeenCalled();
    });
});
