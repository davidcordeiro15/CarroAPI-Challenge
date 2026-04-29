package com.challenge.CarroApi.dto;

import java.util.List;

public record CarroResponse(
        Long carroId,
        String marca,
        String modelo,
        String tipo,
        List<VersaoResponse> versoes

) {}