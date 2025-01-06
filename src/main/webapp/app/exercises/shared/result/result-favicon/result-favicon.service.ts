import { Injectable } from '@angular/core';
import { Result } from 'app/entities/result.model';
import { MissingResultInformation, ResultTemplateStatus, evaluateTemplateStatus, getResultIconClass, getTextColorClass } from '../result.utils';
import { Exercise } from 'app/entities/exercise.model';
import { Participation } from 'app/entities/participation/participation.model';
import { IconDefinition } from '@fortawesome/fontawesome-common-types';

/**
 * A service that updates the favicon of the page based on the result of an exercise. Adds a small badge to the favicon that indicates the status of the result.
 */
@Injectable({
    providedIn: 'root',
})
export class ResultFaviconService {
    // Experimented with different values here, these seem to work best
    private readonly FAVICON_SCALE_FACTOR = 1.75;
    private readonly FAVICON_LOADING_UPDATE_INTERVAL = 200;
    private readonly FAVICON_ROTATION_SPEED = 1;

    private originalFaviconSrcMap = new Map<HTMLLinkElement, string>();
    private isFaviconReplaced = false;
    private cleanupFunctions = new Set<() => void>(); // Not an array since arrays are immutable by default according to the client guidelines

    private getSVGPathFromIcon(icon: IconDefinition) {
        return icon.icon[4].toString();
    }

    private getIconSize(icon: IconDefinition) {
        return Math.max(icon.icon[0], icon.icon[1]);
    }

    private getColorFromClass(className: string) {
        const temporaryDiv = document.createElement('div');
        temporaryDiv.classList.add(className);
        document.body.appendChild(temporaryDiv);
        const computedColor = getComputedStyle(temporaryDiv).getPropertyValue('color');
        document.body.removeChild(temporaryDiv);

        return computedColor;
    }

    /**
     * Updates the favicon of the page. If the favicon has already been replaced, it will be removed first.
     *
     * @param result The result of the exercise
     * @param exercise The exercise the result belongs to
     * @param participation The participation the result belongs to
     * @param isBuilding Whether the result is currently being built
     * @param isQueued Whether the result is currently queued
     * @param missingResultInfo Information about missing results
     */
    updateFavicon(
        result: Result | undefined,
        exercise: Exercise,
        participation: Participation,
        isBuilding: boolean,
        isQueued: boolean,
        missingResultInfo: MissingResultInformation | undefined,
    ) {
        if (this.isFaviconReplaced) this.removeFavicon();

        this.isFaviconReplaced = true;

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

                    const iconPathObject = new Path2D(iconPath as string);

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
                    // We're not using requestAnimationFrame here because it won't update when the tab is in the background
                    const interval = setInterval(drawBadge, this.FAVICON_LOADING_UPDATE_INTERVAL);
                    this.cleanupFunctions.add(() => clearInterval(interval));
                }
            };
            img.addEventListener('load', createBadge);

            this.cleanupFunctions.add(() => {
                img.removeEventListener('load', createBadge);
            });

            img.src = originalHref;
            this.originalFaviconSrcMap.set(faviconLinkElement, originalHref);
        });
    }

    /**
     * Removes the custom favicon from the page and restores the original favicon.
     */
    removeFavicon() {
        if (!this.isFaviconReplaced) return;

        this.originalFaviconSrcMap.forEach((originalSrc, linkElement) => {
            linkElement.href = originalSrc;
        });

        this.originalFaviconSrcMap.clear();

        this.cleanupFunctions.forEach((fn) => fn());
        this.cleanupFunctions.clear();

        this.isFaviconReplaced = false;
    }
}
