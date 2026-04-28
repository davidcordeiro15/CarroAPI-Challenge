package com.challenge.CarroApi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CarroRequest(

        @NotNull Long modeloId,

        @NotBlank String tipo,

        @NotNull Integer ano,

        @NotBlank String cor,

        @NotNull BigDecimal preco,

        @NotBlank String placa

) {}