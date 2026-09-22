package ai.candidly.career.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatusCode;

import ai.candidly.career.domain.Candidate;
import ai.candidly.career.domain.CandidateRepository;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/candidates")
public class CandidateController {

    private final CandidateIngestionService ingestionService;
    private final CandidateRepository candidateRepository;
    private final CandidateDataSubjectService dataSubjectService;

    public CandidateController(CandidateIngestionService ingestionService, CandidateRepository candidateRepository,
            CandidateDataSubjectService dataSubjectService) {
        this.ingestionService = ingestionService;
        this.candidateRepository = candidateRepository;
        this.dataSubjectService = dataSubjectService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Candidate create(@Valid @RequestBody CandidateRequest request) {
        return ingestionService.ingest(request);
    }

    @GetMapping("/{id}")
    public Candidate get(@PathVariable UUID id) {
        return candidateRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatusCode.valueOf(404), "Candidate not found"));
    }

    /** No auth in this slice - the UI's candidate picker until there's a real session/login. */
    @GetMapping
    public List<Candidate> list() {
        return candidateRepository.findAll();
    }

    /** GDPR/UK GDPR data portability (docs/02 §4.1) - everything this candidate ID owns, embeddings included. */
    @GetMapping("/{id}/export")
    public CandidateDataSubjectService.CandidateDataExport export(@PathVariable UUID id) {
        try {
            return dataSubjectService.export(id);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(404), "Candidate not found");
        }
    }

    /** GDPR/UK GDPR right to erasure (docs/02 §4.1) - hard delete with cascade; see CandidateDataSubjectService's javadoc for what deliberately survives it (the audit ledger). */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void erase(@PathVariable UUID id) {
        try {
            dataSubjectService.erase(id);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(404), "Candidate not found");
        }
    }
}
