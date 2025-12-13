import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { CommonModule } from '@angular/common';
import { CourseNotificationComponent } from 'app/communication/course-notification/course-notification/course-notification.component';
import { CourseNotificationService } from 'app/communication/course-notification/course-notification.service';
import { CourseNotification } from 'app/communication/shared/entities/course-notification/course-notification';
import { CourseNotificationCategory } from 'app/communication/shared/entities/course-notification/course-notification-category';
import { CourseNotificationViewingStatus } from 'app/communication/shared/entities/course-notification/course-notification-viewing-status';
import { faBell, faComment } from '@fortawesome/free-solid-svg-icons';
import dayjs from 'dayjs/esm';
import { MockComponent, MockDirective } from 'ng-mocks';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ProfilePictureComponent } from 'app/shared/profile-picture/profile-picture.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';

describe('CourseNotificationComponent', () => {
    let component: CourseNotificationComponent;
    let fixture: ComponentFixture<CourseNotificationComponent>;
    let courseNotificationService: CourseNotificationService;
    let componentAsAny: any;

    const createMockNotification = (id: number, courseId: number, notificationType: string = 'newPostNotification', parameters: any = {}): CourseNotification => {
        return new CourseNotification(
            id,
            courseId,
            notificationType,
            CourseNotificationCategory.COMMUNICATION,
            CourseNotificationViewingStatus.UNSEEN,
            dayjs(),
            {
                courseTitle: 'Test Course',
                courseIconUrl: 'test-icon-url',
                ...parameters,
            },
            '/',
        );
    };

    beforeEach(async () => {
        courseNotificationService = {
            getIconFromType: jest.fn().mockReturnValue(faComment),
            getDateTranslationKey: jest.fn().mockReturnValue('artemisApp.courseNotification.temporal.now'),
            getDateTranslationParams: jest.fn().mockReturnValue({ hours: 1 }),
        } as unknown as CourseNotificationService;

        await TestBed.configureTestingModule({
            imports: [CommonModule, FaIconComponent],
            declarations: [CourseNotificationComponent, MockComponent(ProfilePictureComponent), MockDirective(TranslateDirective)],
            providers: [{ provide: CourseNotificationService, useValue: courseNotificationService }],
        }).compileComponents();

        fixture = TestBed.createComponent(CourseNotificationComponent);
        component = fixture.componentInstance;

        componentAsAny = component as any;

        fixture.componentRef.setInput('courseNotification', createMockNotification(1, 101));

        fixture.detectChanges();
    });

    afterEach(() => {
        jest.restoreAllMocks();
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
        expect(componentAsAny.notificationParameters).toEqual({
            courseTitle: 'Test Course',
            courseIconUrl: 'test-icon-url',
            courseName: 'Test Course',
            courseId: 101,
        });
        expect(componentAsAny.notificationType).toBe('newPostNotification');
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

        const closeClickedSpy = jest.spyOn(componentAsAny.onCloseClicked, 'emit');
        const closeButton = fixture.debugElement.query(By.css('.course-notification-close'));

        closeButton.nativeElement.click();

        expect(closeClickedSpy).toHaveBeenCalledOnce();
    });

    it('should add is-unseen class when isUnseen is true', () => {
        fixture.componentRef.setInput('isUnseen', true);
        fixture.detectChanges();

        const notificationWrap = fixture.debugElement.query(By.css('.course-notification-wrap'));
        expect(notificationWrap.classes['is-unseen']).toBeTrue();
    });

    it('should not add is-unseen class when isUnseen is false', () => {
        fixture.componentRef.setInput('isUnseen', false);
        fixture.detectChanges();

        const notificationWrap = fixture.debugElement.query(By.css('.course-notification-wrap'));
        expect(notificationWrap.classes['is-unseen']).toBeFalsy();
    });

    it('should show profile picture when author details are present', () => {
        const notificationWithAuthor = createMockNotification(1, 101, 'newPostNotification', {
            authorName: 'Test Author',
            authorId: 42,
            authorImageUrl: 'test-author-image.jpg',
        });

        fixture.componentRef.setInput('courseNotification', notificationWithAuthor);
        fixture.detectChanges();

        expect(componentAsAny.isShowProfilePicture).toBeTrue();
        expect(componentAsAny.authorName).toBe('Test Author');
        expect(componentAsAny.authorId).toBe(42);
        expect(componentAsAny.authorImageUrl).toBe('test-author-image.jpg');

        const profilePicture = fixture.debugElement.query(By.css('jhi-profile-picture'));
        expect(profilePicture).not.toBeNull();
    });

    it('should show icon when author details are not present', () => {
        const notificationWithoutAuthor = createMockNotification(1, 101);

        fixture.componentRef.setInput('courseNotification', notificationWithoutAuthor);
        fixture.detectChanges();

        expect(componentAsAny.isShowProfilePicture).toBeFalse();

        const iconElement = fixture.debugElement.query(By.css('.course-notification-icon'));
        expect(iconElement).not.toBeNull();
    });

    it('should update notification details when courseNotification input changes', () => {
        const initialType = componentAsAny.notificationType;

        jest.spyOn(courseNotificationService, 'getIconFromType').mockReturnValue(faBell);

        const updatedNotification = createMockNotification(2, 102, 'differentNotificationType');
        fixture.componentRef.setInput('courseNotification', updatedNotification);
        fixture.detectChanges();

        expect(courseNotificationService.getIconFromType).toHaveBeenCalledWith('differentNotificationType');
        expect(componentAsAny.notificationType).toBe('differentNotificationType');
        expect(componentAsAny.notificationType).not.toBe(initialType);
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
});
