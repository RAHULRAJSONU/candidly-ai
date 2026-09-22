package ai.candidly.career.demographics;

import java.util.UUID;

import org.springframework.http.HttpStatusCode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import ai.candidly.career.domain.CandidateRepository;

/**
 * The candidate's own voluntary self-identification (docs/02 §4, docs/04) - this
 * controller and {@link CandidateDemographicsRepository} are the only things in the
 * codebase allowed to read or write {@code candidate_demographics}. Nothing in {@code
 * matching}/{@code tailoring} imports this package; see {@link CandidateDemographics}'s
 * javadoc for why that's structural, not just convention.
 */
@RestController
@RequestMapping("/api/candidates/{candidateId}/demographics")
public class DemographicsController {

    private final CandidateRepository candidateRepository;
    private final CandidateDemographicsRepository demographicsRepository;

    public DemographicsController(CandidateRepository candidateRepository,
            CandidateDemographicsRepository demographicsRepository) {
        this.candidateRepository = candidateRepository;
        this.demographicsRepository = demographicsRepository;
    }

    @PutMapping
    public CandidateDemographics submit(@PathVariable UUID candidateId, @RequestBody DemographicsRequest request) {
        if (!candidateRepository.existsById(candidateId)) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(404), "Candidate not found");
        }
        demographicsRepository.deleteByCandidateId(candidateId);
        demographicsRepository.flush();
        return demographicsRepository.save(new CandidateDemographics(candidateId, request.gender(),
                request.raceEthnicity(), request.veteranStatus(), request.disabilityStatus()));
    }

    @GetMapping
    public CandidateDemographics get(@PathVariable UUID candidateId) {
        return demographicsRepository.findByCandidateId(candidateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatusCode.valueOf(404),
                        "No self-identification on file for this candidate"));
    }
}
