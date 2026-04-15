package de.tum.cit.aet.artemis.lecture.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.dto.SlideDTO;
import de.tum.cit.aet.artemis.lecture.web.LectureResource.AttachmentVideoUnitDTO;
import de.tum.cit.aet.artemis.videosource.domain.VideoSourceType;
import de.tum.cit.aet.artemis.videosource.service.ResolvedVideo;
import de.tum.cit.aet.artemis.videosource.service.VideoSourceResolver;
import de.tum.cit.aet.artemis.videosource.service.YouTubeUrlService;

class LectureResourceAttachmentVideoUnitDTOTest {

    private static final Long ID = 1L;

    private static final String NAME = "unit";

    private static final List<SlideDTO> SLIDES = List.of();

    private static final ZonedDateTime RELEASE = ZonedDateTime.now();

    private static final String TYPE = "attachment";

    @Test
    void youTubeTypeRequiresYouTubeVideoId() {
        assertThrows(IllegalArgumentException.class,
                () -> new AttachmentVideoUnitDTO(ID, NAME, SLIDES, null, RELEASE, TYPE, "https://youtube.com/watch?v=dQw4w9WgXcQ", VideoSourceType.YOUTUBE, null, null));
    }

    @Test
    void tumLiveTypeForbidsYouTubeVideoId() {
        assertThrows(IllegalArgumentException.class,
                () -> new AttachmentVideoUnitDTO(ID, NAME, SLIDES, null, RELEASE, TYPE, "https://live.rbg.tum.de/foo", VideoSourceType.TUM_LIVE, "dQw4w9WgXcQ", null));
    }

    @Test
    void nullTypeForbidsYouTubeVideoId() {
        assertThrows(IllegalArgumentException.class, () -> new AttachmentVideoUnitDTO(ID, NAME, SLIDES, null, RELEASE, TYPE, "https://vimeo.com/1", null, "dQw4w9WgXcQ", null));
    }

    @Test
    void validYouTubeDtoConstructs() {
        var dto = new AttachmentVideoUnitDTO(ID, NAME, SLIDES, null, RELEASE, TYPE, "https://youtu.be/dQw4w9WgXcQ", VideoSourceType.YOUTUBE, "dQw4w9WgXcQ", null);
        assertThat(dto.videoSourceType()).isEqualTo(VideoSourceType.YOUTUBE);
        assertThat(dto.youtubeVideoId()).isEqualTo("dQw4w9WgXcQ");
    }

    @Test
    void validTumLiveDtoConstructsWithNullYouTubeVideoId() {
        var dto = new AttachmentVideoUnitDTO(ID, NAME, SLIDES, null, RELEASE, TYPE, "https://live.rbg.tum.de/pl.m3u8", VideoSourceType.TUM_LIVE, null, null);
        assertThat(dto.videoSourceType()).isEqualTo(VideoSourceType.TUM_LIVE);
        assertThat(dto.youtubeVideoId()).isNull();
    }

    @Test
    void nullTypeDtoWithNullVideoIdConstructs() {
        var dto = new AttachmentVideoUnitDTO(ID, NAME, SLIDES, null, RELEASE, TYPE, "https://vimeo.com/123", null, null, null);
        assertThat(dto.videoSourceType()).isNull();
        assertThat(dto.youtubeVideoId()).isNull();
    }

    @Test
    void nullVideoSourceConstructs() {
        var dto = new AttachmentVideoUnitDTO(ID, NAME, SLIDES, null, RELEASE, TYPE, null, null, null, null);
        assertThat(dto.videoSource()).isNull();
        assertThat(dto.videoSourceType()).isNull();
    }

    @Test
    void transcriptionErrorCodeIsExposed() {
        var dto = new AttachmentVideoUnitDTO(ID, NAME, SLIDES, null, RELEASE, TYPE, "https://youtu.be/dQw4w9WgXcQ", VideoSourceType.YOUTUBE, "dQw4w9WgXcQ", "YOUTUBE_LIVE");
        assertThat(dto.transcriptionErrorCode()).isEqualTo("YOUTUBE_LIVE");
    }

    // --- factory method wiring tests ---

    @Test
    void factoryPropagatesTranscriptionErrorCode() {
        var unit = new AttachmentVideoUnit();
        unit.setVideoSource("https://youtu.be/dQw4w9WgXcQ");

        var resolver = mock(VideoSourceResolver.class);
        var ytService = mock(YouTubeUrlService.class);
        when(resolver.resolve("https://youtu.be/dQw4w9WgXcQ")).thenReturn(new ResolvedVideo("https://youtu.be/dQw4w9WgXcQ", VideoSourceType.YOUTUBE));
        when(ytService.extractYouTubeVideoId("https://youtu.be/dQw4w9WgXcQ")).thenReturn(Optional.of("dQw4w9WgXcQ"));

        var dto = AttachmentVideoUnitDTO.from(unit, resolver, ytService, "YOUTUBE_LIVE");

        assertThat(dto.transcriptionErrorCode()).isEqualTo("YOUTUBE_LIVE");
    }

    @Test
    void factoryWithNullErrorCodeProducesNullTranscriptionErrorCode() {
        var unit = new AttachmentVideoUnit();
        unit.setVideoSource("https://youtu.be/dQw4w9WgXcQ");

        var resolver = mock(VideoSourceResolver.class);
        var ytService = mock(YouTubeUrlService.class);
        when(resolver.resolve("https://youtu.be/dQw4w9WgXcQ")).thenReturn(new ResolvedVideo("https://youtu.be/dQw4w9WgXcQ", VideoSourceType.YOUTUBE));
        when(ytService.extractYouTubeVideoId("https://youtu.be/dQw4w9WgXcQ")).thenReturn(Optional.of("dQw4w9WgXcQ"));

        var dto = AttachmentVideoUnitDTO.from(unit, resolver, ytService, null);

        assertThat(dto.transcriptionErrorCode()).isNull();
    }
}
