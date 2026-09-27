package ai.candidly.career.positioning;

import java.util.UUID;

import org.springframework.http.HttpStatusCode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/candidates/{candidateId}/profile-positioning")
public class ProfilePositioningController {

    private final ProfilePositioningService profilePositioningService;

    public ProfilePositioningController(ProfilePositioningService profilePositioningService) {
        this.profilePositioningService = profilePositioningService;
    }

    @GetMapping
    public ProfilePositioningView get(@PathVariable UUID candidateId) {
        try {
            return profilePositioningService.derive(candidateId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(404), "Candidate not found");
        }
    }
}
