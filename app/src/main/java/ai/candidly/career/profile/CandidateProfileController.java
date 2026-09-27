package ai.candidly.career.profile;

import java.util.NoSuchElementException;
import java.util.UUID;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import ai.candidly.career.domain.Candidate;
import ai.candidly.career.domain.CandidatePhoto;
import ai.candidly.career.domain.CandidateResume;

/** Backs the "Resume & Profile" page's Personal Info / Preferences edits, resume file card,
 * and profile photo card. */
@RestController
@RequestMapping("/api/candidates/{candidateId}")
public class CandidateProfileController {

    private final CandidateProfileService profileService;
    private final CandidateResumeService resumeService;
    private final CandidatePhotoService photoService;

    public CandidateProfileController(CandidateProfileService profileService, CandidateResumeService resumeService,
            CandidatePhotoService photoService) {
        this.profileService = profileService;
        this.resumeService = resumeService;
        this.photoService = photoService;
    }

    @PutMapping("/profile")
    public Candidate updateProfile(@PathVariable UUID candidateId, @RequestBody CandidateProfileUpdateRequest request) {
        try {
            return profileService.update(candidateId, request);
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(404), "Candidate not found");
        }
    }

    @PostMapping("/resume")
    @ResponseStatus(HttpStatus.CREATED)
    public ResumeMeta uploadResume(@PathVariable UUID candidateId, @RequestParam("file") MultipartFile file) {
        try {
            return resumeService.upload(candidateId, file);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400), e.getMessage());
        }
    }

    @GetMapping("/resume/meta")
    public ResumeMeta resumeMeta(@PathVariable UUID candidateId) {
        return resumeService.getMeta(candidateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatusCode.valueOf(404), "No resume on file"));
    }

    @GetMapping("/resume")
    public ResponseEntity<byte[]> downloadResume(
            @PathVariable UUID candidateId,
            @RequestParam(name = "disposition", defaultValue = "attachment") String disposition) {
        CandidateResume resume = resumeService.get(candidateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatusCode.valueOf(404), "No resume on file"));
        ContentDisposition contentDisposition = "inline".equals(disposition)
                ? ContentDisposition.inline().filename(resume.getFilename()).build()
                : ContentDisposition.attachment().filename(resume.getFilename()).build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(resume.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .body(resume.getContent());
    }

    @DeleteMapping("/resume")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteResume(@PathVariable UUID candidateId) {
        resumeService.delete(candidateId);
    }

    @PostMapping("/photo")
    @ResponseStatus(HttpStatus.CREATED)
    public PhotoMeta uploadPhoto(@PathVariable UUID candidateId, @RequestParam("file") MultipartFile file) {
        try {
            return photoService.upload(candidateId, file);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400), e.getMessage());
        }
    }

    @GetMapping("/photo/meta")
    public PhotoMeta photoMeta(@PathVariable UUID candidateId) {
        return photoService.getMeta(candidateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatusCode.valueOf(404), "No photo on file"));
    }

    @GetMapping("/photo")
    public ResponseEntity<byte[]> downloadPhoto(@PathVariable UUID candidateId) {
        CandidatePhoto photo = photoService.get(candidateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatusCode.valueOf(404), "No photo on file"));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.getContentType()))
                .body(photo.getContent());
    }

    @DeleteMapping("/photo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePhoto(@PathVariable UUID candidateId) {
        photoService.delete(candidateId);
    }
}
