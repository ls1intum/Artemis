import { Component } from '@angular/core';
import { VisibleOnScrollDirective } from './visible-on-scroll.directive';

@Component({
    selector: 'jhi-landing-glowbar',
    standalone: true,
    imports: [VisibleOnScrollDirective],
    template: `
        <div class="gb-wrapper" jhiVisibleOnScroll [threshold]="0">
            <div class="gb-inner">
                <div class="gb-glower gb-a"></div>
                <div class="gb-glower gb-b"></div>
            </div>
        </div>
    `,
    styles: `
        :host {
            display: block;
            position: relative;
            overflow: visible;
            margin: 7rem 0 2.8rem;
        }

        .gb-wrapper {
            /* 1px height so IntersectionObserver can detect it */
            height: 1px;
            width: 100%;
            position: relative;
            overflow: visible;
        }

        .gb-inner {
            height: 3000px;
            width: 100%;
            position: absolute;
            top: -1500px;
            display: flex;
            pointer-events: none;
            mask-image: linear-gradient(to bottom, transparent 1480px, black 50%, black 70%, transparent 90%);
            -webkit-mask-image: linear-gradient(to bottom, transparent 1480px, black 50%, black 70%, transparent 90%);
            transition: opacity 0.7s ease-in-out;
            opacity: 0;
        }

        .gb-glower {
            height: 100%;
            width: 50%;
        }

        .gb-glower.gb-a {
            background: conic-gradient(from 90deg at calc(100% - 200px) 50%, var(--glow-color, #2f6fb1) 0%, transparent 50%);
        }

        .gb-glower.gb-b {
            background: conic-gradient(from 270deg at 200px 50%, var(--glow-color, #2f6fb1), transparent 50%);
            transform: rotateX(180deg);
        }

        :host .gb-wrapper.visible .gb-inner {
            opacity: 1;
        }
    `,
})
export class LandingGlowbarComponent {}
