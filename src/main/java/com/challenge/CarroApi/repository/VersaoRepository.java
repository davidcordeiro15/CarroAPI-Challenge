package com.challenge.CarroApi.repository;


import com.challenge.CarroApi.entity.Versao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VersaoRepository extends JpaRepository<Versao, Long> {


    void deleteByCarroId(Long carroId);


    List<Versao> findByCarroId(Long carroId);
}