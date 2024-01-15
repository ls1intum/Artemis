package de.tum.in.www1.artemis.domain.math;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class MathExerciseExpressionInputConfiguration extends MathExerciseInputConfiguration {

    private boolean sketchEnabled;

    private boolean imageUploadEnabled;

    public boolean imageUploadEnabled() {
        return imageUploadEnabled;
    }

    public void setImageUploadEnabled(boolean imageUploadEnabled) {
        this.imageUploadEnabled = imageUploadEnabled;
    }

    public boolean sketchEnabled() {
        return sketchEnabled;
    }

    public void setSketchEnabled(boolean sketchEnabled) {
        this.sketchEnabled = sketchEnabled;
    }

    public de.tum.in.www1.artemis.domain.math.MathInputType inputType() {
        return MathInputType.EXPRESSION;
    }

    public boolean ocrEnabled() {
        return sketchEnabled || imageUploadEnabled;
    }

}
