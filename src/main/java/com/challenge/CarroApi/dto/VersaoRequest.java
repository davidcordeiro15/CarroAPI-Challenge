package com.challenge.CarroApi.dto;
public record VersaoRequest(
        String nome,
        EspecificacaoRequest especificacao
) {}