package ai.candidly.career.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ai.candidly.career.config.ProviderProperties;

/**
 * Read-only view of which AI provider/model this deployment is actually configured to
 * use (the Settings screen's "AI Preferences" tab) - never the API keys themselves.
 * There is no per-candidate model choice in this build (docs/00 doesn't call for one and
 * swapping providers mid-flight would need separate prompt tuning per provider), so this
 * is informational rather than an editable setting.
 */
@RestController
@RequestMapping("/api/config/ai")
public class AiConfigController {

    private final ProviderProperties groqProperties;
    private final ProviderProperties jinaProperties;
    private final ProviderProperties typeSafeProperties;

    public AiConfigController(ProviderProperties groqProperties, ProviderProperties jinaProperties,
            ProviderProperties typeSafeProperties) {
        this.groqProperties = groqProperties;
        this.jinaProperties = jinaProperties;
        this.typeSafeProperties = typeSafeProperties;
    }

    @GetMapping
    public AiConfigView get() {
        return new AiConfigView(
                new ProviderView("Groq", groqProperties.getModel(), !groqProperties.getApiKey().isBlank()),
                new ProviderView("Jina", jinaProperties.getModel(), !jinaProperties.getApiKey().isBlank()),
                new ProviderView("TypeSafe", typeSafeProperties.getModel(), !typeSafeProperties.getApiKey().isBlank()));
    }

    public record ProviderView(String provider, String model, boolean configured) {
    }

    public record AiConfigView(ProviderView tailoringAndDrafting, ProviderView embeddings, ProviderView judgments) {
    }
}
