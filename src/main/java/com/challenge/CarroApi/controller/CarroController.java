package com.challenge.CarroApi.controller;

import com.challenge.CarroApi.dto.CarroRequest;
import com.challenge.CarroApi.dto.CarroResponse;
import com.challenge.CarroApi.service.CarroService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/carros")
public class CarroController {

    private final CarroService service;

    public CarroController(CarroService service) {
        this.service = service;
    }


    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<CarroResponse> create(
            @Valid @RequestBody CarroRequest request) {

        return ResponseEntity.status(201).body(service.create(request));
    }


    @GetMapping
    public ResponseEntity<List<CarroResponse>> list() {
        return ResponseEntity.ok(service.list());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CarroResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));
    }


    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<CarroResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody CarroRequest request) {

        return ResponseEntity.ok(service.update(id, request));
    }


    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}