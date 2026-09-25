package com.modelflux.controller;

import com.modelflux.model.dto.ProviderStatusResponse;
import com.modelflux.service.ProviderStatusService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/providers")
public class ProviderStatusController {

    private final ProviderStatusService providerStatusService;

    public ProviderStatusController(ProviderStatusService providerStatusService) {
        this.providerStatusService = providerStatusService;
    }

    @GetMapping("/status")
    public ResponseEntity<List<ProviderStatusResponse>> getStatus() {
        return ResponseEntity.ok(providerStatusService.getAllStatuses());
    }
}