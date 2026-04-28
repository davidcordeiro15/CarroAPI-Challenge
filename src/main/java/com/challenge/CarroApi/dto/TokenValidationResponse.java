package com.challenge.CarroApi.dto;

public record TokenValidationResponse(
        boolean valid,
        String email,
        String role
) {}