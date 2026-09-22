package ai.candidly.career.profileimport;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/**
 * Pre-fills the onboarding wizard from an uploaded resume or pasted LinkedIn text -
 * nothing here is persisted (see {@link ProfileExtractionService}'s javadoc); the
 * candidate reviews/edits the result before the existing {@code POST /api/candidates}
 * (unchanged) creates anything.
 */
@RestController
@RequestMapping("/api/candidates/profile-import")
public class ProfileImportController {

    private final ResumeTextExtractor resumeTextExtractor;
    private final ProfileExtractionService profileExtractionService;

    public ProfileImportController(ResumeTextExtractor resumeTextExtractor,
            ProfileExtractionService profileExtractionService) {
        this.resumeTextExtractor = resumeTextExtractor;
        this.profileExtractionService = profileExtractionService;
    }

    @PostMapping("/resume")
    public ProfileImportResult importResume(@RequestParam("file") MultipartFile file) {
        String text = resumeTextExtractor.extract(file);
        return profileExtractionService.extractFromResume(text);
    }

    @PostMapping("/linkedin-text")
    public ProfileImportResult importLinkedInText(@Valid @RequestBody LinkedInTextRequest request) {
        return profileExtractionService.extractFromLinkedInText(request.text());
    }

    public record LinkedInTextRequest(@NotBlank String text) {
    }
}
