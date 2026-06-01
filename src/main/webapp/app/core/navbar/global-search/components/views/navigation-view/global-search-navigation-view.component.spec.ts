import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { MockComponent, MockPipe } from 'ng-mocks';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { GlobalSearchNavigationViewComponent } from './global-search-navigation-view.component';
import { GlobalSearchActionItemComponent } from 'app/core/navbar/global-search/components/action-item/global-search-action-item.component';
import { SearchView } from 'app/core/navbar/global-search/models/search-view.model';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { AccountService } from 'app/core/auth/account.service';
import { LLMSelectionDecision } from 'app/account/user/shared/dto/updateLLMSelectionDecision.dto';
import { Router } from '@angular/router';
import { SearchOverlayService } from 'app/core/navbar/global-search/services/search-overlay.service';
import { GlobalSearchResult } from 'app/openapi/model/globalSearchResult';
import { SearchResultItemComponent } from 'app/core/navbar/global-search/components/modal/search-result-item/search-result-item.component';
import { SearchableEntityItemComponent } from 'app/core/navbar/global-search/components/modal/searchable-entity-item/searchable-entity-item.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { GlobalSearchIrisAnswerComponent } from 'app/core/navbar/global-search/components/views/iris-answer/global-search-iris-answer.component';
import { IrisSearchAnswerService } from 'app/core/navbar/global-search/services/iris-search-answer.service';
import {
    faBook,
    faCalendarCheck,
    faCheckDouble,
    faFileUpload,
    faFont,
    faHashtag,
    faKeyboard,
    faProjectDiagram,
    faQuestion,
    faQuestionCircle,
} from '@fortawesome/free-solid-svg-icons';

describe('GlobalSearchNavigationViewComponent', () => {
    setupTestBed({ zoneless: true });

    let component: GlobalSearchNavigationViewComponent;
    let fixture: ComponentFixture<GlobalSearchNavigationViewComponent>;
    let router: Router;
    let overlay: SearchOverlayService;

    // jsdom does not implement scrollIntoView; mock it to prevent TypeError in the effect
    HTMLElement.prototype.scrollIntoView = vi.fn();

    function configureTestBed(irisEnabled: boolean, llmDecision: LLMSelectionDecision = LLMSelectionDecision.CLOUD_AI): void {
        TestBed.configureTestingModule({
            imports: [
                GlobalSearchNavigationViewComponent,
                MockComponent(GlobalSearchActionItemComponent),
                MockComponent(GlobalSearchIrisAnswerComponent),
                MockComponent(SearchResultItemComponent),
                MockComponent(SearchableEntityItemComponent),
                MockComponent(FaIconComponent),
                MockPipe(ArtemisTranslatePipe),
            ],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ProfileService, useValue: { isModuleFeatureActive: vi.fn().mockReturnValue(irisEnabled) } },
                { provide: AccountService, useValue: { userIdentity: signal({ selectedLLMUsage: llmDecision }) } },
                { provide: Router, useValue: { navigate: vi.fn() } },
                { provide: SearchOverlayService, useValue: { close: vi.fn(), isOpen: signal(false) } },
                { provide: IrisSearchAnswerService, useValue: { ask: vi.fn() } },
            ],
        });

        fixture = TestBed.createComponent(GlobalSearchNavigationViewComponent);
        component = fixture.componentInstance;
        router = TestBed.inject(Router);
        overlay = TestBed.inject(SearchOverlayService);
        fixture.componentRef.setInput('searchQuery', '');
        fixture.componentRef.setInput('selectedIndex', -1);
        fixture.detectChanges();
    }

    describe('when iris is enabled', () => {
        beforeEach(() => {
            vi.clearAllMocks();
            configureTestBed(true);
        });

        it('should create', () => {
            expect(component).toBeTruthy();
        });

        describe('itemCount', () => {
            it('should equal action button count plus searchable entities when not searching', () => {
                // actionButtonCount = 1 (lecture button; iris is inline), searchableEntities.length = 6
                expect(component.itemCount()).toBe(7);
            });

            it('should equal action button count plus results when searching', () => {
                fixture.componentRef.setInput('showResults', true);
                fixture.componentRef.setInput('results', [{ id: '1' }, { id: '2' }] as GlobalSearchResult[]);
                fixture.detectChanges();
                expect(component.itemCount()).toBe(3); // 1 button + 2 results
            });
        });

        describe('Keyboard navigation', () => {
            it('should emit SearchView.Lecture when Enter is pressed at index 0', () => {
                const spy = vi.fn();
                component.viewSelected.subscribe(spy);

                fixture.componentRef.setInput('selectedIndex', 0);
                fixture.detectChanges();

                const event = new KeyboardEvent('keydown', { key: 'Enter' });
                component.handleKeydown(event);

                expect(spy).toHaveBeenCalledWith(SearchView.Lecture);
            });

            it('should call preventDefault when Enter is pressed at index 0', () => {
                fixture.componentRef.setInput('selectedIndex', 0);
                fixture.detectChanges();

                const event = new KeyboardEvent('keydown', { key: 'Enter' });
                const preventDefaultSpy = vi.spyOn(event, 'preventDefault');

                component.handleKeydown(event);

                expect(preventDefaultSpy).toHaveBeenCalled();
            });

            it('should not emit when Enter is pressed at index -1', () => {
                const spy = vi.fn();
                component.viewSelected.subscribe(spy);

                fixture.componentRef.setInput('selectedIndex', -1);
                fixture.detectChanges();

                const event = new KeyboardEvent('keydown', { key: 'Enter' });
                component.handleKeydown(event);

                expect(spy).not.toHaveBeenCalled();
            });

            it('should not emit for non-Enter keys', () => {
                const spy = vi.fn();
                component.viewSelected.subscribe(spy);

                fixture.componentRef.setInput('selectedIndex', 0);
                fixture.detectChanges();

                const event = new KeyboardEvent('keydown', { key: 'ArrowDown' });
                component.handleKeydown(event);

                expect(spy).not.toHaveBeenCalled();
            });

            it('should handle Enter on first entity at index 1', () => {
                // Lecture button is at index 0; first entity starts at index 1 (Lecture(0), Entity(1))
                const spy = vi.fn();
                component.entityClick.subscribe(spy);

                fixture.componentRef.setInput('selectedIndex', 1);
                fixture.detectChanges();

                const event = new KeyboardEvent('keydown', { key: 'Enter' });
                component.handleKeydown(event);

                expect(spy).toHaveBeenCalledWith(component['searchableEntities'][0]);
            });

            it('should handle Enter on entities', () => {
                const spy = vi.fn();
                component.entityClick.subscribe(spy);

                fixture.componentRef.setInput('selectedIndex', 2); // Lecture(0), Entity(1), Entity(2)
                fixture.detectChanges();

                const event = new KeyboardEvent('keydown', { key: 'Enter' });
                component.handleKeydown(event);

                expect(spy).toHaveBeenCalledWith(component['searchableEntities'][1]);
            });

            it('should handle Enter on results', () => {
                fixture.componentRef.setInput('showResults', true);
                fixture.componentRef.setInput('results', [{ id: '123', type: 'exercise', metadata: { courseId: 1 } }] as GlobalSearchResult[]);
                fixture.componentRef.setInput('selectedIndex', 1); // Lecture(0), Result(1)
                fixture.detectChanges();

                const event = new KeyboardEvent('keydown', { key: 'Enter' });
                component.handleKeydown(event);

                expect(router.navigate).toHaveBeenCalledWith(['/courses', 1, 'exercises', '123']);
            });
        });

        describe('getIconForType', () => {
            it('should return correct icons for exercise badges', () => {
                expect(component['getIconForType']('exercise', 'programming')).toBe(faKeyboard);
                expect(component['getIconForType']('exercise', 'modeling')).toBe(faProjectDiagram);
                expect(component['getIconForType']('exercise', 'text')).toBe(faFont);
                expect(component['getIconForType']('exercise', 'File Upload')).toBe(faFileUpload);
                expect(component['getIconForType']('exercise', 'quiz')).toBe(faCheckDouble);
                expect(component['getIconForType']('exercise', 'unknown')).toBe(faQuestion);
            });

            it('should return correct icons for other types', () => {
                expect(component['getIconForType']('lecture')).toBe(faBook);
                expect(component['getIconForType']('lecture_unit')).toBe(faBook);
                expect(component['getIconForType']('channel')).toBe(faHashtag);
                expect(component['getIconForType']('faq')).toBe(faQuestionCircle);
                expect(component['getIconForType']('exam')).toBe(faCalendarCheck);
                expect(component['getIconForType']('unknown')).toBe(faQuestion);
            });
        });

        describe('navigateToResult', () => {
            it('should close overlay if courseId is missing', () => {
                component['navigateToResult']({ type: 'exercise', id: '1' } as GlobalSearchResult);
                expect(overlay.close).toHaveBeenCalled();
                expect(router.navigate).not.toHaveBeenCalled();
            });

            it('should navigate to exercise', () => {
                component['navigateToResult']({ type: 'exercise', id: '1', metadata: { courseId: 10 } } as GlobalSearchResult);
                expect(router.navigate).toHaveBeenCalledWith(['/courses', 10, 'exercises', '1']);
            });

            it('should navigate to exercise management for exam exercise when user is at least editor', () => {
                component['navigateToResult']({
                    type: 'exercise',
                    id: '42',
                    badge: 'Programming',
                    metadata: { courseId: 10, examId: 5, exerciseGroupId: 3, isAtLeastEditor: true },
                } as GlobalSearchResult);
                expect(router.navigate).toHaveBeenCalledWith(['/course-management', 10, 'exams', 5, 'exercise-groups', 3, 'programming-exercises', '42']);
            });

            it('should navigate to assessment dashboard for exam exercise when user is tutor', () => {
                component['navigateToResult']({
                    type: 'exercise',
                    id: '42',
                    badge: 'Programming',
                    metadata: { courseId: 10, examId: 5, isAtLeastTutor: true },
                } as GlobalSearchResult);
                expect(router.navigate).toHaveBeenCalledWith(['/course-management', 10, 'exams', 5, 'assessment-dashboard', '42']);
            });

            it('should navigate to exam page for exam exercise when user is a student', () => {
                component['navigateToResult']({
                    type: 'exercise',
                    id: '42',
                    metadata: { courseId: 10, examId: 5 },
                } as GlobalSearchResult);
                expect(router.navigate).toHaveBeenCalledWith(['/courses', 10, 'exams', 5]);
            });

            it('should navigate to lecture', () => {
                component['navigateToResult']({ type: 'lecture', id: '2', metadata: { courseId: 10 } } as GlobalSearchResult);
                expect(router.navigate).toHaveBeenCalledWith(['/courses', 10, 'lectures', '2']);
            });

            it('should navigate to lecture unit', () => {
                component['navigateToResult']({ type: 'lecture_unit', id: '3', metadata: { courseId: 10, lectureId: 20 } } as GlobalSearchResult);
                expect(router.navigate).toHaveBeenCalledWith(['/courses', 10, 'lectures', 20]);
            });

            it('should navigate to exam', () => {
                component['navigateToResult']({ type: 'exam', id: '4', metadata: { courseId: 10 } } as GlobalSearchResult);
                expect(router.navigate).toHaveBeenCalledWith(['/courses', 10, 'exams', '4']);
            });

            it('should navigate to faq', () => {
                component['navigateToResult']({ type: 'faq', metadata: { courseId: 10 } } as GlobalSearchResult);
                expect(router.navigate).toHaveBeenCalledWith(['/courses', 10, 'faq']);
            });

            it('should navigate to channel', () => {
                component['navigateToResult']({ type: 'channel', id: '5', metadata: { courseId: 10 } } as GlobalSearchResult);
                expect(router.navigate).toHaveBeenCalledWith(['/courses', 10, 'communication'], { queryParams: { conversationId: '5' } });
            });
        });

        describe('template', () => {
            it('should render the lecture content action button', () => {
                const button = fixture.nativeElement.querySelector('jhi-global-search-action-item');
                expect(button).toBeTruthy();
            });

            it('should render results when showResults is true', () => {
                fixture.componentRef.setInput('showResults', true);
                fixture.componentRef.setInput('results', [{ id: '1', type: 'exercise' }] as GlobalSearchResult[]);
                fixture.detectChanges();
                const items = fixture.nativeElement.querySelectorAll('jhi-global-search-result-item');
                expect(items.length).toBe(1);
            });

            it('should render no results state', () => {
                fixture.componentRef.setInput('showResults', true);
                fixture.componentRef.setInput('results', []);
                fixture.detectChanges();
                expect(fixture.nativeElement.textContent).toContain('global.search.noResultsFound');
            });

            it('should render error state', () => {
                fixture.componentRef.setInput('searchError', 'error.message');
                fixture.detectChanges();
                expect(fixture.nativeElement.textContent).toContain('error.message');
            });

            it('should render loading state', () => {
                fixture.componentRef.setInput('isLoading', true);
                fixture.detectChanges();
                const skeletons = fixture.nativeElement.querySelectorAll('p-skeleton');
                expect(skeletons.length).toBeGreaterThan(0);
            });
        });
    });

    describe('when iris is disabled', () => {
        beforeEach(() => {
            vi.clearAllMocks();
            configureTestBed(false);
        });

        it('should create', () => {
            expect(component).toBeTruthy();
        });

        it('itemCount should equal searchableEntities count when iris is disabled', () => {
            // actionButtonCount = 0 (iris disabled), searchableEntities.length = 6
            expect(component.itemCount()).toBe(6);
        });

        it('should not emit when Enter is pressed at index 0', () => {
            const spy = vi.fn();
            component.viewSelected.subscribe(spy);

            fixture.componentRef.setInput('selectedIndex', 0);
            fixture.detectChanges();

            const event = new KeyboardEvent('keydown', { key: 'Enter' });
            component.handleKeydown(event);

            expect(spy).not.toHaveBeenCalled();
        });

        it('should not render the lecture content action button', () => {
            const button = fixture.nativeElement.querySelector('jhi-global-search-action-item');
            expect(button).toBeNull();
        });
    });

    describe('when iris module is enabled but user has opted out of AI', () => {
        beforeEach(() => {
            vi.clearAllMocks();
            configureTestBed(true, LLMSelectionDecision.NO_AI);
        });

        it('should not render the iris answer card', () => {
            expect(fixture.nativeElement.querySelector('jhi-global-search-iris-answer')).toBeNull();
        });

        it('should not render the lecture search button', () => {
            expect(fixture.nativeElement.querySelector('jhi-global-search-action-item')).toBeNull();
        });

        it('itemCount should equal searchableEntities count only', () => {
            // actionButtonCount = 0 (user opted out), searchableEntities.length = 6
            expect(component.itemCount()).toBe(6);
        });
    });
});
