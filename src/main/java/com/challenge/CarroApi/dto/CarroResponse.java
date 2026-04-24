package com.challenge.CarroApi.dto;

public record CarroResponse(
        Long id,
        String modelo,
        String marca,
        String tipo
) {}