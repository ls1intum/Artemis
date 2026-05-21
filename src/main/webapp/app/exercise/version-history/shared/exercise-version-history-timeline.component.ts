import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { addPublicFilePrefix } from 'app/app.constants';
import { ExerciseVersionMetadata } from 'app/exercise/version-history/shared/exercise-version-history.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ProfilePictureComponent } from 'app/shared/profile-picture/profile-picture.component';
import { ButtonModule } from 'primeng/button';
import { MessageModule } from 'primeng/message';
import { SkeletonModule } from 'primeng/skeleton';
import { TimelineModule } from 'primeng/timeline';

/** View-model for a single entry rendered in the PrimeNG Timeline. */
interface TimelineEntryViewModel {
    id: number;
    createdDate?: ExerciseVersionMetadata['createdDate'];
    authorId?: number;
    authorImageUrl?: string;
    displayName: string;
    secondaryName?: string;
    login?: string;
}

/**
 * Vertical timeline that lists exercise versions with author avatars and dates.
 *
 * This is a presentational (dumb) component — all data flows in via inputs and
 * user interactions flow out via outputs. It supports skeleton loading, an empty
 * state message, and a "load more" button for server-side pagination.
 */
@Component({
    selector: 'jhi-exercise-version-history-timeline',
    templateUrl: './exercise-version-history-timeline.component.html',
    styleUrls: ['./exercise-version-history-timeline.component.scss'],
    imports: [TranslateDirective, ArtemisDatePipe, ButtonModule, MessageModule, SkeletonModule, TimelineModule, ProfilePictureComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ExerciseVersionHistoryTimelineComponent {
    /** Version metadata entries to display, ordered newest-first. */
    readonly versions = input.required<ExerciseVersionMetadata[]>();
    /** Id of the currently selected version (highlighted in the UI). */
    readonly selectedVersionId = input<number>();
    /** Whether additional pages are available on the server. */
    readonly hasMore = input(false);
    /** Whether the initial page is being fetched. */
    readonly loading = input(false);
    /** Whether an additional page is currently being fetched. */
    readonly loadingMore = input(false);
    /** Total number of versions across all pages (shown in the header). */
    readonly totalItems = input(0);

    /** Emitted when the user clicks a version card. Payload is the version id. */
    readonly selectVersion = output<number>();
    /** Emitted when the user clicks the "load more" button. */
    readonly loadMore = output<void>();

    /**
     * Transforms raw {@link ExerciseVersionMetadata} entries into view-model
     * objects suitable for the PrimeNG Timeline template, resolving display
     * names and profile picture URLs.
     */
    readonly timelineEntries = computed<TimelineEntryViewModel[]>(() =>
        this.versions().map((version) => {
            const name = version.author?.name?.trim();
            const login = version.author?.login?.trim();
            const displayName = name || login || '-';
            const secondaryName = login ? `#${version.id} \u00b7 @${login}` : `#${version.id}`;

            return {
                id: version.id,
                createdDate: version.createdDate,
                authorId: version.author?.id,
                authorImageUrl: version.author?.imageUrl ? addPublicFilePrefix(version.author.imageUrl) : undefined,
                displayName,
                secondaryName,
                login,
            };
        }),
    );

    /** Forwards a version selection to the parent. */
    onSelect(versionId: number): void {
        this.selectVersion.emit(versionId);
    }

    /** Forwards a "load more" request to the parent. */
    onLoadMore(): void {
        this.loadMore.emit();
    }
}
