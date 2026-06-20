import { Component, OnInit, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { AlertService } from 'app/foundation/service/alert.service';
import { GocastBindingStatus, GocastStream } from './gocast.model';
import { GocastService } from './gocast.service';

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
    imports: [FormsModule, TranslateDirective, ArtemisTranslatePipe, RouterLink],
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

    /** Emitted when a stream is selected (streamId, stream name). */
    streamSelected = output<{ streamId: number; streamName: string; slug?: string }>();

    // ── state ───────────────────────────────────────────────────────────────
    streams = signal<GocastStream[]>([]);
    isLoading = signal(false);
    selectedStreamId = signal<number | undefined>(undefined);
    /** Resolved binding status — either from the input or fetched on init. */
    bindingStatus = signal<GocastBindingStatus | undefined>(undefined);

    ngOnInit(): void {
        const inputBinding = this.hasActiveBinding();
        if (inputBinding !== undefined) {
            // Caller provided the binding state — skip the check.
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
            next: (binding) => {
                this.bindingStatus.set(binding.status);
                if (binding.status === 'ACTIVE') {
                    this.loadStreams();
                }
            },
            error: () => {
                // 404 or other error → treat as no binding
                this.bindingStatus.set(undefined);
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
     * Called when the instructor selects a stream from the dropdown.
     * Emits `streamSelected` with the chosen stream's id and name.
     */
    onStreamSelected(event: Event): void {
        const streamId = Number((event.target as HTMLSelectElement).value);
        if (!streamId) {
            return;
        }
        const stream = this.streams().find((s) => s.streamId === streamId);
        if (stream) {
            this.selectedStreamId.set(stream.streamId);
            this.streamSelected.emit({ streamId: stream.streamId, streamName: stream.name });
        }
    }
}
