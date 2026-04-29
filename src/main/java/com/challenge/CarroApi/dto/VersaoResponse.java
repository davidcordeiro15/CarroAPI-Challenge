package com.challenge.CarroApi.dto;
public record VersaoResponse(
        Long versaoId,
        String nome,
        EspecificacaoResponse especificacao
) {}