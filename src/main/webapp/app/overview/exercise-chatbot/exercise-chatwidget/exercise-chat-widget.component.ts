import { Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { faArrowDown, faCircleInfo, faExpand, faPaperPlane, faRobot, faXmark } from '@fortawesome/free-solid-svg-icons';
import { MatDialog } from '@angular/material/dialog';
import { ActivatedRoute } from '@angular/router';
import { LocalStorageService } from 'ngx-webstorage';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/core/user/user.model';

@Component({
    selector: 'jhi-exercise-chat-widget',
    templateUrl: './exercise-chat-widget.component.html',
    styleUrls: ['./exercise-chat-widget.component.scss'],
})
export class ExerciseChatWidgetComponent implements OnInit {
    @ViewChild('chatWidget') chatWidget!: ElementRef;
    @ViewChild('chatBody') chatBody!: ElementRef;
    messages: string[] = [];
    irisMessages: string[] = [];
    userMessages: string[] = [];
    newMessage = '';
    componentClass = 'chat-widget';
    headerClass = 'chat-header';
    userAccepted = false;
    public firstName: string | undefined;
    isScrolledToBottom = true;

    constructor(private dialog: MatDialog, private route: ActivatedRoute, private localStorage: LocalStorageService, private accountService: AccountService) {}

    // Icons
    faPaperPlane = faPaperPlane;
    faExpand = faExpand;
    faXmark = faXmark;
    faArrowDown = faArrowDown;
    faRobot = faRobot;
    faCircleInfo = faCircleInfo;

    ngOnInit() {
        this.route.url.subscribe((data) => {
            if (data[0].path === 'chat') {
                this.setFullScreenClass();
            } else {
                this.setDefaultClass();
            }
        });
        this.accountService.identity().then((user: User) => {
            if (typeof user!.login === 'string') {
                this.userAccepted = localStorage.getItem(user!.login) == 'true';
            }
        });
    }

    private setDefaultClass() {
        this.componentClass = 'chat-widget ngDraggable ngResizable';
        this.headerClass = 'chat-header';
    }

    private setFullScreenClass() {
        this.componentClass = 'chat-widget-fullscreen';
        this.headerClass = 'chat-header-fullscreen';
    }

    onSend(): void {
        if (this.newMessage) {
            this.userMessages.push(this.newMessage);
            this.newMessage = '';
        }
        this.scrollToBottom();
    }

    scrollToBottom() {
        setTimeout(() => {
            const chatBodyElement: HTMLElement = this.chatBody.nativeElement;
            chatBodyElement.scrollTop = chatBodyElement.scrollHeight;
        });
    }

    closeChat() {
        this.dialog.closeAll();
    }

    openComponentInNewWindow() {
        window.open('/chat', '_blank', 'width=600,height=600');
        this.closeChat();
    }

    acceptPermission() {
        this.accountService.identity().then((user: User) => {
            if (typeof user!.login === 'string') {
                localStorage.setItem(user!.login, 'true');
            }
        });

        this.userAccepted = true;
        this.irisMessages.push('Hey! How can I help you?');
    }

    scrollToBottomOnClick() {
        const chatBody = this.chatBody.nativeElement;
        chatBody.scrollTop = chatBody.scrollHeight;
        this.isScrolledToBottom = true;
    }

    onChatScroll() {
        const chatBody = this.chatBody.nativeElement;
        const scrollHeight = chatBody.scrollHeight;
        const scrollTop = chatBody.scrollTop;
        const clientHeight = chatBody.clientHeight;
        this.isScrolledToBottom = scrollHeight - scrollTop === clientHeight;
    }
}
