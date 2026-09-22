package ai.candidly.career.pipeline;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OfferRepository extends JpaRepository<Offer, UUID> {

    List<Offer> findByCandidateId(UUID candidateId);
}
