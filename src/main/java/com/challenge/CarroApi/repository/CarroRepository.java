package com.challenge.CarroApi.repository;

import com.challenge.CarroApi.entity.Carro;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CarroRepository extends JpaRepository<Carro, Long> {
}