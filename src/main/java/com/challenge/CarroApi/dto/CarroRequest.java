package com.challenge.CarroApi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record CarroRequest(

        Long modeloId,

        @NotBlank String tipo,

        @NotNull List<VersaoRequest> versoes



) {}