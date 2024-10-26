import { AfterViewInit, Component, HostListener, Inject, OnDestroy } from '@angular/core';
import interact from 'interactjs';
import { DOCUMENT } from '@angular/common';
import { MatDialog } from '@angular/material/dialog';
import { NavigationStart, Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { ButtonType } from 'app/shared/components/button.component';
import { IrisModule } from 'app/iris/iris.module';

@Component({
    selector: 'jhi-chatbot-widget',
    templateUrl: './chatbot-widget.component.html',
    styleUrls: ['./chatbot-widget.component.scss'],
    standalone: true,
    imports: [IrisModule],
})
export class IrisChatbotWidgetComponent implements OnDestroy, AfterViewInit {
    // User preferences
    initialWidth = 400;
    initialHeight = 600;
    fullWidthFactor = 0.93;
    fullHeightFactor = 0.85;
    isMobile = false;
    fullSize = false;
    public ButtonType = ButtonType;

    protected navigationSubscription: Subscription;

    constructor(
        @Inject(DOCUMENT) private document: Document,
        private router: Router,
        private dialog: MatDialog,
    ) {
        this.navigationSubscription = this.router.events.subscribe((event) => {
            if (event instanceof NavigationStart) {
                this.dialog.closeAll();
            }
        });
    }

    @HostListener('window:resize', ['$event'])
    onResize() {
        this.setPositionAndScale();
    }

    ngAfterViewInit() {
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

    setPositionAndScale() {
        const cntRect = (this.document.querySelector('.cdk-overlay-container') as HTMLElement)?.getBoundingClientRect();
        if (!cntRect) {
            return;
        }

        this.isMobile = cntRect.width < 600;

        let initX: number;
        let initY: number;

        if (this.fullSize || this.isMobile) {
            initX = (cntRect.width * (1 - this.fullWidthFactor)) / 2.0;
            initY = (cntRect.height * (1 - this.fullHeightFactor)) / 2.0;
        } else {
            initX = cntRect.width - this.initialWidth - 20;
            initY = cntRect.height - this.initialHeight - 20;
        }

        const nE = this.document.querySelector('.chat-widget') as HTMLElement;
        nE.style.transform = `translate(${initX}px, ${initY}px)`;
        nE.setAttribute('data-x', String(initX));
        nE.setAttribute('data-y', String(initY));

        // Set width and height
        if (this.fullSize || this.isMobile) {
            nE.style.width = `${cntRect.width * this.fullWidthFactor}px`;
            nE.style.height = `${cntRect.height * this.fullHeightFactor}px`;
        } else {
            nE.style.width = `${this.initialWidth}px`;
            nE.style.height = `${this.initialHeight}px`;
        }
    }

    ngOnDestroy() {
        this.toggleScrollLock(false);
    }

    /**
     * Closes the chat widget.
     */
    closeChat() {
        this.dialog.closeAll();
    }

    toggleFullSize() {
        this.fullSize = !this.fullSize;
        this.setPositionAndScale();
    }

    toggleScrollLock(lockParent: boolean): void {
        if (lockParent) {
            document.body.classList.add('cdk-global-scroll');
        } else {
            document.body.classList.remove('cdk-global-scroll');
        }
    }
}
