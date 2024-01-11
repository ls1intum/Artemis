import { NavigationStart, Router } from '@angular/router';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { AfterViewInit, Component, Inject, OnDestroy } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialog } from '@angular/material/dialog';
import { IrisStateStore } from 'app/iris/state-store.service';
import { Subscription } from 'rxjs';
import { SharedService } from 'app/iris/shared.service';
import { IrisSessionService } from 'app/iris/session.service';
import { animate, state, style, transition, trigger } from '@angular/animations';
import { UserService } from 'app/core/user/user.service';
import { TranslateService } from '@ngx-translate/core';
import { IrisChatbotWidgetComponent } from 'app/iris/exercise-chatbot/widget/chatbot-widget.component';
import interact from 'interactjs';

@Component({
    selector: 'jhi-chatbot-dialog-widget',
    templateUrl: './chatbot-dialog-widget.component.html',
    styleUrls: ['./chatbot-widget.component.scss'],
    animations: [
        trigger('fadeAnimation', [
            state(
                'start',
                style({
                    opacity: 1,
                }),
            ),
            state(
                'end',
                style({
                    opacity: 0,
                }),
            ),
            transition('start => end', [animate('2s ease')]),
        ]),
    ],
})
export class IrisChatbotDialogWidgetComponent extends IrisChatbotWidgetComponent implements AfterViewInit, OnDestroy {
    private navigationSubscription: Subscription;
    // data
    exerciseId?: number | undefined;
    courseId: number;
    stateStore: IrisStateStore;
    sessionService: IrisSessionService;
    constructor(
        dialog: MatDialog,
        @Inject(MAT_DIALOG_DATA) public data: any,
        userService: UserService,
        private router: Router,
        sharedService: SharedService,
        modalService: NgbModal,
        translateService: TranslateService,
    ) {
        super(dialog, userService, sharedService, modalService, translateService, document);
        this.stateStore = data.stateStore;
        this.courseId = data.courseId;
        this.exerciseId = Number.isNaN(data.exerciseId) ? undefined : data.exerciseId;
        this.sessionService = data.sessionService;
        this.navigationSubscription = this.router.events.subscribe((event) => {
            if (event instanceof NavigationStart) {
                this.dialog.closeAll();
            }
        });
        this.fullSize = data.fullSize ?? false;
        this.paramsOnSend = data.paramsOnSend;
    }

    ngAfterViewInit() {
        super.ngAfterViewInit();
        interact('.chat-widget')
            .resizable({
                // resize from all edges and corners
                edges: { left: true, right: true, bottom: true, top: true },

                listeners: {
                    move: (event) => {
                        const target = event.target;
                        let x = parseFloat(target.getAttribute('data-x')) || 0;
                        let y = parseFloat(target.getAttribute('data-y')) || 0;

                        // update the element's style
                        target.style.width = event.rect.width + 'px';
                        target.style.height = event.rect.height + 'px';

                        // Reset fullsize if widget smaller than the full size factors times the overlay container size
                        const cntRect = (this.document.querySelector('.cdk-overlay-container') as HTMLElement).getBoundingClientRect();
                        this.fullSize = !(event.rect.width < cntRect.width * this.fullWidthFactor || event.rect.height < cntRect.height * this.fullHeightFactor);

                        // translate when resizing from top or left edges
                        x += event.deltaRect.left;
                        y += event.deltaRect.top;

                        target.style.transform = 'translate(' + x + 'px,' + y + 'px)';

                        target.setAttribute('data-x', x);
                        target.setAttribute('data-y', y);
                    },
                },
                modifiers: [
                    // keep the edges inside the parent
                    interact.modifiers.restrictEdges({
                        outer: '.cdk-overlay-container',
                    }),

                    // minimum size
                    interact.modifiers.restrictSize({
                        min: { width: this.initialWidth, height: this.initialHeight },
                    }),
                ],

                inertia: true,
            })
            .draggable({
                listeners: {
                    move: (event: any) => {
                        const target = event.target,
                            // keep the dragged position in the data-x/data-y attributes
                            x = (parseFloat(target.getAttribute('data-x')) || 0) + event.dx,
                            y = (parseFloat(target.getAttribute('data-y')) || 0) + event.dy;

                        // translate the element
                        target.style.transform = 'translate(' + x + 'px, ' + y + 'px)';

                        // update the posiion attributes
                        target.setAttribute('data-x', x);
                        target.setAttribute('data-y', y);
                    },
                },
                inertia: true,
                modifiers: [
                    interact.modifiers.restrictRect({
                        restriction: '.cdk-overlay-container',
                        endOnly: true,
                    }),
                ],
            });
        this.setPositionAndScale();
    }

    ngOnDestroy() {
        super.ngOnDestroy();
        this.navigationSubscription.unsubscribe();
        this.toggleScrollLock(false);
    }

    setPositionAndScale() {
        const cntRect = (this.document.querySelector('.cdk-overlay-container') as HTMLElement)?.getBoundingClientRect();
        if (!cntRect) {
            return;
        }

        const initX = this.fullSize ? (cntRect.width * (1 - this.fullWidthFactor)) / 2.0 : cntRect.width - this.initialWidth - 20;
        const initY = this.fullSize ? (cntRect.height * (1 - this.fullHeightFactor)) / 2.0 : cntRect.height - this.initialHeight - 20;

        const nE = this.document.querySelector('.chat-widget') as HTMLElement;
        nE.style.transform = `translate(${initX}px, ${initY}px)`;
        nE.setAttribute('data-x', String(initX));
        nE.setAttribute('data-y', String(initY));

        // Set width and height
        if (this.fullSize) {
            nE.style.width = `${cntRect.width * this.fullWidthFactor}px`;
            nE.style.height = `${cntRect.height * this.fullHeightFactor}px`;
        } else {
            nE.style.width = `${this.initialWidth}px`;
            nE.style.height = `${this.initialHeight}px`;
        }
    }

    /**
     * Maximizes the chat widget screen.
     */
    maximizeScreen() {
        this.fullSize = true;
        this.setPositionAndScale();
    }

    /**
     * Minimizes the chat widget screen.
     */
    minimizeScreen() {
        this.fullSize = false;
        this.setPositionAndScale();
    }
}
