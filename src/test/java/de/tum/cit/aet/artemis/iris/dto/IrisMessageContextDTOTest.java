package de.tum.cit.aet.artemis.iris.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;

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
    void videoContextValidationRequiresNonNegativeTimestamp() {
        var dto = new IrisVideoContextDTO("video", 1L, -5.0);
        var violations = validator.validate(dto);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("timestamp"));
    }

    @Test
    void videoContextValidationAllowsZeroTimestamp() {
        var dto = new IrisVideoContextDTO("video", 1L, 0.0);
        var violations = validator.validate(dto);

        assertThat(violations).isEmpty();
    }

    @Test
    void slidesContextValidationRequiresPositiveLectureUnitId() {
        var dto = new IrisSlidesContextDTO("slides", -1L, 1);
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
    void videoContextConvenienceConstructorSetsTypeAutomatically() {
        var dto = new IrisVideoContextDTO(42L, 125.5);

        assertThat(dto.type()).isEqualTo("video");
        assertThat(dto.lectureUnitId()).isEqualTo(42L);
        assertThat(dto.timestamp()).isEqualTo(125.5);
    }

    @Test
    void slidesContextConvenienceConstructorSetsTypeAutomatically() {
        var dto = new IrisSlidesContextDTO(100L, 5);

        assertThat(dto.type()).isEqualTo("slides");
        assertThat(dto.lectureUnitId()).isEqualTo(100L);
        assertThat(dto.page()).isEqualTo(5);
    }

    @Test
    void polymorphicTypeHandlingWithMultipleContexts() throws Exception {
        // Test that mixed context types can be created and accessed
        var videoContext = new IrisVideoContextDTO(1L, 10.5);
        var slidesContext = new IrisSlidesContextDTO(2L, 3);

        List<IrisMessageContextDTO> contexts = List.of(videoContext, slidesContext);

        assertThat(contexts).hasSize(2);
        assertThat(contexts.get(0)).isInstanceOf(IrisVideoContextDTO.class);
        assertThat(contexts.get(1)).isInstanceOf(IrisSlidesContextDTO.class);

        var video = (IrisVideoContextDTO) contexts.get(0);
        assertThat(video.lectureUnitId()).isEqualTo(1L);
        assertThat(video.timestamp()).isEqualTo(10.5);

        var slides = (IrisSlidesContextDTO) contexts.get(1);
        assertThat(slides.lectureUnitId()).isEqualTo(2L);
        assertThat(slides.page()).isEqualTo(3);
    }
}
