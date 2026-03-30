package com.example.bibek.demo.repo;

import com.example.bibek.demo.model.DocumentOperation;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentOperationRepository extends JpaRepository<DocumentOperation, Long> {
    List<DocumentOperation> findByDocumentIdOrderByLogicalTimestampAsc(String documentId);

    @Query("SELECT op FROM DocumentOperation op WHERE op.documentId = :docId " +
            "AND op.operationId > :afterOpId ORDER BY op.operationId ASC")
    List<DocumentOperation> findOperationsAfter(@Param("docId") String documentId,
                                                @Param("afterOpId") Long afterOperationId);

    long countByDocumentId(String documentId);

    boolean existsByDocumentIdAndCharacterId(String documentId, String characterId);
}