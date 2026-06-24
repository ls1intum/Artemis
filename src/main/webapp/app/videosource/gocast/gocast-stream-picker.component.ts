import { Component, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MessageModule } from 'primeng/message';
import { SelectModule } from 'primeng/select';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { AlertService } from 'app/foundation/service/alert.service';
import { GocastBindingStatus, GocastBindingWithApproval, GocastStream } from './gocast.model';
import { GocastService } from './gocast.service';

/** A single option in the TUM Live stream p-select dropdown. */
interface GocastStreamOption {
    label: string;
    value: number;
    private: boolean;
}

/**
 * Stage 2 — Stream picker for the lecture-unit form.
 *
 * When courseId is provided:
 * - Checks the binding status of the Artemis course on init.
 * - If the course has an ACTIVE gocast binding, loads streams and shows the dropdown.
 * - If no ACTIVE binding, shows a hint linking to the course binding settings.
 *
 * The hasActiveBinding input can also be passed directly to skip the binding check (e.g. when
 * the parent already knows the status). If not provided, the component resolves it from the server.
 *
 * Emits `streamSelected` when the instructor picks a stream.
 */
@Component({
    selector: 'jhi-gocast-stream-picker',
    templateUrl: './gocast-stream-picker.component.html',
    imports: [FormsModule, TranslateDirective, ArtemisTranslatePipe, MessageModule, SelectModule],
})
export class GocastStreamPickerComponent implements OnInit {
    private readonly gocastService = inject(GocastService);
    private readonly alertService = inject(AlertService);

    /** The Artemis course id, used to fetch streams for the bound gocast course. */
    courseId = input.required<number>();

    /**
     * Optional: Whether the course already has an ACTIVE gocast binding (caller-resolved).
     * When not provided (default undefined), the component resolves the binding status itself on init.
     */
    hasActiveBinding = input<boolean | undefined>(undefined);

    /**
     * Emitted when the stream selection changes.
     * Carries the chosen stream (streamId, name, slug) on selection, or `undefined` when the
     * selection is cleared — so the parent form can reset its cached stream id and avoid
     * submitting a stale value.
     */
    streamSelected = output<{ streamId: number; streamName: string; slug?: string } | undefined>();

    // ── state ───────────────────────────────────────────────────────────────
    streams = signal<GocastStream[]>([]);
    isLoading = signal(false);
    selectedStreamId = signal<number | undefined>(undefined);
    /** Resolved binding status — either from the input or fetched on init. */
    bindingStatus = signal<GocastBindingStatus | undefined>(undefined);
    /**
     * The TUM Live course slug for the active binding.
     * Needed to build the correct watch-page URL: https://tum.live/w/{slug}/{streamId}.
     * Populated from the server binding response; undefined when binding resolves via hasActiveBinding input.
     */
    private boundCourseSlug: string | undefined;

    /** Streams mapped to PrimeNG p-select options (label/value/private). */
    streamOptions = computed<GocastStreamOption[]>(() =>
        this.streams().map((stream) => ({
            label: stream.name,
            value: stream.streamId,
            private: stream.private,
        })),
    );

    ngOnInit(): void {
        const inputBinding = this.hasActiveBinding();
        if (inputBinding !== undefined) {
            // Caller provided the binding state — skip the server check.
            // Note: slug is not available in this path; the form must handle URL building fallback.
            if (inputBinding) {
                this.bindingStatus.set('ACTIVE');
                this.loadStreams();
            } else {
                this.bindingStatus.set('PENDING');
            }
            return;
        }
        // Resolve binding from the server.
        this.gocastService.getBinding(this.courseId()).subscribe({
            next: (response: GocastBindingWithApproval) => {
                this.bindingStatus.set(response.binding.status);
                if (response.binding.status === 'ACTIVE') {
                    this.boundCourseSlug = response.binding.gocastCourseSlug;
                    this.loadStreams();
                }
            },
            error: (err: { status?: number }) => {
                if (err.status === 404) {
                    // 404 → no binding exists; show the "no binding" hint via REVOKED state
                    this.bindingStatus.set('REVOKED');
                }
                // 5xx / other transient errors: leave bindingStatus undefined so the component
                // renders nothing (avoids misleading "no binding" hint for upstream outages)
            },
        });
    }

    private loadStreams(): void {
        this.isLoading.set(true);
        this.gocastService.listTumLiveStreams(this.courseId()).subscribe({
            next: (streams) => {
                this.streams.set(streams);
                this.isLoading.set(false);
            },
            error: () => {
                this.isLoading.set(false);
                this.alertService.error('artemisApp.gocast.streamPicker.error.loadStreams');
            },
        });
    }

    /**
     * Called when the instructor changes the stream dropdown (p-select onChange value).
     * Emits `streamSelected` with the chosen stream, or `undefined` when the selection is cleared
     * (so the parent form drops any stale cached stream id).
     */
    onStreamSelected(streamId: number | undefined): void {
        if (!streamId) {
            this.selectedStreamId.set(undefined);
            this.streamSelected.emit(undefined);
            return;
        }
        const stream = this.streams().find((s) => s.streamId === streamId);
        if (stream) {
            this.selectedStreamId.set(stream.streamId);
            this.streamSelected.emit({ streamId: stream.streamId, streamName: stream.name, slug: this.boundCourseSlug });
        }
    }
}
