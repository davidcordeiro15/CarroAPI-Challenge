package com.challenge.CarroApi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CarroRequest(

        @NotNull Long modeloId,

        @NotBlank
        @Size(max = 50)
        String tipo

) {}