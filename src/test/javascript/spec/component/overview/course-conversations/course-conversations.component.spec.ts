import { CourseConversationsComponent } from 'app/overview/course-conversations/course-conversations.component';
import { ComponentFixture, TestBed, fakeAsync, tick, waitForAsync } from '@angular/core/testing';
import { ConversationDTO } from 'app/entities/metis/conversation/conversation.model';
import { generateExampleChannelDTO, generateExampleGroupChatDTO, generateOneToOneChatDTO } from './helpers/conversationExampleModels';
import { MockComponent, MockPipe } from 'ng-mocks';
import { MetisConversationService } from 'app/shared/metis/metis-conversation.service';
import { LoadingIndicatorContainerStubComponent } from '../../../helpers/stubs/loading-indicator-container-stub.component';
import { ConversationSelectionSidebarComponent } from 'app/overview/course-conversations/layout/conversation-selection-sidebar/conversation-selection-sidebar.component';
import { ConversationHeaderComponent } from 'app/overview/course-conversations/layout/conversation-header/conversation-header.component';
import { CourseWideSearchComponent } from 'app/overview/course-conversations/course-wide-search/course-wide-search.component';
import { ConversationMessagesComponent } from 'app/overview/course-conversations/layout/conversation-messages/conversation-messages.component';
import { ConversationThreadSidebarComponent } from 'app/overview/course-conversations/layout/conversation-thread-sidebar/conversation-thread-sidebar.component';
import { Course } from 'app/entities/course.model';
import { BehaviorSubject } from 'rxjs';
import { ActivatedRoute, Params, Router, convertToParamMap } from '@angular/router';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { MetisService } from 'app/shared/metis/metis.service';
import { Post } from 'app/entities/metis/post.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { CourseConversationsCodeOfConductComponent } from 'app/overview/course-conversations/code-of-conduct/course-conversations-code-of-conduct.component';
import { MockMetisConversationService } from '../../../helpers/mocks/service/mock-metis-conversation.service';
import { MockMetisService } from '../../../helpers/mocks/service/mock-metis-service.service';
import { ButtonComponent } from 'app/shared/components/button.component';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { SortDirection } from 'app/shared/metis/metis.util';
import { DocumentationButtonComponent } from 'app/shared/components/documentation-button/documentation-button.component';
import { getElement } from '../../../helpers/utils/general.utils';

const examples: (ConversationDTO | undefined)[] = [undefined, generateOneToOneChatDTO({}), generateExampleGroupChatDTO({}), generateExampleChannelDTO({})];

examples.forEach((activeConversation) => {
    describe('CourseConversationComponent with ' + (activeConversation?.type || 'no active conversation'), () => {
        let component: CourseConversationsComponent;
        let fixture: ComponentFixture<CourseConversationsComponent>;
        const course = { id: 1 } as Course;
        let queryParamsSubject: BehaviorSubject<Params>;
        const router = new MockRouter();
        let postsSubject: BehaviorSubject<Post[]>;
        let acceptCodeOfConductSpy: jest.SpyInstance;
        let setActiveConversationSpy: jest.SpyInstance;

        beforeEach(waitForAsync(() => {
            queryParamsSubject = new BehaviorSubject(convertToParamMap({}));
            TestBed.configureTestingModule({
                declarations: [
                    CourseConversationsComponent,
                    LoadingIndicatorContainerStubComponent,
                    MockComponent(ConversationSelectionSidebarComponent),
                    MockComponent(ConversationHeaderComponent),
                    MockComponent(DocumentationButtonComponent),
                    MockComponent(ConversationMessagesComponent),
                    MockComponent(ConversationThreadSidebarComponent),
                    MockComponent(CourseConversationsCodeOfConductComponent),
                    MockComponent(ButtonComponent),
                    MockComponent(CourseWideSearchComponent),
                    MockPipe(ArtemisTranslatePipe),
                    MockPipe(HtmlForMarkdownPipe),
                ],
                providers: [
                    { provide: Router, useValue: router },
                    {
                        provide: ActivatedRoute,
                        useValue: {
                            parent: {
                                parent: {
                                    paramMap: new BehaviorSubject(
                                        convertToParamMap({
                                            courseId: 1,
                                        }),
                                    ),
                                },
                            },
                            queryParams: queryParamsSubject,
                        },
                    },
                ],
                imports: [FormsModule, ReactiveFormsModule, FontAwesomeModule, NgbModule],
            }).compileComponents();

            const metisConversationService = new MockMetisConversationService();
            const metisService = new MockMetisService();

            TestBed.overrideComponent(CourseConversationsComponent, {
                set: {
                    providers: [
                        { provide: MetisConversationService, useValue: metisConversationService },
                        { provide: MetisService, useValue: metisService },
                    ],
                },
            });

            fixture = TestBed.createComponent(CourseConversationsComponent);
            component = fixture.componentInstance;

            postsSubject = new BehaviorSubject([]);
            jest.spyOn(metisConversationService, 'course', 'get').mockReturnValue(course);
            jest.spyOn(metisConversationService, 'activeConversation$', 'get').mockReturnValue(new BehaviorSubject(activeConversation).asObservable());
            setActiveConversationSpy = jest.spyOn(metisConversationService, 'setActiveConversation');
            acceptCodeOfConductSpy = jest.spyOn(metisConversationService, 'acceptCodeOfConduct');

            jest.spyOn(metisService, 'posts', 'get').mockReturnValue(postsSubject.asObservable());
        }));

        afterEach(() => {
            jest.resetAllMocks();
        });

        it('should have service set up', () => {
            fixture.detectChanges();
            expect(component.isServiceSetUp).toBeTrue();
            expect(component.isLoading).toBeFalse();
            expect(component.conversationsOfUser).toHaveLength(1);
            expect(component.activeConversation).toEqual(activeConversation);
        });

        it('should update thread in post', fakeAsync(() => {
            fixture.detectChanges();
            component.postInThread = { id: 1, content: 'loremIpsum' } as Post;
            fixture.detectChanges();
            const updatedPost = { id: 1, content: 'updatedContent' } as Post;
            postsSubject.next([updatedPost]);
            tick();
            expect(component.postInThread).toEqual(updatedPost);
        }));

        it('should set active conversation depending on the query param', fakeAsync(() => {
            queryParamsSubject.next({ conversationId: '12' });
            // mock setActiveConversationById method
            fixture.detectChanges();
            tick();
            expect(setActiveConversationSpy).toHaveBeenCalledWith(12);
        }));

        it('should set the query params when an active conversation is selected', () => {
            const activatedRoute = TestBed.inject(ActivatedRoute);
            const navigateSpy = jest.spyOn(router, 'navigate');
            fixture.detectChanges();
            expect(navigateSpy).toHaveBeenCalledWith([], {
                relativeTo: activatedRoute,
                queryParams: { conversationId: activeConversation?.id },
                replaceUrl: true,
            });
        });

        it('should accept code of conduct', () => {
            fixture.detectChanges();
            component.acceptCodeOfConduct();
            expect(acceptCodeOfConductSpy).toHaveBeenCalledOnce();
        });

        it('should initialize the course-wide search text correctly', () => {
            fixture.detectChanges();
            const searchInput = getElement(fixture.debugElement, 'input[name=searchText]');
            expect(searchInput.textContent).toBe('');
        });

        it('should update the course-wide search text correctly given an input', () => {
            fixture.detectChanges();
            const searchInput = getElement(fixture.debugElement, 'input[name=searchText]');
            searchInput.value = 'test input';
            searchInput.dispatchEvent(new Event('input'));
            fixture.detectChanges();
            expect(component.courseWideSearchTerm).toBe('test input');
        });

        it('should set search text in the courseWideSearchConfig correctly', () => {
            fixture.detectChanges();
            component.initializeCourseWideSearchConfig();
            component.courseWideSearchTerm = 'test';
            component.onSearch();
            fixture.detectChanges();
            expect(component.courseWideSearchConfig.searchTerm).toBe(component.courseWideSearchTerm);
        });

        it('should initialize formGroup correctly', fakeAsync(() => {
            component.ngOnInit();
            tick();
            expect(component.formGroup.get('filterToOwn')?.value).toBeFalse();
            expect(component.formGroup.get('filterToUnresolved')?.value).toBeFalse();
            expect(component.formGroup.get('filterToAnsweredOrReacted')?.value).toBeFalse();
        }));

        it('Should update filter setting when filterToUnresolved checkbox is checked', fakeAsync(() => {
            fixture.detectChanges();
            component.formGroup.patchValue({
                filterToUnresolved: true,
                filterToOwn: false,
                filterToAnsweredOrReacted: false,
            });
            const filterResolvedCheckbox = getElement(fixture.debugElement, 'input[name=filterToUnresolved]');
            filterResolvedCheckbox.dispatchEvent(new Event('change'));
            tick();
            fixture.detectChanges();
            expect(component.courseWideSearchConfig.filterToUnresolved).toBeTrue();
            expect(component.courseWideSearchConfig.filterToOwn).toBeFalse();
            expect(component.courseWideSearchConfig.filterToAnsweredOrReacted).toBeFalse();
        }));

        it('Should update filter setting when filterToOwn checkbox is checked', fakeAsync(() => {
            fixture.detectChanges();
            component.formGroup.patchValue({
                filterToUnresolved: false,
                filterToOwn: true,
                filterToAnsweredOrReacted: false,
            });
            const filterOwnCheckbox = getElement(fixture.debugElement, 'input[name=filterToOwn]');
            filterOwnCheckbox.dispatchEvent(new Event('change'));
            tick();
            fixture.detectChanges();
            expect(component.courseWideSearchConfig.filterToUnresolved).toBeFalse();
            expect(component.courseWideSearchConfig.filterToOwn).toBeTrue();
            expect(component.courseWideSearchConfig.filterToAnsweredOrReacted).toBeFalse();
        }));

        it('Should update filter setting when filterToAnsweredOrReacted checkbox is checked', fakeAsync(() => {
            fixture.detectChanges();
            component.formGroup.patchValue({
                filterToUnresolved: false,
                filterToOwn: false,
                filterToAnsweredOrReacted: true,
            });
            const filterAnsweredOrReactedCheckbox = getElement(fixture.debugElement, 'input[name=filterToAnsweredOrReacted]');
            filterAnsweredOrReactedCheckbox.dispatchEvent(new Event('change'));
            tick();
            fixture.detectChanges();
            expect(component.courseWideSearchConfig.filterToUnresolved).toBeFalse();
            expect(component.courseWideSearchConfig.filterToOwn).toBeFalse();
            expect(component.courseWideSearchConfig.filterToAnsweredOrReacted).toBeTrue();
        }));

        it('Should update filter setting when all filter checkboxes are checked', fakeAsync(() => {
            fixture.detectChanges();
            component.formGroup.patchValue({
                filterToUnresolved: true,
                filterToOwn: true,
                filterToAnsweredOrReacted: true,
            });
            const filterResolvedCheckbox = getElement(fixture.debugElement, 'input[name=filterToUnresolved]');
            const filterOwnCheckbox = getElement(fixture.debugElement, 'input[name=filterToOwn]');
            const filterAnsweredOrReactedCheckbox = getElement(fixture.debugElement, 'input[name=filterToAnsweredOrReacted]');
            filterResolvedCheckbox.dispatchEvent(new Event('change'));
            filterOwnCheckbox.dispatchEvent(new Event('change'));
            filterAnsweredOrReactedCheckbox.dispatchEvent(new Event('change'));
            tick();
            fixture.detectChanges();
            expect(component.courseWideSearchConfig.filterToUnresolved).toBeTrue();
            expect(component.courseWideSearchConfig.filterToOwn).toBeTrue();
            expect(component.courseWideSearchConfig.filterToAnsweredOrReacted).toBeTrue();
        }));

        it('should initialize sorting direction correctly', fakeAsync(() => {
            component.ngOnInit();
            tick();
            fixture.detectChanges();
            expect(component.courseWideSearchConfig.sortingOrder).toBe(SortDirection.ASCENDING);
        }));

        it('should change sorting direction after clicking the order direction button', fakeAsync(() => {
            component.ngOnInit();
            tick();
            fixture.detectChanges();
            const selectedDirectionOption = getElement(fixture.debugElement, '.clickable');
            selectedDirectionOption.dispatchEvent(new Event('click'));
            expect(component.courseWideSearchConfig.sortingOrder).toBe(SortDirection.DESCENDING);
        }));
    });
});
