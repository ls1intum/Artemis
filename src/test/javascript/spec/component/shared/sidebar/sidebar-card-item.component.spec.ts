import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SidebarCardItemComponent } from 'app/shared/sidebar/sidebar-card-item/sidebar-card-item.component';
import { SidebarCardSize } from 'app/types/sidebar';
import { DifficultyLevel } from 'app/exercise/entities/exercise.model';
import { OneToOneChatDTO } from 'app/entities/metis/conversation/one-to-one-chat.model';
import { input, runInInjectionContext } from '@angular/core';
import { faPeopleGroup } from '@fortawesome/free-solid-svg-icons';
import { ProfilePictureComponent } from '../../../../../../main/webapp/app/shared/profile-picture/profile-picture.component';
import { MockComponent } from 'ng-mocks';

describe('SidebarCardItemComponent', () => {
    let component: SidebarCardItemComponent;
    let fixture: ComponentFixture<SidebarCardItemComponent>;
    let sidebarItemMock: any;

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [SidebarCardItemComponent, MockComponent(ProfilePictureComponent)],
        }).compileComponents();

        fixture = TestBed.createComponent(SidebarCardItemComponent);
        component = fixture.componentInstance;

        sidebarItemMock = {
            title: 'testTitle',
            id: 'testId',
            size: 'M' as SidebarCardSize,
            difficulty: DifficultyLevel.EASY,
            type: 'oneToOneChat',
            conversation: {
                members: [
                    { id: 2, name: 'RequestingUser', isRequestingUser: true },
                    { id: 1, name: 'User1', isRequestingUser: false },
                ],
            } as OneToOneChatDTO,
        };

        component.sidebarItem = sidebarItemMock;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should display item title', () => {
        const compiled = fixture.debugElement.nativeElement;
        expect(compiled.querySelector('#test-sidebar-card-title').textContent).toContain(sidebarItemMock.title);
    });

    it('should format unreadCount correctly when count is less than 99', () => {
        runInInjectionContext(fixture.debugElement.injector, () => {
            component.unreadCount = input<number>(45);
            component.ngOnInit();
            expect(component.formattedUnreadCount).toBe('45');
        });
    });

    it('should format unreadCount as "99+" when count exceeds 99', () => {
        runInInjectionContext(fixture.debugElement.injector, () => {
            component.unreadCount = input<number>(120);
            component.ngOnInit();
            expect(component.formattedUnreadCount).toBe('99+');
        });
    });

    it('should set group icon for group chats in extractMessageUser', () => {
        component.sidebarItem.type = 'groupChat';
        component.sidebarItem.icon = undefined;
        component.extractMessageUser();
        expect(component.sidebarItem.icon).toBe(faPeopleGroup);
    });

    it('should set otherUser for one-to-one chat in extractMessageUser', () => {
        component.extractMessageUser();
        expect(component.otherUser).toEqual(sidebarItemMock.conversation.members[1]);
    });
});
