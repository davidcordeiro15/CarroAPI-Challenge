package com.challenge.CarroApi.service;

import com.challenge.CarroApi.dto.CarroRequest;
import com.challenge.CarroApi.dto.CarroResponse;
import com.challenge.CarroApi.entity.Carro;
import com.challenge.CarroApi.entity.Modelo;
import com.challenge.CarroApi.repository.CarroRepository;
import com.challenge.CarroApi.repository.ModeloRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CarroService {

    private final CarroRepository repository;
    private final ModeloRepository modeloRepository;

    public CarroService(CarroRepository repository, ModeloRepository modeloRepository) {
        this.repository = repository;
        this.modeloRepository = modeloRepository;
    }

    public CarroResponse create(CarroRequest request) {

        Modelo modelo = modeloRepository.findById(request.modeloId())
                .orElseThrow(() -> new RuntimeException("Modelo não encontrado"));

        Carro carro = new Carro();
        carro.setModelo(modelo);
        carro.setTipo(request.tipo());

        repository.save(carro);

        return toResponse(carro);
    }

    public List<CarroResponse> list() {
        return repository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public CarroResponse findById(Long id) {
        Carro carro = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Carro não encontrado"));

        return toResponse(carro);
    }

    public CarroResponse update(Long id, CarroRequest request) {

        Carro carro = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Carro não encontrado"));

        Modelo modelo = modeloRepository.findById(request.modeloId())
                .orElseThrow(() -> new RuntimeException("Modelo não encontrado"));

        carro.setModelo(modelo);
        carro.setTipo(request.tipo());

        repository.save(carro);

        return toResponse(carro);
    }

    public void delete(Long id) {
        Carro carro = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Carro não encontrado"));

        repository.delete(carro);
    }


    private CarroResponse toResponse(Carro carro) {
        return new CarroResponse(
                carro.getId(),
                carro.getModelo().getNome(),
                carro.getModelo().getMarca().getNome(),
                carro.getTipo()
        );
    }
}