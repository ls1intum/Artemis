import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { CommonModule } from '@angular/common';
import { CourseNotificationComponent } from 'app/notification/course-notification/course-notification/course-notification.component';
import { CourseNotificationService } from 'app/notification/course-notification/course-notification.service';
import { CourseNotification } from 'app/notification/shared/entities/course-notification/course-notification';
import { CourseNotificationCategory } from 'app/notification/shared/entities/course-notification/course-notification-category';
import { CourseNotificationViewingStatus } from 'app/notification/shared/entities/course-notification/course-notification-viewing-status';
import { faBell, faComment } from '@fortawesome/free-solid-svg-icons';
import dayjs from 'dayjs/esm';
import { MockComponent, MockDirective } from 'ng-mocks';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ProfilePictureComponent } from 'app/shared-ui/profile-picture/profile-picture.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { TranslateService, provideTranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import englishNotification from 'src/main/webapp/i18n/en/notification.json';
import germanNotification from 'src/main/webapp/i18n/de/notification.json';

describe('CourseNotificationComponent', () => {
    let component: CourseNotificationComponent;
    let fixture: ComponentFixture<CourseNotificationComponent>;
    let courseNotificationService: CourseNotificationService;
    let componentAsAny: any;

    const createMockNotification = (
        id: number,
        courseId: number,
        notificationType: string = 'newPostNotification',
        parameters: Record<string, unknown> = {},
    ): CourseNotification => {
        return new CourseNotification(
            id,
            courseId,
            notificationType,
            CourseNotificationCategory.COMMUNICATION,
            CourseNotificationViewingStatus.UNSEEN,
            dayjs(),
            'Test Course',
            'test-icon-url',
            { ...parameters },
            '/',
        );
    };

    afterEach(() => {
        vi.restoreAllMocks();
    });

    beforeEach(async () => {
        courseNotificationService = {
            getIconFromType: vi.fn().mockReturnValue(faComment),
            getDateTranslationKey: vi.fn().mockReturnValue('artemisApp.courseNotification.temporal.now'),
            getDateTranslationParams: vi.fn().mockReturnValue({ hours: 1 }),
        } as unknown as CourseNotificationService;

        await TestBed.configureTestingModule({
            imports: [CommonModule, FaIconComponent, CourseNotificationComponent, MockComponent(ProfilePictureComponent), MockDirective(TranslateDirective)],
            providers: [
                { provide: CourseNotificationService, useValue: courseNotificationService },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        });

        fixture = TestBed.createComponent(CourseNotificationComponent);
        component = fixture.componentInstance;

        componentAsAny = component as any;

        fixture.componentRef.setInput('courseNotification', createMockNotification(1, 101));

        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should initialize with the correct service calls', () => {
        expect(courseNotificationService.getIconFromType).toHaveBeenCalledWith('newPostNotification');
        expect(courseNotificationService.getDateTranslationKey).toHaveBeenCalled();
        expect(courseNotificationService.getDateTranslationParams).toHaveBeenCalled();
    });

    it('should set notification parameters correctly', () => {
        // The course is offered to the translation by name, and the payload contributes the values of its own type.
        // The icon is not a translation value, so it is not among them.
        expect(componentAsAny.notificationParameters()).toEqual({
            courseName: 'Test Course',
            courseId: 101,
        });
        expect(componentAsAny.notificationType()).toBe('newPostNotification');
    });

    it('should render markdown-bearing parameters to plain text (resolved asynchronously)', async () => {
        const notification = createMockNotification(2, 102, 'newPostNotification', { postMarkdownContent: '**bold** _italic_' });
        fixture.componentRef.setInput('courseNotification', notification);
        fixture.detectChanges();

        expect(componentAsAny.notificationInitialized()).toBe(false);

        // Rendering markdown lazily loads the pipeline, so the parameter is populated after a microtask.
        await vi.waitFor(() => {
            expect(componentAsAny.notificationParameters()?.postMarkdownContent).toBe('bold italic');
        });
        expect(componentAsAny.notificationInitialized()).toBe(true);
    });

    it('should show close button when isShowClose is true', () => {
        fixture.componentRef.setInput('isShowClose', true);
        fixture.detectChanges();

        const closeButton = fixture.debugElement.query(By.css('.course-notification-close'));
        expect(closeButton).not.toBeNull();
    });

    it('should not show close button when isShowClose is false', () => {
        fixture.componentRef.setInput('isShowClose', false);
        fixture.detectChanges();

        const closeButton = fixture.debugElement.query(By.css('.course-notification-close'));
        expect(closeButton).toBeNull();
    });

    it('should emit onCloseClicked when close button is clicked', () => {
        fixture.componentRef.setInput('isShowClose', true);
        fixture.detectChanges();

        const closeClickedSpy = vi.spyOn(componentAsAny.onCloseClicked, 'emit');
        const closeButton = fixture.debugElement.query(By.css('.course-notification-close'));

        closeButton.nativeElement.click();

        expect(closeClickedSpy).toHaveBeenCalledOnce();
    });

    it('should add is-unseen class when isUnseen is true', () => {
        fixture.componentRef.setInput('isUnseen', true);
        fixture.detectChanges();

        const notificationWrap = fixture.debugElement.query(By.css('.course-notification-wrap'));
        expect(notificationWrap.classes['is-unseen']).toBe(true);
    });

    it('should not add is-unseen class when isUnseen is false', () => {
        fixture.componentRef.setInput('isUnseen', false);
        fixture.detectChanges();

        const notificationWrap = fixture.debugElement.query(By.css('.course-notification-wrap'));
        expect(notificationWrap.classes['is-unseen']).toBeFalsy();
    });

    it('should add is-fluid class and host fluid class only when fluid is true', () => {
        fixture.componentRef.setInput('fluid', false);
        fixture.detectChanges();
        expect(fixture.debugElement.query(By.css('.course-notification-wrap')).classes['is-fluid']).toBeFalsy();
        expect((fixture.nativeElement as HTMLElement).classList.contains('fluid')).toBe(false);

        fixture.componentRef.setInput('fluid', true);
        fixture.detectChanges();
        expect(fixture.debugElement.query(By.css('.course-notification-wrap')).classes['is-fluid']).toBe(true);
        expect((fixture.nativeElement as HTMLElement).classList.contains('fluid')).toBe(true);
    });

    it('should show profile picture when author details are present', () => {
        const notificationWithAuthor = createMockNotification(1, 101, 'newPostNotification', {
            authorName: 'Test Author',
            authorId: 42,
            authorImageUrl: 'test-author-image.jpg',
        });

        fixture.componentRef.setInput('courseNotification', notificationWithAuthor);
        fixture.detectChanges();

        expect(componentAsAny.isShowProfilePicture()).toBe(true);
        expect(componentAsAny.authorName()).toBe('Test Author');
        expect(componentAsAny.authorId()).toBe(42);
        expect(componentAsAny.authorImageUrl()).toBe('test-author-image.jpg');

        const profilePicture = fixture.debugElement.query(By.css('jhi-profile-picture'));
        expect(profilePicture).not.toBeNull();
    });

    it('should use the reply author profile and decode navigation query parameters', () => {
        const notification = createMockNotification(7, 101, 'newAnswerNotification', {
            replyAuthorName: 'Ada Lovelace',
            replyAuthorId: 42,
            replyImageUrl: '/public/ada.png',
            replyIsBot: true,
        });
        notification.relativeWebAppUrl = '/courses/101/messages?messageId=42&search=sorting%20algorithms&empty';
        fixture.componentRef.setInput('courseNotification', notification);
        fixture.detectChanges();

        expect(component['authorName']()).toBe('Ada Lovelace');
        expect(component['authorId']()).toBe(42);
        expect(component['authorImageUrl']()).toBe('/public/ada.png');
        expect(component['isAuthorBot']()).toBe(true);
        expect(component['isShowProfilePicture']()).toBe(true);
        expect(component['notificationUrl']()).toEqual({ link: ['/courses/101/messages'], queryParams: { messageId: '42', search: 'sorting algorithms', empty: '' } });
        expect(fixture.debugElement.query(By.directive(ProfilePictureComponent))).not.toBeNull();
    });

    it('should show icon when author details are not present', () => {
        const notificationWithoutAuthor = createMockNotification(1, 101);

        fixture.componentRef.setInput('courseNotification', notificationWithoutAuthor);
        fixture.detectChanges();

        expect(componentAsAny.isShowProfilePicture()).toBe(false);

        const iconElement = fixture.debugElement.query(By.css('.course-notification-icon'));
        expect(iconElement).not.toBeNull();
    });

    it('should update notification details when courseNotification input changes', () => {
        const initialType = componentAsAny.notificationType();

        vi.spyOn(courseNotificationService, 'getIconFromType').mockReturnValue(faBell);

        const updatedNotification = createMockNotification(2, 102, 'differentNotificationType');
        fixture.componentRef.setInput('courseNotification', updatedNotification);
        fixture.detectChanges();

        expect(courseNotificationService.getIconFromType).toHaveBeenCalledWith('differentNotificationType');
        expect(componentAsAny.notificationType()).toBe('differentNotificationType');
        expect(componentAsAny.notificationType()).not.toBe(initialType);
    });

    it('should show loading indicator when displayTimeInMilliseconds is defined', () => {
        fixture.componentRef.setInput('displayTimeInMilliseconds', 5000);
        fixture.detectChanges();

        const loadingIndicator = fixture.debugElement.query(By.css('.course-notification-loading-indicator'));
        expect(loadingIndicator).not.toBeNull();
        expect(loadingIndicator.styles['animation-duration']).toBe('5000ms');
    });

    it('should not show loading indicator when displayTimeInMilliseconds is undefined', () => {
        fixture.componentRef.setInput('displayTimeInMilliseconds', undefined);
        fixture.detectChanges();

        const loadingIndicator = fixture.debugElement.query(By.css('.course-notification-loading-indicator'));
        expect(loadingIndicator).toBeNull();
    });

    it('should show the generic content of a notification without a message of its editor', () => {
        fixture.componentRef.setInput('courseNotification', createMockNotification(3, 101, 'exerciseUpdatedNotification', { exerciseTitle: 'Essay' }));
        fixture.detectChanges();

        expect(componentAsAny.notificationContentKey()).toBe('artemisApp.courseNotification.exerciseUpdatedNotification.content');
    });

    it('should show the content with the message of its editor, escaped, when the notification carries one', () => {
        fixture.componentRef.setInput(
            'courseNotification',
            createMockNotification(3, 101, 'attachmentChangedNotification', { unitName: 'Lecture 1', notificationText: 'Use <b>v2</b> & re-download' }),
        );
        fixture.detectChanges();

        expect(componentAsAny.notificationContentKey()).toBe('artemisApp.courseNotification.attachmentChangedNotification.contentWithNotificationText');
        expect(componentAsAny.notificationParameters().notificationText).toBe('Use &lt;b&gt;v2&lt;/b&gt; &amp; re-download');
        expect(componentAsAny.notificationParameters().unitName).toBe('Lecture 1');
    });

    it('should show the generic content when the message of the editor is blank', () => {
        fixture.componentRef.setInput('courseNotification', createMockNotification(3, 101, 'exerciseUpdatedNotification', { exerciseTitle: 'Essay', notificationText: '  ' }));
        fixture.detectChanges();

        expect(componentAsAny.notificationContentKey()).toBe('artemisApp.courseNotification.exerciseUpdatedNotification.content');
    });
});

describe('CourseNotificationComponent rendering translated content', () => {
    let fixture: ComponentFixture<CourseNotificationComponent>;

    const renderContent = async (notificationType: string, payload: Record<string, unknown>): Promise<HTMLElement> => {
        const notification = new CourseNotification(
            1,
            101,
            notificationType,
            CourseNotificationCategory.GENERAL,
            CourseNotificationViewingStatus.UNSEEN,
            dayjs(),
            'Test Course',
            undefined,
            payload,
            '/courses/101',
        );
        fixture.componentRef.setInput('courseNotification', notification);
        fixture.detectChanges();
        await vi.waitFor(() => {
            fixture.detectChanges();
            expect(fixture.nativeElement.querySelector('.course-notification-content')).not.toBeNull();
        });
        return fixture.nativeElement.querySelector('.course-notification-content');
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CourseNotificationComponent, MockComponent(ProfilePictureComponent)],
            providers: [
                provideTranslateService(),
                {
                    provide: CourseNotificationService,
                    useValue: {
                        getIconFromType: vi.fn().mockReturnValue(faComment),
                        getDateTranslationKey: vi.fn().mockReturnValue('artemisApp.courseNotification.temporal.now'),
                        getDateTranslationParams: vi.fn().mockReturnValue({}),
                    },
                },
            ],
        });
        // The real English translations, so a missing or misspelled key fails here instead of showing up as a key in the app.
        const translateService = TestBed.inject(TranslateService);
        translateService.setTranslation('en', englishNotification);
        translateService.setTranslation('de', germanNotification);
        translateService.use('en');
        fixture = TestBed.createComponent(CourseNotificationComponent);
    });

    it('should show the message an editor wrote about an exercise update, next to the exercise', async () => {
        const content = await renderContent('exerciseUpdatedNotification', { exerciseTitle: 'Essay on sorting', notificationText: 'Task 2 now asks for 300 words' });

        expect(content.textContent).toBe('Essay on sorting: Task 2 now asks for 300 words');
    });

    it('should show the message an editor wrote about an attachment as text, never as markup', async () => {
        const content = await renderContent('attachmentChangedNotification', {
            unitName: 'Sorting algorithms',
            notificationText: 'Slide 4 was <b>corrected</b> & <img src=x onerror="alert(1)">',
        });

        expect(content.textContent).toBe('Sorting algorithms: Slide 4 was <b>corrected</b> & <img src=x onerror="alert(1)">');
        expect(content.querySelector('b, img')).toBeNull();
    });

    it('should show the generic content of notifications stored without a message', async () => {
        expect((await renderContent('exerciseUpdatedNotification', { exerciseTitle: 'Essay on sorting' })).textContent).toBe('Essay on sorting was updated.');
        expect((await renderContent('attachmentChangedNotification', { unitName: 'Sorting algorithms' })).textContent).toBe('An attachment in Sorting algorithms changed.');
    });
    it.each([
        'newPostNotification',
        'newAnswerNotification',
        'irisResponseNeedsReviewNotification',
        'newMentionNotification',
        'newAnnouncementNotification',
        'newCpcPlagiarismCaseNotification',
        'newPlagiarismCaseNotification',
    ])('should show a fallback when %s has no message preview', async (notificationType) => {
        const content = await renderContent(notificationType, {});

        expect(content.textContent).toBe('No message preview available.');
    });

    it.each([undefined, null, '', '   ', '![Screenshot](/public/image.png)'])('should handle an empty or non-text post preview (%s)', async (postMarkdownContent) => {
        const content = await renderContent('newPostNotification', { postMarkdownContent });

        expect(content.textContent).toBe('No message preview available.');
    });

    it('should keep rendering the message text when a preview is available', async () => {
        const content = await renderContent('newPostNotification', { postMarkdownContent: '**Please** check the build log.' });

        expect(content.textContent).toBe('Please check the build log.');
    });

    it('should use the reply preview for a thread answer even when the parent message has no preview', async () => {
        const content = await renderContent('newAnswerNotification', { replyMarkdownContent: 'The build is fixed.' });

        expect(content.textContent).toBe('The build is fixed.');
    });

    it('should not use the parent message as a substitute for a missing reply preview', async () => {
        const content = await renderContent('newAnswerNotification', { postMarkdownContent: 'Original message' });

        expect(content.textContent).toBe('No message preview available.');
    });

    it('should update the fallback when the language changes', async () => {
        const content = await renderContent('newPostNotification', {});
        TestBed.inject(TranslateService).use('de');
        fixture.detectChanges();
        await fixture.whenStable();

        expect(content.textContent).toBe('Keine Nachrichtenvorschau verfügbar.');
    });
});
