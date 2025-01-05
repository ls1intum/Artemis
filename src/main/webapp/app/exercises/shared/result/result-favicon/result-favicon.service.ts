import { Injectable } from '@angular/core';
import { Result } from 'app/entities/result.model';
import { MissingResultInformation, ResultTemplateStatus, evaluateTemplateStatus, getResultIconClass, getTextColorClass } from '../result.utils';
import { Exercise } from 'app/entities/exercise.model';
import { Participation } from 'app/entities/participation/participation.model';
import { IconProp } from '@fortawesome/angular-fontawesome/types';

@Injectable({
    providedIn: 'root',
})
export class ResultFaviconService {
    // experimented with different values here, these seem to work best
    private readonly FAVICON_SCALE_FACTOR = 1.75;
    private readonly FAVICON_LOADING_UPDATE_INTERVAL = 200;
    private readonly FAVICON_ROTATION_SPEED = 1;

    private originalFaviconSrcMap = new Map<HTMLLinkElement, string>();
    private isFaviconReplaced = false;
    private cleanupFunctions: (() => void)[] = [];

    constructor() {}

    private getSVGPathFromIcon(icon: IconProp) {
        // todo: find a better way to do this. this is bad.
        return (icon as any).icon[4];
    }

    private getIconSize(icon: IconProp) {
        // todo: again, this isn't ideal
        return Math.max((icon as any).icon[0], (icon as any).icon[1]);
    }

    private getColorFromClass(className: string) {
        const temporaryDiv = document.createElement('div');
        temporaryDiv.classList.add(className);
        document.body.appendChild(temporaryDiv);
        const computedColor = getComputedStyle(temporaryDiv).getPropertyValue('color');
        document.body.removeChild(temporaryDiv);

        return computedColor;
    }

    updateFavicon(
        result: Result | undefined,
        exercise: Exercise,
        participation: Participation,
        isBuilding: boolean,
        missingResultInfo: MissingResultInformation | undefined,
        isQueued: boolean,
    ) {
        if (this.isFaviconReplaced) this.removeFavicon();

        const templateStatus = evaluateTemplateStatus(exercise, participation, result, isBuilding, missingResultInfo, isQueued);
        const shouldBeLoading = [ResultTemplateStatus.IS_BUILDING, ResultTemplateStatus.IS_QUEUED, ResultTemplateStatus.IS_GENERATING_FEEDBACK].includes(templateStatus);

        const iconClass = getResultIconClass(result, templateStatus);
        const iconPath = this.getSVGPathFromIcon(iconClass);
        const iconSize = this.getIconSize(iconClass);
        const iconColorClass = getTextColorClass(result, templateStatus);
        const iconColor = this.getColorFromClass(iconColorClass);

        document.querySelectorAll('link[rel="shortcut icon"], link[rel="icon"]').forEach((faviconLinkElement: HTMLLinkElement) => {
            const originalHref = faviconLinkElement.href;

            const faviconSize = iconSize * this.FAVICON_SCALE_FACTOR;
            const canvas = document.createElement('canvas');

            canvas.width = faviconSize;
            canvas.height = faviconSize;

            const context = canvas.getContext('2d');
            const img = document.createElement('img');

            const createBadge = () => {
                if (!context) return;

                const drawBadge = () => {
                    context.save();

                    context.clearRect(0, 0, faviconSize, faviconSize);

                    context.drawImage(img, 0, 0, faviconSize, faviconSize);

                    context.fillStyle = iconColor;

                    const iconPathObject = new Path2D(iconPath);

                    context.translate(faviconSize - iconSize / 2, faviconSize - iconSize / 2);
                    if (shouldBeLoading) {
                        context.rotate((Date.now() / (this.FAVICON_ROTATION_SPEED * 1000)) % (2 * Math.PI));
                    }

                    context.translate(-iconSize / 2, -iconSize / 2);
                    context.fill(iconPathObject);

                    context.restore();

                    faviconLinkElement.href = canvas.toDataURL('image/png');
                };

                drawBadge();

                if (shouldBeLoading) {
                    const interval = setInterval(drawBadge, this.FAVICON_LOADING_UPDATE_INTERVAL);
                    this.cleanupFunctions.push(() => clearInterval(interval));
                }
            };
            img.addEventListener('load', createBadge);

            // this shouldn't actually be needed since the images are probably loaded
            // already, but better safe than sorry
            this.cleanupFunctions.push(() => {
                img.removeEventListener('load', createBadge);
            });

            img.src = originalHref;
            this.originalFaviconSrcMap.set(faviconLinkElement, originalHref);
        });

        this.isFaviconReplaced = true;
    }

    removeFavicon() {
        if (!this.isFaviconReplaced) return;

        this.originalFaviconSrcMap.forEach((originalSrc, linkElement) => {
            linkElement.href = originalSrc;
        });

        this.originalFaviconSrcMap.clear();

        this.cleanupFunctions.forEach((fn) => fn());
        this.cleanupFunctions.length = 0;

        this.isFaviconReplaced = false;
    }
}
