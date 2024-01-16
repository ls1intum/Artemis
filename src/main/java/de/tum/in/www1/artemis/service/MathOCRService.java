package de.tum.in.www1.artemis.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import de.tum.in.www1.artemis.service.dto.MathOCRRequestDTO;
import de.tum.in.www1.artemis.service.dto.MathOCRResponseDTO;
import de.tum.in.www1.artemis.service.dto.MathOCRTokenResponseDTO;

@Service
public class MathOCRService {

    private static final Logger log = LoggerFactory.getLogger(MathOCRService.class);

    private static final String ocrApiBaseUrl = "https://api.mathpix.com/v3";

    @Value("${artemis.math.ocr.enabled:#{false}}")
    private boolean ocrEnabled;

    @Value("${artemis.math.ocr.app-id:#{null}}")
    private String mathpixAppId;

    @Value("${artemis.math.ocr.app-key:#{null}}")
    private String mathpixAppKey;

    @Value("${artemis.math.ocr.client-token-expiration:#{300}}")
    private int mathpixClientTokenExpiration;

    private final HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_2).build();

    public MathOCRTokenResponseDTO getClientToken() throws JsonProcessingException, ExecutionException, InterruptedException {
        this.verifyOCRConfiguration();

        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode data = objectMapper.createObjectNode();
        data.put("expires", this.mathpixClientTokenExpiration);
        String body = objectMapper.writeValueAsString(data);

        // create request
        HttpRequest request = createMathpixRequest("app-tokens", body);

        // send request & deserialize response
        MathpixAppTokenResponse response = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(HttpResponse::body).thenApply(responseBody -> {
            try {
                return objectMapper.readValue(responseBody, MathpixAppTokenResponse.class);
            }
            catch (IOException e) {
                log.error("Error while deserializing app token response: {}", e.getMessage());
                return null;
            }
        }).get();

        return new MathOCRTokenResponseDTO(response.appToken(), response.appTokenExpiresAt());
    }

    public MathOCRResponseDTO getExpressionFromImageOrStroke(MathOCRRequestDTO ocrRequest) throws JsonProcessingException, ExecutionException, InterruptedException {
        this.verifyOCRConfiguration();

        String ocrAction = getOCRAction(ocrRequest);
        MathpixOCRRequest ocrBody = MathpixOCRRequest.fromOcrRequest(ocrRequest);

        // serialize request body
        ObjectMapper objectMapper = new ObjectMapper();
        String body = objectMapper.writeValueAsString(ocrBody);

        // create request
        HttpRequest request = createMathpixRequest(ocrAction, body);

        // send request & deserialize response
        MathpixOCRResponse response = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(HttpResponse::body).thenApply(responseBody -> {
            try {
                return objectMapper.readValue(responseBody, MathpixOCRResponse.class);
            }
            catch (IOException e) {
                log.error("Error while deserializing OCR response: {}", e.getMessage());
                return null;
            }
        }).get();

        return new MathOCRResponseDTO(response.text(), response.latexStyled(), response.confidence(), response.error());
    }

    private void verifyOCRConfiguration() {
        if (!ocrEnabled) {
            throw new IllegalStateException("OCR is not enabled");
        }
        if (mathpixAppId == null || mathpixAppKey == null) {
            throw new IllegalStateException("Mathpix app id or key not configured");
        }
    }

    private HttpRequest createMathpixRequest(String action, String body) {
        URI endpoint = URI.create(ocrApiBaseUrl + "/" + action);

        return HttpRequest.newBuilder().uri(endpoint).POST(HttpRequest.BodyPublishers.ofString(body)).header("Content-Type", "application/json").header("app_id", this.mathpixAppId)
                .header("app_key", this.mathpixAppKey).build();
    }

    private String getOCRAction(MathOCRRequestDTO ocrRequest) {
        if (ocrRequest.image() != null) {
            return "text";
        }
        else if (ocrRequest.strokes() != null) {
            return "strokes";
        }
        else {
            throw new IllegalArgumentException("No image or stroke provided for OCR request");
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record MathpixOCRRequest(String src, StrokesData strokes, String[] format) {

        private static MathpixOCRRequest fromOcrRequest(MathOCRRequestDTO ocrRequest) {
            String src = null;
            StrokesData strokes = null;
            if (ocrRequest.image() != null) {
                src = ocrRequest.image();
            }
            else if (ocrRequest.strokes() != null) {
                strokes = new StrokesData(new Strokes(ocrRequest.strokes().x(), ocrRequest.strokes().y()));
            }
            String[] format = new String[] { "text", "latex_styled" };

            return new MathpixOCRRequest(src, strokes, format);
        }

        private record StrokesData(Strokes strokes) {
        }

        private record Strokes(Double[][] x, Double[][] y) {
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MathpixOCRResponse(String text, String latexStyled, String[] detectedAlphabets, Boolean isPrinted, Boolean isHandwritten, Double confidence,
            Double confidenceRate, String error) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MathpixAppTokenResponse(String appToken, int appTokenExpiresAt) {
    }
}
