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

import de.tum.cit.aet.artemis.iris.dto.IrisCombinedViewContextDTO;
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
    void serializesCombinedViewContextWithNestedSlidesAndVideo() throws Exception {
        var dto = new IrisCombinedViewContextDTO(new IrisSlidesContextDTO(123L, 5), new IrisVideoContextDTO(123L, 45.2));
        String json = mapper.writeValueAsString(dto);

        assertThat(json).contains("\"type\":\"combinedView\"");
        assertThat(json).contains("\"slides\"");
        assertThat(json).contains("\"page\":5");
        assertThat(json).contains("\"video\"");
        assertThat(json).contains("\"timestamp\":45.2");
    }

    @Test
    void deserializesCombinedViewContextWithNestedSlidesAndVideo() throws Exception {
        var original = new IrisCombinedViewContextDTO(new IrisSlidesContextDTO(123L, 5), new IrisVideoContextDTO(123L, 45.2));
        String json = mapper.writeValueAsString(original);
        IrisMessageContextDTO dto = mapper.readValue(json, IrisCombinedViewContextDTO.class);

        assertThat(dto).isInstanceOf(IrisCombinedViewContextDTO.class);
        var combinedViewContext = (IrisCombinedViewContextDTO) dto;
        assertThat(combinedViewContext.type()).isEqualTo("combinedView");
        assertThat(combinedViewContext.slides()).isNotNull();
        assertThat(combinedViewContext.slides().page()).isEqualTo(5);
        assertThat(combinedViewContext.video()).isNotNull();
        assertThat(combinedViewContext.video().timestamp()).isEqualTo(45.2);
        // The lecture unit ID is derived from the nested contexts.
        assertThat(combinedViewContext.lectureUnitId()).isEqualTo(123L);
    }

    @Test
    void combinedViewContextDerivesLectureUnitIdFromVideoWhenNoSlides() {
        var dto = new IrisCombinedViewContextDTO(null, new IrisVideoContextDTO(456L, 10.0));

        assertThat(dto.slides()).isNull();
        assertThat(dto.lectureUnitId()).isEqualTo(456L);
    }

    @Test
    void combinedViewContextValidationCascadesToNestedSlides() {
        // A nested slides context with an invalid page must fail validation via the @Valid cascade.
        var dto = new IrisCombinedViewContextDTO(new IrisSlidesContextDTO("slides", 123L, 0), null);
        var violations = validator.validate(dto);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("slides.page"));
    }

    @Test
    void combinedViewContextValidationCascadesToNestedVideoLectureUnitId() {
        // A nested video context with a non-positive lectureUnitId must fail validation via the @Valid cascade.
        var dto = new IrisCombinedViewContextDTO(null, new IrisVideoContextDTO("video", 0L, 10.0));
        var violations = validator.validate(dto);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("video.lectureUnitId"));
    }

    @Test
    void polymorphicTypeHandlingWithCombinedViewAndOtherContexts() throws Exception {
        // Test that the combined view context works together with standalone video/slides contexts
        var combinedViewContext = new IrisCombinedViewContextDTO(new IrisSlidesContextDTO(123L, 5), new IrisVideoContextDTO(123L, 45.2));
        var videoContext = new IrisVideoContextDTO(123L, 45.2);
        var slidesContext = new IrisSlidesContextDTO(123L, 5);

        List<IrisMessageContextDTO> contexts = List.of(combinedViewContext, videoContext, slidesContext);

        assertThat(contexts).hasSize(3);
        assertThat(contexts.get(0)).isInstanceOf(IrisCombinedViewContextDTO.class);
        assertThat(contexts.get(1)).isInstanceOf(IrisVideoContextDTO.class);
        assertThat(contexts.get(2)).isInstanceOf(IrisSlidesContextDTO.class);

        var combinedView = (IrisCombinedViewContextDTO) contexts.get(0);
        assertThat(combinedView.lectureUnitId()).isEqualTo(123L);

        var video = (IrisVideoContextDTO) contexts.get(1);
        assertThat(video.lectureUnitId()).isEqualTo(123L);
        assertThat(video.timestamp()).isEqualTo(45.2);

        var slides = (IrisSlidesContextDTO) contexts.get(2);
        assertThat(slides.lectureUnitId()).isEqualTo(123L);
        assertThat(slides.page()).isEqualTo(5);
    }
}
