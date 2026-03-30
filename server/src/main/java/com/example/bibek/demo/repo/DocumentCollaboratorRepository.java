package com.example.bibek.demo.repo;

import com.example.bibek.demo.model.DocumentCollaborator;
import com.example.bibek.demo.model.DocumentCollaboratorId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentCollaboratorRepository extends JpaRepository<DocumentCollaborator, DocumentCollaboratorId> {

    List<DocumentCollaborator> findByDocumentId(String documentId);

    List<DocumentCollaborator> findByUserId(String userId);

    boolean existsByDocumentIdAndUserId(String documentId, String userId);
}
