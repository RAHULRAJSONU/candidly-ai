package ai.candidly.career.vault;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;

public record CertificationRequest(@NotBlank String name, @NotBlank String issuer, LocalDate issuedOn, String credentialId) {
}
