package com.instantdrs.backend.controller;

import com.instantdrs.backend.dto.ReviewDecisionRequest;
import com.instantdrs.backend.dto.ReviewDecisionResponse;
import com.instantdrs.backend.service.ReviewDecisionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/drs/sessions/{id}")
public class ReviewDecisionController {

    private final ReviewDecisionService reviewDecisionService;

    public ReviewDecisionController(ReviewDecisionService reviewDecisionService) {
        this.reviewDecisionService = reviewDecisionService;
    }

    @PostMapping({"/decision", "/review-decision"})
    public ResponseEntity<ReviewDecisionResponse> saveDecision(
            @PathVariable("id") Long id,
            @Valid @RequestBody ReviewDecisionRequest request) {
        ReviewDecisionResponse response = reviewDecisionService.saveDecision(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping({"/decision", "/review-decision"})
    public ResponseEntity<ReviewDecisionResponse> getLatestDecision(@PathVariable("id") Long id) {
        ReviewDecisionResponse response = reviewDecisionService.getLatestDecision(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/decisions")
    public ResponseEntity<List<ReviewDecisionResponse>> getAllDecisions(@PathVariable("id") Long id) {
        List<ReviewDecisionResponse> decisions = reviewDecisionService.getAllDecisionsForSession(id);
        return ResponseEntity.ok(decisions);
    }
}
