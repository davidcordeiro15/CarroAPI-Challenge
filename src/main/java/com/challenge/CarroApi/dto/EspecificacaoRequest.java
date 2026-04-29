package com.challenge.CarroApi.dto;
public record EspecificacaoRequest(
        String motor,
        String potencia,
        String torque,
        String transmissao,
        String carga,
        String reboque,
        String tanque
) {}