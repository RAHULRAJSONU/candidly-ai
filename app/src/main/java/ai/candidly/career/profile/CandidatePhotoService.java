package ai.candidly.career.profile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import ai.candidly.career.domain.CandidatePhoto;
import ai.candidly.career.domain.CandidatePhotoRepository;
import ai.candidly.career.domain.CandidateRepository;

/**
 * Stores/serves the candidate's uploaded profile photo for the "Resume &amp; Profile" page
 * (mockup-driven addition, no direct docs/00-04 requirement) - mirrors
 * {@link CandidateResumeService}'s storage pattern. The frontend crops the image to a
 * square client-side before uploading, so this service just validates type/size and
 * stores the bytes as-is.
 */
@Service
public class CandidatePhotoService {

    private static final long MAX_SIZE_BYTES = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final CandidateRepository candidateRepository;
    private final CandidatePhotoRepository photoRepository;

    public CandidatePhotoService(CandidateRepository candidateRepository, CandidatePhotoRepository photoRepository) {
        this.candidateRepository = candidateRepository;
        this.photoRepository = photoRepository;
    }

    @Transactional
    public PhotoMeta upload(UUID candidateId, MultipartFile file) {
        if (!candidateRepository.existsById(candidateId)) {
            throw new IllegalArgumentException("Candidate not found: " + candidateId);
        }
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new IllegalArgumentException("Image exceeds 5MB limit");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Only JPEG, PNG, and WebP images are accepted");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Instant now = Instant.now();

        CandidatePhoto photo = photoRepository.findByCandidateId(candidateId).orElse(null);
        if (photo == null) {
            photo = new CandidatePhoto(candidateId, contentType, bytes, now);
            photoRepository.save(photo);
        } else {
            photo.replace(contentType, bytes, now);
        }
        return toMeta(photo);
    }

    @Transactional(readOnly = true)
    public Optional<PhotoMeta> getMeta(UUID candidateId) {
        return photoRepository.findByCandidateId(candidateId).map(CandidatePhotoService::toMeta);
    }

    @Transactional(readOnly = true)
    public Optional<CandidatePhoto> get(UUID candidateId) {
        return photoRepository.findByCandidateId(candidateId);
    }

    @Transactional
    public void delete(UUID candidateId) {
        photoRepository.deleteByCandidateId(candidateId);
    }

    private static PhotoMeta toMeta(CandidatePhoto photo) {
        return new PhotoMeta(photo.getContentType(), photo.getSizeBytes(), photo.getUploadedAt());
    }
}
