package com.challenge.CarroApi.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "VERSOES")
@Getter
@Setter
public class Versao {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "carro_id")
    private Carro carro;

    private String nome;

    @OneToOne(mappedBy = "versao", cascade = CascadeType.ALL, orphanRemoval = true)
    private Especificacao especificacao;
}