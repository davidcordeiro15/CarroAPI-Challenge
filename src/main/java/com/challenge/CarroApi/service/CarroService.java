package com.challenge.CarroApi.service;

import com.challenge.CarroApi.dto.*;
import com.challenge.CarroApi.entity.Carro;
import com.challenge.CarroApi.entity.Especificacao;
import com.challenge.CarroApi.entity.Modelo;
import com.challenge.CarroApi.entity.Versao;
import com.challenge.CarroApi.repository.CarroRepository;
import com.challenge.CarroApi.repository.EspecificacaoRepository;
import com.challenge.CarroApi.repository.ModeloRepository;
import com.challenge.CarroApi.repository.VersaoRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CarroService {

    private final CarroRepository repository;
    private final ModeloRepository modeloRepository;
    private final VersaoRepository versaoRepository;
    private final EspecificacaoRepository especificacaoRepository;

    public CarroService(CarroRepository repository, ModeloRepository modeloRepository, VersaoRepository versaoRepository, EspecificacaoRepository especificacaoRepository) {
        this.repository = repository;
        this.modeloRepository = modeloRepository;
        this.versaoRepository = versaoRepository;
        this.especificacaoRepository = especificacaoRepository;
    }
    public CarroResponse create(CarroRequest request) {

        Modelo modelo = modeloRepository.findById(request.modeloId())
                .orElseThrow(() -> new RuntimeException("Modelo não encontrado"));

        Carro carro = new Carro();
        carro.setModelo(modelo);
        carro.setTipo(request.tipo());

        repository.save(carro);

        for (VersaoRequest vReq : request.versoes()) {

            Versao versao = new Versao();
            versao.setNome(vReq.nome());
            versao.setCarro(carro);

            versaoRepository.save(versao);

            EspecificacaoRequest eReq = vReq.especificacao();

            Especificacao esp = new Especificacao();
            esp.setVersao(versao);
            esp.setMotor(eReq.motor());
            esp.setPotencia(eReq.potencia());
            esp.setTorque(eReq.torque());
            esp.setTransmissao(eReq.transmissao());
            esp.setCarga(eReq.carga());
            esp.setReboque(eReq.reboque());
            esp.setTanque(eReq.tanque());

            especificacaoRepository.save(esp);
        }

        return findById(carro.getId());
    }

    public List<CarroResponse> list() {
        return repository.findAll()
                .stream()
                .map(this::toFullResponse)
                .toList();
    }

    public CarroResponse findById(Long id) {
        Carro carro = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Carro não encontrado"));

        return toFullResponse(carro);
    }
    @Transactional
    public CarroResponse update(Long id, CarroRequest request) {

        Carro carro = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Carro não encontrado"));


        carro.setTipo(request.tipo());

        repository.save(carro);


        versaoRepository.deleteByCarroId(id);

        for (VersaoRequest vReq : request.versoes()) {

            Versao versao = new Versao();
            versao.setNome(vReq.nome());
            versao.setCarro(carro);
            versaoRepository.save(versao);

            EspecificacaoRequest eReq = vReq.especificacao();

            Especificacao esp = new Especificacao();
            esp.setVersao(versao);
            esp.setMotor(eReq.motor());
            esp.setPotencia(eReq.potencia());
            esp.setTorque(eReq.torque());
            esp.setTransmissao(eReq.transmissao());
            esp.setCarga(eReq.carga());
            esp.setReboque(eReq.reboque());
            esp.setTanque(eReq.tanque());

            versao.setEspecificacao(esp);

            especificacaoRepository.save(esp);
        }

        return findById(id);
    }
    @Transactional
    public void delete(Long id) {

        Carro carro = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Carro não encontrado"));

        versaoRepository.deleteByCarroId(id);

        repository.delete(carro);
    }


    private CarroResponse toFullResponse(Carro carro) {

        List<VersaoResponse> versoes = carro.getVersoes()
                .stream()
                .map(v -> new VersaoResponse(
                        v.getId(),
                        v.getNome(),
                        new EspecificacaoResponse(
                                v.getEspecificacao().getMotor(),
                                v.getEspecificacao().getPotencia(),
                                v.getEspecificacao().getTorque(),
                                v.getEspecificacao().getTransmissao(),
                                v.getEspecificacao().getCarga(),
                                v.getEspecificacao().getReboque(),
                                v.getEspecificacao().getTanque()
                        )
                ))
                .toList();

        return new CarroResponse(
                carro.getId(),
                carro.getModelo().getMarca().getNome(),
                carro.getModelo().getNome(),
                carro.getTipo(),
                versoes
        );
    }
}