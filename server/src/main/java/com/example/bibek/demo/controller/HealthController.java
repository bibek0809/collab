package com.example.bibek.demo.controller;

import com.example.bibek.demo.dto.response.DocumentResponses;
import com.example.bibek.demo.service.CrdtService;
import com.example.bibek.demo.service.PresenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class HealthController {

    private final CrdtService crdtService;
    private final PresenceService presenceService;

    @GetMapping("/health")
    public DocumentResponses.HealthResponse health() {
        Map<String, String> checks = new LinkedHashMap<>();
        checks.put("database", "ok");
        checks.put("active_documents", String.valueOf(crdtService.getActiveDocumentCount()));
        checks.put("active_users", String.valueOf(presenceService.getTotalActiveUsers()));
        checks.put("documents_with_presence", String.valueOf(presenceService.getActiveDocumentCount()));

        return DocumentResponses.HealthResponse.builder()
                .status("healthy")
                .checks(checks)
                .build();
    }
}

