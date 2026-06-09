package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;

import de.tum.cit.aet.artemis.iris.dto.IrisFullscreenContextDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisMessageContextDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisSlidesContextDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisVideoContextDTO;

class IrisMessageContextDTOTest {

    private ObjectMapper mapper;

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @BeforeEach
    void setup() {
        // Configure ObjectMapper with polymorphic type handling for sealed interfaces
        PolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder().allowIfSubType(IrisMessageContextDTO.class).build();
        mapper = new ObjectMapper();
        mapper.activateDefaultTyping(ptv, ObjectMapper.DefaultTyping.OBJECT_AND_NON_CONCRETE);
    }

    @Test
    void serializesVideoContext() throws Exception {
        var dto = new IrisVideoContextDTO(42L, 125.5);
        String json = mapper.writeValueAsString(dto);

        assertThat(json).contains("\"type\":\"video\"");
        assertThat(json).contains("\"lectureUnitId\":42");
        assertThat(json).contains("\"timestamp\":125.5");
    }

    @Test
    void deserializesVideoContext() throws Exception {
        // Test that video context can be deserialized from JSON
        var original = new IrisVideoContextDTO(42L, 125.5);
        String json = mapper.writeValueAsString(original);
        IrisMessageContextDTO dto = mapper.readValue(json, IrisVideoContextDTO.class);

        assertThat(dto).isInstanceOf(IrisVideoContextDTO.class);
        var videoContext = (IrisVideoContextDTO) dto;
        assertThat(videoContext.lectureUnitId()).isEqualTo(42L);
        assertThat(videoContext.timestamp()).isEqualTo(125.5);
        assertThat(videoContext.type()).isEqualTo("video");
    }

    @Test
    void serializesSlidesContext() throws Exception {
        var dto = new IrisSlidesContextDTO(100L, 5);
        String json = mapper.writeValueAsString(dto);

        assertThat(json).contains("\"type\":\"slides\"");
        assertThat(json).contains("\"lectureUnitId\":100");
        assertThat(json).contains("\"page\":5");
    }

    @Test
    void deserializesSlidesContext() throws Exception {
        // Test that slides context can be deserialized from JSON
        var original = new IrisSlidesContextDTO(100L, 5);
        String json = mapper.writeValueAsString(original);
        IrisMessageContextDTO dto = mapper.readValue(json, IrisSlidesContextDTO.class);

        assertThat(dto).isInstanceOf(IrisSlidesContextDTO.class);
        var slidesContext = (IrisSlidesContextDTO) dto;
        assertThat(slidesContext.lectureUnitId()).isEqualTo(100L);
        assertThat(slidesContext.page()).isEqualTo(5);
        assertThat(slidesContext.type()).isEqualTo("slides");
    }

    @Test
    void videoContextValidationRequiresPositiveLectureUnitId() {
        var dto = new IrisVideoContextDTO("video", 0L, 10.0);
        var violations = validator.validate(dto);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("lectureUnitId"));
    }

    @Test
    void slidesContextValidationRequiresPageGreaterThanZero() {
        var dto = new IrisSlidesContextDTO("slides", 1L, 0);
        var violations = validator.validate(dto);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("page"));
    }

    @Test
    void serializesFullscreenContext() throws Exception {
        var dto = new IrisFullscreenContextDTO(123L);
        String json = mapper.writeValueAsString(dto);

        assertThat(json).contains("\"type\":\"fullscreen\"");
        assertThat(json).contains("\"lectureUnitId\":123");
    }

    @Test
    void deserializesFullscreenContext() throws Exception {
        // Test that fullscreen context can be deserialized from JSON
        var original = new IrisFullscreenContextDTO(123L);
        String json = mapper.writeValueAsString(original);
        IrisMessageContextDTO dto = mapper.readValue(json, IrisFullscreenContextDTO.class);

        assertThat(dto).isInstanceOf(IrisFullscreenContextDTO.class);
        var fullscreenContext = (IrisFullscreenContextDTO) dto;
        assertThat(fullscreenContext.lectureUnitId()).isEqualTo(123L);
        assertThat(fullscreenContext.type()).isEqualTo("fullscreen");
    }

    @Test
    void fullscreenContextValidationRequiresPositiveLectureUnitId() {
        var dto = new IrisFullscreenContextDTO("fullscreen", 0L);
        var violations = validator.validate(dto);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("lectureUnitId"));
    }

    @Test
    void polymorphicTypeHandlingWithFullscreenAndOtherContexts() throws Exception {
        // Test that fullscreen context works together with video/slides contexts
        var fullscreenContext = new IrisFullscreenContextDTO(123L);
        var videoContext = new IrisVideoContextDTO(123L, 45.2);
        var slidesContext = new IrisSlidesContextDTO(123L, 5);

        List<IrisMessageContextDTO> contexts = List.of(fullscreenContext, videoContext, slidesContext);

        assertThat(contexts).hasSize(3);
        assertThat(contexts.get(0)).isInstanceOf(IrisFullscreenContextDTO.class);
        assertThat(contexts.get(1)).isInstanceOf(IrisVideoContextDTO.class);
        assertThat(contexts.get(2)).isInstanceOf(IrisSlidesContextDTO.class);

        var fullscreen = (IrisFullscreenContextDTO) contexts.get(0);
        assertThat(fullscreen.lectureUnitId()).isEqualTo(123L);

        var video = (IrisVideoContextDTO) contexts.get(1);
        assertThat(video.lectureUnitId()).isEqualTo(123L);
        assertThat(video.timestamp()).isEqualTo(45.2);

        var slides = (IrisSlidesContextDTO) contexts.get(2);
        assertThat(slides.lectureUnitId()).isEqualTo(123L);
        assertThat(slides.page()).isEqualTo(5);
    }
}
