package ai.candidly.career.profile;

import java.util.NoSuchElementException;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ai.candidly.career.domain.Candidate;
import ai.candidly.career.domain.CandidateRepository;
import ai.candidly.career.domain.CurrencyCodes;

/**
 * Applies partial edits from the "Resume &amp; Profile" page to a {@link Candidate}. Plain
 * CRUD - no TypeSafe judgment involved (per the typesafe-ai skill, editing a candidate's
 * own stated profile fields is not an AI decision). Candidate is a managed JPA entity
 * inside this {@code @Transactional} method, so the setter calls below are enough to
 * persist - no explicit {@code save()} needed.
 *
 * <p>A missing candidate throws {@link NoSuchElementException} (404) so it stays distinct
 * from a rejected field value, which throws {@link IllegalArgumentException} (400).
 */
@Service
public class CandidateProfileService {

    private final CandidateRepository candidateRepository;

    public CandidateProfileService(CandidateRepository candidateRepository) {
        this.candidateRepository = candidateRepository;
    }

    @Transactional
    public Candidate update(UUID candidateId, CandidateProfileUpdateRequest request) {
        Candidate candidate = candidateRepository.findById(candidateId)
                .orElseThrow(() -> new NoSuchElementException("Candidate not found: " + candidateId));

        if (request.fullName() != null) candidate.setFullName(request.fullName());
        if (request.location() != null) candidate.setLocation(request.location());
        if (request.workAuthorizations() != null) candidate.setWorkAuthorizations(request.workAuthorizations());
        if (request.phone() != null) candidate.setPhone(request.phone());
        if (request.linkedinUrl() != null) candidate.setLinkedinUrl(request.linkedinUrl());
        if (request.portfolioUrl() != null) candidate.setPortfolioUrl(request.portfolioUrl());
        if (request.headline() != null) candidate.setHeadline(request.headline());
        if (request.professionalSummary() != null) candidate.setProfessionalSummary(request.professionalSummary());
        if (request.workMode() != null) candidate.setWorkMode(request.workMode());
        if (request.noticePeriod() != null) candidate.setNoticePeriod(request.noticePeriod());
        if (request.openToRelocation() != null) candidate.setOpenToRelocation(request.openToRelocation());
        if (request.expectedCompMinMinorUnits() != null) candidate.setExpectedCompMinMinorUnits(request.expectedCompMinMinorUnits());
        if (request.expectedCompMaxMinorUnits() != null) candidate.setExpectedCompMaxMinorUnits(request.expectedCompMaxMinorUnits());
        if (request.compFloorMinorUnits() != null) {
            if (request.compFloorMinorUnits() < 0) {
                throw new IllegalArgumentException("compFloorMinorUnits must be >= 0, got: " + request.compFloorMinorUnits());
            }
            candidate.setCompFloorMinorUnits(request.compFloorMinorUnits());
        }
        if (request.preferredCurrency() != null) {
            candidate.setPreferredCurrency(CurrencyCodes.normalize(request.preferredCurrency(), "preferredCurrency"));
        }
        if (request.preferredLocations() != null) candidate.setPreferredLocations(request.preferredLocations());
        if (request.preferredRoles() != null) candidate.setPreferredRoles(request.preferredRoles());
        if (request.preferredIndustries() != null) candidate.setPreferredIndustries(request.preferredIndustries());
        if (request.employmentTypes() != null) candidate.setEmploymentTypes(request.employmentTypes());
        if (request.rawSkillMentions() != null) candidate.setRawSkillMentions(request.rawSkillMentions());

        return candidate;
    }
}
