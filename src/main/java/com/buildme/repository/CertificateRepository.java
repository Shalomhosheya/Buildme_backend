package com.buildme.repository;

import com.buildme.model.Certificate;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface CertificateRepository extends MongoRepository<Certificate, String> {
    Optional<Certificate> findByUserId(String userId);
    Optional<Certificate> findByCertId(String certId);
    boolean existsByUserId(String userId);
}
