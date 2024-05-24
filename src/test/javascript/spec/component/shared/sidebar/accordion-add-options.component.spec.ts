import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AccordionAddOptionsComponent } from 'app/shared/sidebar/accordion-add-options/accordion-add-options.component';
import { MockComponent, MockProvider } from 'ng-mocks';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbCollapseMocksModule } from '../../../helpers/mocks/directive/ngbCollapseMocks.module';
import { NgbTooltipMocksModule } from '../../../helpers/mocks/directive/ngbTooltipMocks.module';
import { MetisConversationService } from 'app/shared/metis/metis-conversation.service';
import { NotificationService } from 'app/shared/notification/notification.service';
import { AccountService } from 'app/core/auth/account.service';
import { MetisService } from 'app/shared/metis/metis.service';
import { ConversationService } from 'app/shared/metis/conversations/conversation.service';
import { ConversationDTO } from 'app/entities/metis/conversation/conversation.model';
import { CourseStorageService } from 'app/course/manage/course-storage.service';

describe('AccordionAddOptionsComponent', () => {
    let component: AccordionAddOptionsComponent;
    let fixture: ComponentFixture<AccordionAddOptionsComponent>;
    const canCreateChannel = jest.fn();

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [NgbCollapseMocksModule, NgbTooltipMocksModule],
            declarations: [AccordionAddOptionsComponent, MockComponent(FaIconComponent)],
            providers: [
                MockProvider(NgbModal),
                MockProvider(MetisConversationService),
                MockProvider(NotificationService),
                MockProvider(AccountService),
                MockProvider(MetisService),
                MockProvider(CourseStorageService),
                MockProvider(ConversationService, {
                    getConversationName: (conversation: ConversationDTO) => {
                        return conversation.id + '';
                    },
                }),
            ],
        }).compileComponents();

        canCreateChannel.mockReturnValue(true);
        fixture = TestBed.createComponent(AccordionAddOptionsComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
