package com.challenge.CarroApi.repository;

import com.challenge.CarroApi.entity.Especificacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EspecificacaoRepository extends JpaRepository<Especificacao, Long> {

    Optional<Especificacao> findByVersaoId(Long versaoId);

    void deleteByVersaoId(Long versaoId);
}