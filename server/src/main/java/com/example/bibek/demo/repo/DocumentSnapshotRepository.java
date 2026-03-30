package com.example.bibek.demo.repo;

import com.example.bibek.demo.model.DocumentSnapshot;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentSnapshotRepository extends JpaRepository<DocumentSnapshot, String> {

    @Query("SELECT s FROM DocumentSnapshot s WHERE s.documentId = :documentId " +
            "ORDER BY s.createdAt DESC")
    List<DocumentSnapshot> findLatestSnapshots(@Param("documentId") String documentId, Pageable pageable);
}