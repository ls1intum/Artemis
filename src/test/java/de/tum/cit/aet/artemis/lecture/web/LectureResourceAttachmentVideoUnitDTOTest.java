package de.tum.cit.aet.artemis.lecture.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.dto.SlideDTO;
import de.tum.cit.aet.artemis.lecture.web.LectureResource.AttachmentVideoUnitDTO;
import de.tum.cit.aet.artemis.videosource.domain.VideoSourceType;
import de.tum.cit.aet.artemis.videosource.service.YouTubeUrlService;

class LectureResourceAttachmentVideoUnitDTOTest {

    private static final Long ID = 1L;

    private static final String NAME = "unit";

    private static final List<SlideDTO> SLIDES = List.of();

    private static final ZonedDateTime RELEASE = ZonedDateTime.now();

    private static final String TYPE = "attachment";

    @Test
    void youTubeTypeRequiresYouTubeVideoId() {
        assertThatThrownBy(() -> new AttachmentVideoUnitDTO(ID, NAME, SLIDES, null, RELEASE, TYPE, "https://youtube.com/watch?v=dQw4w9WgXcQ", VideoSourceType.YOUTUBE, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tumLiveTypeForbidsYouTubeVideoId() {
        assertThatThrownBy(() -> new AttachmentVideoUnitDTO(ID, NAME, SLIDES, null, RELEASE, TYPE, "https://live.rbg.tum.de/foo", VideoSourceType.TUM_LIVE, "dQw4w9WgXcQ"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullTypeForbidsYouTubeVideoId() {
        assertThatThrownBy(() -> new AttachmentVideoUnitDTO(ID, NAME, SLIDES, null, RELEASE, TYPE, "https://vimeo.com/1", null, "dQw4w9WgXcQ"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validYouTubeDtoConstructs() {
        var dto = new AttachmentVideoUnitDTO(ID, NAME, SLIDES, null, RELEASE, TYPE, "https://youtu.be/dQw4w9WgXcQ", VideoSourceType.YOUTUBE, "dQw4w9WgXcQ");
        assertThat(dto.videoSourceType()).isEqualTo(VideoSourceType.YOUTUBE);
        assertThat(dto.youtubeVideoId()).isEqualTo("dQw4w9WgXcQ");
    }

    @Test
    void validTumLiveDtoConstructsWithNullYouTubeVideoId() {
        var dto = new AttachmentVideoUnitDTO(ID, NAME, SLIDES, null, RELEASE, TYPE, "https://live.rbg.tum.de/pl.m3u8", VideoSourceType.TUM_LIVE, null);
        assertThat(dto.videoSourceType()).isEqualTo(VideoSourceType.TUM_LIVE);
        assertThat(dto.youtubeVideoId()).isNull();
    }

    @Test
    void nullTypeDtoWithNullVideoIdConstructs() {
        var dto = new AttachmentVideoUnitDTO(ID, NAME, SLIDES, null, RELEASE, TYPE, "https://vimeo.com/123", null, null);
        assertThat(dto.videoSourceType()).isNull();
        assertThat(dto.youtubeVideoId()).isNull();
    }

    @Test
    void nullVideoSourceConstructs() {
        var dto = new AttachmentVideoUnitDTO(ID, NAME, SLIDES, null, RELEASE, TYPE, null, null, null);
        assertThat(dto.videoSource()).isNull();
        assertThat(dto.videoSourceType()).isNull();
    }

    // --- factory method wiring tests ---

    private static final YouTubeUrlService YOUTUBE_URL_SERVICE = new YouTubeUrlService();

    @Test
    void factoryYouTubeUrlProducesYouTubeTypeAndId() {
        var unit = new AttachmentVideoUnit();
        unit.setVideoSource("https://youtu.be/dQw4w9WgXcQ");

        var dto = AttachmentVideoUnitDTO.from(unit, YOUTUBE_URL_SERVICE);

        assertThat(dto.videoSource()).isEqualTo("https://youtu.be/dQw4w9WgXcQ");
        assertThat(dto.videoSourceType()).isEqualTo(VideoSourceType.YOUTUBE);
        assertThat(dto.youtubeVideoId()).isEqualTo("dQw4w9WgXcQ");
    }

    /**
     * TUM Live URLs are not YouTube URLs, so the factory must leave videoSourceType null.
     * The client resolves TUM Live playlist URLs on-demand via a separate endpoint.
     * The raw videoSource URL must be exposed unchanged so the client can pass it to that endpoint.
     */
    @Test
    void factoryTumLiveUrlProducesNullTypeAndExposesRawUrl() {
        String watchUrl = "https://live.rbg.tum.de/w/foo/123";

        var unit = new AttachmentVideoUnit();
        unit.setVideoSource(watchUrl);

        var dto = AttachmentVideoUnitDTO.from(unit, YOUTUBE_URL_SERVICE);

        assertThat(dto.videoSource()).isEqualTo(watchUrl);
        assertThat(dto.videoSourceType()).isNull();
        assertThat(dto.youtubeVideoId()).isNull();
    }
}
