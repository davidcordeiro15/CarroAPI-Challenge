package com.challenge.CarroApi.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "ESPECIFICACOES")
public class Especificacao {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "versao_id")
    private Versao versao;

    private String motor;
    private String potencia;
    private String torque;
    private String transmissao;

    private String carga;
    private String reboque;
    private String tanque;
}
