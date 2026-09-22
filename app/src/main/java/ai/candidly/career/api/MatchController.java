package ai.candidly.career.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatusCode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import ai.candidly.career.domain.CandidateRepository;
import ai.candidly.career.domain.JobPostingRepository;
import ai.candidly.career.domain.MatchScorecard;
import ai.candidly.career.domain.MatchScorecardRepository;
import ai.candidly.career.matching.MatchOrchestratorService;

@RestController
@RequestMapping("/api/matches")
public class MatchController {

    private final MatchOrchestratorService matchOrchestratorService;
    private final CandidateRepository candidateRepository;
    private final JobPostingRepository jobPostingRepository;
    private final MatchScorecardRepository matchScorecardRepository;

    public MatchController(MatchOrchestratorService matchOrchestratorService, CandidateRepository candidateRepository,
            JobPostingRepository jobPostingRepository, MatchScorecardRepository matchScorecardRepository) {
        this.matchOrchestratorService = matchOrchestratorService;
        this.candidateRepository = candidateRepository;
        this.jobPostingRepository = jobPostingRepository;
        this.matchScorecardRepository = matchScorecardRepository;
    }

    @PostMapping
    public MatchScorecard evaluate(@RequestParam UUID candidateId, @RequestParam UUID jobPostingId) {
        var candidate = candidateRepository.findById(candidateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatusCode.valueOf(404), "Candidate not found"));
        var job = jobPostingRepository.findById(jobPostingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatusCode.valueOf(404), "Job posting not found"));
        return matchOrchestratorService.evaluate(candidate, job);
    }

    /** The Matches/Match Explorer UI's listing for one candidate - every scorecard on file, scored or not yet shortlisted. */
    @GetMapping
    public List<MatchScorecard> list(@RequestParam UUID candidateId) {
        return matchScorecardRepository.findByCandidateId(candidateId);
    }

    @GetMapping("/{id}")
    public MatchScorecard get(@PathVariable UUID id) {
        return matchScorecardRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatusCode.valueOf(404), "Match scorecard not found"));
    }
}
