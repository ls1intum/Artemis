package de.tum.cit.aet.artemis.lecture.service;

import java.util.List;

import de.tum.cit.aet.artemis.lecture.domain.LectureTranscriptionSegment;

/** Parsed transcription checkpoint data, as Iris sends it while a unit is in the TRANSCRIBING phase. */
record TranscriptionCheckpoint(String language, List<LectureTranscriptionSegment> segments, boolean isEnriched) {
}
