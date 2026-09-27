package ai.candidly.career.settings;

import java.util.UUID;

import org.springframework.http.HttpStatusCode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Backs the "Settings" page (mock: AI Preferences / Notifications / Appearance / Billing cards). */
@RestController
@RequestMapping("/api/candidates/{candidateId}/settings")
public class CandidateSettingsController {

    private final CandidateSettingsService service;

    public CandidateSettingsController(CandidateSettingsService service) {
        this.service = service;
    }

    @GetMapping
    public CandidateSettings get(@PathVariable UUID candidateId) {
        return service.get(candidateId);
    }

    @PutMapping
    public CandidateSettings update(@PathVariable UUID candidateId, @RequestBody CandidateSettingsRequest request) {
        try {
            return service.update(candidateId, request);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400), e.getMessage());
        }
    }
}
