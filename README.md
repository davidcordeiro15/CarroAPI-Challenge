# 🚗 CarroApi — API de Gerenciamento Automotivo


> **Stack:** Java 21 · Spring Boot 4 · Oracle DB · JPA/Hibernate · JWT · Bucket4j  
> **Porta:** `8081` | **Depende de:** AuthApi na porta `8080`

---

## Sumário

1. [Visão Geral da API](#1-visão-geral-da-api)
2. [Arquitetura](#2-arquitetura)
3. [Modelagem de Dados](#3-modelagem-de-dados)
4. [Endpoints da API](#4-endpoints-da-api)
5. [Uso do Swagger](#5-uso-do-swagger)
6. [Uso com Postman](#6-uso-com-postman)
7. [Boas Práticas Aplicadas](#7-boas-práticas-aplicadas)
8. [Integração com Banco de Dados](#8-integração-com-banco-de-dados)
9. [Segurança](#9-segurança)
10. [Como Executar o Projeto](#10-como-executar-o-projeto)
11. [Possíveis Melhorias](#11-possíveis-melhorias)

---

## 1. Visão Geral da API

### Objetivo

A **CarroApi** é um microsserviço RESTful responsável por gerenciar o **catálogo automotivo** de uma plataforma de veículos. Ela permite criar, consultar, atualizar e excluir registros de carros com toda a sua hierarquia de dados: marca, modelo, versões e especificações técnicas.

O projeto faz parte de um **ecossistema de microsserviços**, operando em conjunto obrigatório com a **AuthApi** (porta `8080`), que é responsável pela autenticação. A CarroApi não valida tokens JWT por conta própria — ela delega essa responsabilidade à AuthApi via chamada HTTP, o que é um padrão arquitetural importante de comunicação entre serviços.

### Conceito das Entidades

| Entidade | Responsabilidade | Exemplo Real |
|----------|-----------------|--------------|
| **Marca** | Fabricante do veículo | Toyota, Honda, Volkswagen |
| **Modelo** | Linha de produto da marca | Corolla, Civic, Gol |
| **Carro** | Um veículo específico de um modelo | Corolla 2024 sedan |
| **Versão** | Configuração/trim de um carro | GLi, XEi, Altis |
| **Especificação** | Dados técnicos detalhados de uma versão | Motor 2.0, 170cv, CVT |

> 💡 **Por que separar Versão de Especificação?**  
> Um mesmo carro pode ter 5 versões diferentes (básica, intermediária, topo de linha...). Cada versão tem suas próprias especificações técnicas. Se misturássemos tudo numa tabela, teríamos redundância massiva de dados. A separação permite alterar especificações sem afetar outras versões, e facilita comparações técnicas entre versões.

---

## 2. Arquitetura

### Arquitetura em Camadas (Layered Architecture)

A CarroApi segue o padrão de **arquitetura em camadas**, onde cada camada tem uma responsabilidade única e bem definida. Essa é a base da SOA (Arquitetura Orientada a Serviços).

```
┌─────────────────────────────────────────────────────────────────────┐
│                        CLIENTE (Postman/Browser)                     │
└───────────────────────────────┬─────────────────────────────────────┘
                                │ HTTP Request
                                ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    CAMADA DE SEGURANÇA (Filters)                     │
│  RateLimitFilter → JwtFilter → SecurityConfig                        │
│  (Rate limiting por IP) (Delega validação para AuthApi)              │
└───────────────────────────────┬─────────────────────────────────────┘
                                │ Requisição autenticada
                                ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    CAMADA DE APRESENTAÇÃO (Controller)               │
│  CarroController                                                     │
│  • Recebe requisições HTTP                                           │
│  • Valida entrada com @Valid                                         │
│  • Delega para o Service                                             │
│  • Retorna ResponseEntity com status HTTP correto                    │
└───────────────────────────────┬─────────────────────────────────────┘
                                │ Chama métodos de negócio
                                ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    CAMADA DE NEGÓCIO (Service)                       │
│  CarroService | TokenValidationService | AuthClient                  │
│  • Contém as regras de negócio                                       │
│  • Orquestra operações entre repositórios                            │
│  • Realiza mapeamentos DTO ↔ Entity                                  │
│  • Gerencia transações (@Transactional)                              │
└───────────────────────────────┬─────────────────────────────────────┘
                                │ Acessa dados
                                ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    CAMADA DE DADOS (Repository)                      │
│  CarroRepository | VersaoRepository | EspecificacaoRepository        │
│  ModeloRepository                                                    │
│  • Estende JpaRepository                                             │
│  • Gera queries SQL automaticamente                                  │
│  • Sem lógica de negócio                                             │
└───────────────────────────────┬─────────────────────────────────────┘
                                │ SQL
                                ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    BANCO DE DADOS (Oracle)                           │
│  Tabelas: CARROS, MODELOS, MARCAS, VERSOES, ESPECIFICACOES           │
└─────────────────────────────────────────────────────────────────────┘
```

### Por que cada camada existe?

**Controller** — É a "recepcionista" do sistema. Só recebe e encaminha, não processa lógica de negócio. Isso permite que você mude a forma de entrar (REST → GraphQL, por exemplo) sem alterar a lógica.

**Service** — É o "cérebro" do sistema. Toda regra de negócio fica aqui. Se a regra mudar (ex: um carro precisa ter no mínimo 2 versões), só o Service é alterado.

**Repository** — É a "camada de acesso a dados". Abstrai completamente o SQL. Amanhã, se mudar de Oracle para PostgreSQL, só as configurações do banco mudam — o Repository permanece idêntico.

**DTO (Data Transfer Object)** — São os "contratos" da API. Separam a estrutura interna (Entity) da estrutura exposta publicamente. Permitem evoluir o banco sem quebrar a API e vice-versa.

### Fluxo Completo de uma Requisição

```
POST /carros
Body: { modeloId: 1, tipo: "Sedan", versoes: [...] }
Authorization: Bearer eyJhbGci...

┌────────────────────────────────────────────────────────────────┐
│ 1. RateLimitFilter verifica se o IP não excedeu 20 req/min    │
│    → Se excedeu: 429 Too Many Requests                         │
└───────────────────────────────┬────────────────────────────────┘
                                ▼
┌────────────────────────────────────────────────────────────────┐
│ 2. JwtFilter lê o header Authorization                         │
│    → TokenValidationService verifica cache local (5min TTL)    │
│    → Cache miss: AuthClient chama POST http://localhost:8080   │
│                              /auth/validate                    │
│    → AuthApi responde: {valid: true, email: "...", role: "ADMIN"}│
│    → Resultado cacheado para próximas requisições              │
│    → SecurityContext recebe autenticação com ROLE_ADMIN        │
└───────────────────────────────┬────────────────────────────────┘
                                ▼
┌────────────────────────────────────────────────────────────────┐
│ 3. SecurityConfig verifica se a requisição está autenticada    │
│    → anyRequest().authenticated() → ✅ passa                   │
└───────────────────────────────┬────────────────────────────────┘
                                ▼
┌────────────────────────────────────────────────────────────────┐
│ 4. CarroController recebe a requisição                         │
│    → @PreAuthorize("hasRole('ADMIN')") → ✅ é ADMIN            │
│    → @Valid valida o CarroRequest                              │
│    → Chama service.create(request)                             │
└───────────────────────────────┬────────────────────────────────┘
                                ▼
┌────────────────────────────────────────────────────────────────┐
│ 5. CarroService.create(request)                                │
│    → modeloRepository.findById(request.modeloId())            │
│    → Cria entidade Carro, salva no banco                       │
│    → Loop: para cada VersaoRequest                             │
│         → Cria Versao, salva                                   │
│         → Cria Especificacao, salva                            │
│    → Retorna CarroResponse montado                             │
└───────────────────────────────┬────────────────────────────────┘
                                ▼
┌────────────────────────────────────────────────────────────────┐
│ 6. ResponseEntity.status(201).body(carroResponse)              │
│    → HTTP 201 Created + JSON no corpo                          │
└────────────────────────────────────────────────────────────────┘
```

### Por que isso é SOA (Arquitetura Orientada a Serviços)?

A SOA define que sistemas são compostos por **serviços independentes** que se comunicam via contratos bem definidos. A CarroApi exemplifica isso de forma clara:

1. **Serviço independente:** Roda na porta `8081`, tem seu próprio banco de dados, pode ser deployado e escalado independentemente da AuthApi

2. **Comunicação via contrato:** O `AuthClient` chama `POST /auth/validate` com um contrato JSON definido. Se a AuthApi mudar internamente mas mantiver o contrato, a CarroApi não percebe a diferença

3. **Delegação de responsabilidade:** A CarroApi não sabe validar tokens JWT — ela delega essa responsabilidade ao serviço que é especialista nisso (AuthApi)

4. **Baixo acoplamento:** A CarroApi só conhece a URL da AuthApi (`auth.api.url=http://localhost:8080`). Se a AuthApi mudar de servidor, basta atualizar essa propriedade

```
┌──────────────────┐    HTTP    ┌──────────────────┐
│    CarroApi      │◄──────────▶│    AuthApi        │
│   (porta 8081)   │  /auth/    │   (porta 8080)    │
│                  │  validate  │                   │
│ • Gerencia carros│            │ • Autentica users │
│ • Próprio banco  │            │ • Emite JWT       │
└──────────────────┘            └──────────────────┘
        │                               │
        ▼                               ▼
   Oracle DB                       Oracle DB
   (CARROS, etc.)                  (USUARIOS)
```

---

## 3. Modelagem de Dados

### Diagrama de Entidade-Relacionamento

```
MARCAS                  MODELOS                 CARROS
┌──────────────┐        ┌──────────────────┐    ┌─────────────────┐
│ id (PK)      │        │ id (PK)          │    │ id (PK)         │
│ nome         │◄──┐    │ marca_id (FK)    │◄──┐│ modelo_id (FK)  │
└──────────────┘   │    │ nome             │   ││ tipo            │
                   │    └──────────────────┘   │└─────────────────┘
                   │                           │         │
                   │    1:N                    │  1:N    │ 1:N
                   └─(Uma Marca tem N Modelos) └─────────┘
                                                         │
                                                         ▼
VERSOES                                         ESPECIFICACOES
┌─────────────────────┐                         ┌──────────────────────┐
│ id (PK)             │                         │ id (PK)              │
│ carro_id (FK)       │ 1:1                     │ versao_id (FK, único)│
│ nome                │◄───────────────────────▶│ motor                │
└─────────────────────┘                         │ potencia             │
        ▲                                        │ torque               │
        │ 1:N (Um Carro tem N Versões)           │ transmissao          │
        │                                        │ carga                │
   CARROS                                        │ reboque              │
                                                 │ tanque               │
                                                 └──────────────────────┘
```

### Tabelas e Seus Propósitos

#### Tabela `MARCAS`
```sql
CREATE TABLE MARCAS (
    id    NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nome  VARCHAR2(100) NOT NULL
);
```
Armazena apenas os fabricantes. Normalizada para evitar repetição: "Toyota" não precisa aparecer em 500 registros de carros, apenas uma vez aqui.

#### Tabela `MODELOS`
```sql
CREATE TABLE MODELOS (
    id       NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    marca_id NUMBER REFERENCES MARCAS(id),
    nome     VARCHAR2(100)
);
```
Liga marcas a linhas de produto. Um `SELECT modelo JOIN marca` retorna "Toyota Corolla" sem duplicar os dados da marca.

#### Tabela `CARROS`
```sql
CREATE TABLE CARROS (
    id        NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    modelo_id NUMBER REFERENCES MODELOS(id),
    tipo      VARCHAR2(50)
);
```
Representa uma instância de veículo. O `tipo` (Sedan, Hatch, SUV) diferencia variantes de carroceria dentro do mesmo modelo.

#### Tabela `VERSOES`
```sql
CREATE TABLE VERSOES (
    id       NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    carro_id NUMBER REFERENCES CARROS(id),
    nome     VARCHAR2(100)
);
```
Representa o "trim level" (acabamento) do veículo. Um Corolla pode ter GLi, XEi e Altis — três versões com especificações distintas.

#### Tabela `ESPECIFICACOES`
```sql
CREATE TABLE ESPECIFICACOES (
    id          NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    versao_id   NUMBER UNIQUE REFERENCES VERSOES(id),   -- chave 1:1
    motor       VARCHAR2(100),
    potencia    VARCHAR2(50),
    torque      VARCHAR2(50),
    transmissao VARCHAR2(50),
    carga       VARCHAR2(50),
    reboque     VARCHAR2(50),
    tanque      VARCHAR2(50)
);
```
O campo `versao_id UNIQUE` garante a relação **1:1** com `VERSOES`. Uma versão tem exatamente uma ficha técnica. Separar essas informações em tabela própria segue o **Princípio de Responsabilidade Única** (SRP): a tabela VERSOES se preocupa com o nome; a ESPECIFICACOES se preocupa com os dados técnicos.

### Explicação dos Relacionamentos

#### 1:N — Um para Muitos

```
MARCAS (1) ──────▶ (N) MODELOS
  A Toyota tem muitos modelos (Corolla, Camry, RAV4...)
  Mas cada modelo pertence a uma única marca

MODELOS (1) ─────▶ (N) CARROS
  O Corolla tem versões Sedan e Hatch (tipos diferentes)
  Mas cada carro tem um único modelo

CARROS (1) ──────▶ (N) VERSOES
  Um Corolla Sedan tem versões GLi, XEi, Altis
  Mas cada versão pertence a um único carro
```

No código Java, o relacionamento 1:N é mapeado com:
```java
// Entidade "um" (Carro)
@OneToMany(mappedBy = "carro", cascade = CascadeType.ALL, orphanRemoval = true)
private List<Versao> versoes = new ArrayList<>();

// Entidade "muitos" (Versao)
@ManyToOne
@JoinColumn(name = "carro_id")
private Carro carro;
```

O `cascade = CascadeType.ALL` significa: quando um `Carro` for deletado, o JPA automaticamente deleta todas as `Versao` associadas.  
O `orphanRemoval = true` garante que se uma `Versao` for removida da lista `versoes` do `Carro`, ela é automaticamente deletada do banco — sem precisar de DELETE manual.

#### 1:1 — Um para Um

```
VERSOES (1) ─────▶ (1) ESPECIFICACOES
  Cada versão tem exatamente uma ficha técnica
  Cada ficha técnica pertence a exatamente uma versão
```

```java
// Entidade "proprietária" (Especificacao)
@OneToOne
@JoinColumn(name = "versao_id")
private Versao versao;

// Entidade "mapeada" (Versao)
@OneToOne(mappedBy = "versao", cascade = CascadeType.ALL, orphanRemoval = true)
private Especificacao especificacao;
```

### Como Funciona o JOIN Entre Tabelas

Quando o `CarroService` chama `toFullResponse(carro)`, o Hibernate executa joins para montar a resposta completa. Em SQL puro, seria:

```sql
SELECT
    c.id          AS carro_id,
    ma.nome       AS marca,
    mo.nome       AS modelo,
    c.tipo,
    v.id          AS versao_id,
    v.nome        AS versao_nome,
    e.motor, e.potencia, e.torque,
    e.transmissao, e.carga, e.reboque, e.tanque
FROM CARROS c
    JOIN MODELOS      mo ON mo.id       = c.modelo_id
    JOIN MARCAS       ma ON ma.id       = mo.marca_id
    JOIN VERSOES      v  ON v.carro_id  = c.id
    JOIN ESPECIFICACOES e ON e.versao_id = v.id
WHERE c.id = 1;
```

O Hibernate faz isso automaticamente quando você navega pelas propriedades das entidades:
```java
carro.getModelo().getMarca().getNome()  // JOIN MODELOS JOIN MARCAS
carro.getVersoes()                      // JOIN VERSOES
v.getEspecificacao().getMotor()         // JOIN ESPECIFICACOES
```

### Impacto na Escalabilidade

Esta modelagem normalizada tem impactos diretos na escalabilidade:

- **Atualizar o nome de uma marca** → 1 UPDATE na tabela MARCAS reflete em todos os carros automaticamente
- **Adicionar campo técnico** → apenas a tabela ESPECIFICACOES muda
- **Indexação eficiente** → `marca_id`, `modelo_id`, `carro_id`, `versao_id` são FKs e são automaticamente candidatos a índices, tornando buscas por marca/modelo rápidas mesmo com milhões de carros
- **Escalabilidade horizontal** → como os dados estão normalizados, a leitura pode ser distribuída em réplicas de leitura sem redundância

---

## 4. Endpoints da API

**Base URL:** `http://localhost:8081`

> ⚠️ Todos os endpoints exigem autenticação. Obtenha um token JWT pela AuthApi (`POST http://localhost:8080/auth/login`) antes de testar.

---

### `POST /carros` — Criar Carro

**Função:** Cria um novo carro com suas versões e especificações técnicas em uma única operação.

**Acesso:** 🔒 Requer token JWT com `role: ADMIN`

**Por que só ADMIN pode criar?** Em um catálogo de veículos real, apenas usuários administrativos devem poder adicionar novos modelos ao catálogo. Usuários comuns só consultam.

**Método HTTP:** `POST` — usado para criação de recursos (RFC 7231). O servidor define o ID do novo recurso.

**Request:**
```http
POST /carros
Content-Type: application/json
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...

{
  "modeloId": 1,
  "tipo": "Sedan",
  "versoes": [
    {
      "nome": "GLi",
      "especificacao": {
        "motor": "2.0 Flex",
        "potencia": "170 cv",
        "torque": "20,9 kgfm",
        "transmissao": "Automático CVT",
        "carga": "Não informado",
        "reboque": "Não informado",
        "tanque": "55 litros"
      }
    },
    {
      "nome": "XEi",
      "especificacao": {
        "motor": "2.0 Flex",
        "potencia": "177 cv",
        "torque": "21,4 kgfm",
        "transmissao": "Automático CVT",
        "carga": "Não informado",
        "reboque": "Não informado",
        "tanque": "55 litros"
      }
    }
  ]
}
```

**Explicação dos campos do Request:**

| Campo | Tipo | Validação | Descrição |
|-------|------|-----------|-----------|
| `modeloId` | Long | Deve existir no banco | ID do Modelo previamente cadastrado |
| `tipo` | String | `@NotBlank` | Tipo de carroceria (Sedan, Hatch, SUV) |
| `versoes` | List | `@NotNull`, mínimo 1 item | Lista de versões/trims do carro |
| `versoes[].nome` | String | — | Nome da versão (GLi, XEi, etc.) |
| `versoes[].especificacao` | Object | — | Ficha técnica da versão |
| `especificacao.motor` | String | — | Descrição do motor |
| `especificacao.potencia` | String | — | Potência em cv |
| `especificacao.torque` | String | — | Torque em kgfm |
| `especificacao.transmissao` | String | — | Tipo de câmbio |
| `especificacao.carga` | String | — | Capacidade de carga |
| `especificacao.reboque` | String | — | Capacidade de reboque |
| `especificacao.tanque` | String | — | Volume do tanque |

**Response — 201 Created:**
```json
{
  "carroId": 3,
  "marca": "Toyota",
  "modelo": "Corolla",
  "tipo": "Sedan",
  "versoes": [
    {
      "versaoId": 5,
      "nome": "GLi",
      "especificacao": {
        "motor": "2.0 Flex",
        "potencia": "170 cv",
        "torque": "20,9 kgfm",
        "transmissao": "Automático CVT",
        "carga": "Não informado",
        "reboque": "Não informado",
        "tanque": "55 litros"
      }
    },
    {
      "versaoId": 6,
      "nome": "XEi",
      "especificacao": {
        "motor": "2.0 Flex",
        "potencia": "177 cv",
        "torque": "21,4 kgfm",
        "transmissao": "Automático CVT",
        "carga": "Não informado",
        "reboque": "Não informado",
        "tanque": "55 litros"
      }
    }
  ]
}
```

**Por que o response é diferente do request?**  
O `CarroRequest` recebe `modeloId` (só o ID). O `CarroResponse` retorna `marca` e `modelo` (os nomes completos). Isso é o padrão DTO em ação: o cliente não precisa saber o ID interno da marca — ele quer ver "Toyota Corolla". O Service faz o JOIN e monta essa resposta de forma transparente.

**Respostas de Erro:**

| Status | Motivo |
|--------|--------|
| `400` | Campos inválidos (`tipo` em branco, `versoes` nulo) |
| `401` | Token ausente ou inválido |
| `403` | Token válido mas role não é ADMIN |
| `429` | Rate limit excedido |
| `500` | Modelo não encontrado ou erro interno |

---

### `GET /carros` — Listar Todos os Carros

**Função:** Retorna a lista completa de todos os carros cadastrados com suas versões e especificações.

**Acesso:** 🔒 Requer token JWT válido (qualquer role)

**Método HTTP:** `GET` — semântica de leitura, idempotente e seguro (não modifica dados).

**Request:**
```http
GET /carros
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

**Response — 200 OK:**
```json
[
  {
    "carroId": 1,
    "marca": "Volkswagen",
    "modelo": "Gol",
    "tipo": "Hatch",
    "versoes": [
      {
        "versaoId": 1,
        "nome": "1.0 MPI",
        "especificacao": {
          "motor": "1.0 MPI Total Flex",
          "potencia": "82 cv",
          "torque": "10,2 kgfm",
          "transmissao": "Manual 5 marchas",
          "carga": "480 kg",
          "reboque": "Não permitido",
          "tanque": "46 litros"
        }
      }
    ]
  },
  {
    "carroId": 2,
    "marca": "Honda",
    "modelo": "Civic",
    "tipo": "Sedan",
    "versoes": [ ... ]
  }
]
```

**Consideração de performance:**  
Para um catálogo com milhares de carros, retornar tudo sem paginação pode ser lento. Veja a seção de melhorias para a implementação de paginação com `Page<CarroResponse>`.

---

### `GET /carros/{id}` — Buscar Carro por ID

**Função:** Retorna um único carro pelo seu ID, com todas as versões e especificações.

**Acesso:** 🔒 Requer token JWT válido (qualquer role)

**Request:**
```http
GET /carros/3
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

**Response — 200 OK:** (mesmo formato do item individual do GET /carros)

**Response — 500 Internal Server Error** (se ID não encontrado):
```json
{
  "error": "Erro interno"
}
```

> ⚠️ **Nota técnica identificada no código:** O `GlobalExceptionHandler` trata qualquer `Exception` genérica com status `500`. Idealmente, um `RuntimeException("Carro não encontrado")` deveria retornar `404 Not Found`. Esta é uma melhoria apontada na seção 11.

---

### `PUT /carros/{id}` — Atualizar Carro

**Função:** Substitui completamente as versões e especificações de um carro existente. Também atualiza o tipo do carro.

**Acesso:** 🔒 Requer token JWT com `role: ADMIN`

**Método HTTP:** `PUT` — substitui o recurso completo (RFC 7231). Diferente do `PATCH`, que faria atualização parcial.

**Estratégia de atualização adotada:**  
O `CarroService.update()` usa a estratégia **delete-and-recreate**: deleta todas as versões antigas do carro e insere as novas. Isso é mais simples do que fazer diff entre versões existentes e novas.

```java
// CarroService.java
versaoRepository.deleteByCarroId(id);   // Apaga todas as versões antigas
for (VersaoRequest vReq : request.versoes()) {
    // Cria cada versão e especificação do zero
}
```

**Request:**
```http
PUT /carros/3
Content-Type: application/json
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...

{
  "modeloId": 1,
  "tipo": "Sedan",
  "versoes": [
    {
      "nome": "Altis Premium",
      "especificacao": {
        "motor": "2.0 Flex",
        "potencia": "177 cv",
        "torque": "21,4 kgfm",
        "transmissao": "Automático CVT",
        "carga": "Não informado",
        "reboque": "Não informado",
        "tanque": "55 litros"
      }
    }
  ]
}
```

**Response — 200 OK:** Retorna o carro atualizado no mesmo formato do `CarroResponse`.

**Por que `@Transactional`?**  
A anotação garante que todas as operações (delete versões antigas + insert versões novas) aconteçam numa única transação de banco. Se qualquer operação falhar, o banco é revertido ao estado anterior — evitando carros sem versões em caso de erro parcial.

---

### `DELETE /carros/{id}` — Deletar Carro

**Função:** Remove permanentemente um carro e, em cascata, todas as suas versões e especificações.

**Acesso:** 🔒 Requer token JWT com `role: ADMIN`

**Request:**
```http
DELETE /carros/3
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

**Response — 204 No Content** (sem corpo na resposta)

**Por que 204 e não 200?**  
O código `204` indica sucesso mas sem conteúdo para retornar. Como o recurso foi deletado, não há objeto para responder — `204` é semanticamente correto (RFC 7231).

**O que é deletado em cascata:**
```
DELETE carro id=3
  ├── DELETE ESPECIFICACOES WHERE versao_id IN (SELECT id FROM VERSOES WHERE carro_id=3)
  ├── DELETE VERSOES WHERE carro_id = 3
  └── DELETE CARROS WHERE id = 3
```

Isso é realizado pelo código:
```java
versaoRepository.deleteByCarroId(id);  // Remove versões (e cascata remove especificações)
repository.delete(carro);              // Remove o carro
```

---

## 5. Uso do Swagger

### Pré-requisito: Autenticação

A CarroApi **não tem endpoint de login**. O login é feito na AuthApi. Siga os passos:

**Passo 1 — Obter token pela AuthApi:**

Acesse `http://localhost:8080/swagger-ui/index.html` e faça `POST /auth/login`:
```json
{
  "email": "admin@api.com",
  "senha": "admin123"
}
```
Copie o `token` da resposta.

### Acessar o Swagger da CarroApi

Com a CarroApi rodando:
```
http://localhost:8081/swagger-ui/index.html
```

### Interface do Swagger UI

```
┌────────────────────────────────────────────────────────┐
│  CarroApi                                              │
│                                          [Authorize 🔒]│
├────────────────────────────────────────────────────────┤
│  ▼ carros-controller                                   │
│    POST   /carros        Criar carro                   │
│    GET    /carros        Listar todos                  │
│    GET    /carros/{id}   Buscar por ID                 │
│    PUT    /carros/{id}   Atualizar                     │
│    DELETE /carros/{id}   Deletar                       │
└────────────────────────────────────────────────────────┘
```

### Passo a Passo para Testar

**1. Autorizar com Bearer Token:**
- Clique em **"Authorize 🔒"** no canto superior direito
- No campo **"bearerAuth (http, Bearer)"**, cole apenas o token (sem `Bearer `)
- Clique em **"Authorize"** → **"Close"**

**2. Testar `POST /carros`:**
- Clique no endpoint → **"Try it out"**
- Substitua o body exemplo pelos dados reais
- Certifique-se de que o `modeloId` existe no banco
- Clique em **"Execute"**
- Verifique se o `Code` retornado é `201`

**3. Testar `GET /carros`:**
- Clique no endpoint → **"Try it out"** → **"Execute"**
- Não precisa preencher body (é um GET)
- A lista completa de carros aparecerá em "Response body"

**4. Testar `GET /carros/{id}`:**
- Clique no endpoint → **"Try it out"**
- Preencha o campo `id` com o ID do carro (ex: `1`)
- Execute e veja os detalhes

**5. Testar `DELETE /carros/{id}`:**
- Requer role ADMIN
- Preencha o `id` e execute
- Response `204` = sucesso

**Interpretando as respostas:**

| Code | Significado |
|------|-------------|
| `200` | Sucesso com corpo |
| `201` | Criado com sucesso |
| `204` | Sucesso sem corpo |
| `400` | Dados inválidos |
| `401` | Sem autenticação |
| `403` | Sem permissão (role insuficiente) |
| `429` | Muitas requisições |
| `500` | Erro interno |

---

## 6. Uso com Postman

### Configuração Inicial

**1. Variáveis de Ambiente (recomendado):**

Crie um environment no Postman chamado "CarroApi Local" com as variáveis:
```
base_url_auth  = http://localhost:8080
base_url_carro = http://localhost:8081
jwt_token      = (deixe vazio por enquanto)
```

**2. Obter o Token Automaticamente:**

Crie uma requisição de login na AuthApi e use o script de pós-resposta para capturar o token:

```
Método: POST
URL:    {{base_url_auth}}/auth/login
Body (raw JSON):
{
  "email": "admin@api.com",
  "senha": "admin123"
}
```

Na aba **Scripts → Post-response**:
```javascript
const response = pm.response.json();
pm.environment.set("jwt_token", response.token);
console.log("Token salvo:", response.token.substring(0, 30) + "...");
```

Agora `{{jwt_token}}` estará disponível em todas as requisições.

### Requisições da CarroApi

**Header obrigatório em todas as requisições:**

| Key | Value |
|-----|-------|
| `Authorization` | `Bearer {{jwt_token}}` |
| `Content-Type` | `application/json` |

---

**Listar todos os carros:**
```
Método: GET
URL:    {{base_url_carro}}/carros
```

---

**Buscar carro por ID:**
```
Método: GET
URL:    {{base_url_carro}}/carros/1
```

---

**Criar um novo carro:**
```
Método: POST
URL:    {{base_url_carro}}/carros
Body (raw JSON):
{
  "modeloId": 1,
  "tipo": "SUV",
  "versoes": [
    {
      "nome": "Adventure",
      "especificacao": {
        "motor": "1.3 Turbo Flex",
        "potencia": "185 cv",
        "torque": "27,5 kgfm",
        "transmissao": "Automático 6 marchas",
        "carga": "525 kg",
        "reboque": "1.500 kg",
        "tanque": "58 litros"
      }
    }
  ]
}
```

Use o script Post-response para salvar o ID do carro criado:
```javascript
const res = pm.response.json();
pm.environment.set("carro_id", res.carroId);
console.log("Carro criado com ID:", res.carroId);
```

---

**Atualizar carro:**
```
Método: PUT
URL:    {{base_url_carro}}/carros/{{carro_id}}
Body (raw JSON):
{
  "modeloId": 1,
  "tipo": "SUV",
  "versoes": [
    {
      "nome": "Adventure Plus",
      "especificacao": {
        "motor": "1.3 Turbo Flex",
        "potencia": "190 cv",
        "torque": "28,0 kgfm",
        "transmissao": "Automático 6 marchas",
        "carga": "525 kg",
        "reboque": "1.800 kg",
        "tanque": "58 litros"
      }
    }
  ]
}
```

---

**Deletar carro:**
```
Método: DELETE
URL:    {{base_url_carro}}/carros/{{carro_id}}
```

Response esperado: `204 No Content` (sem corpo)

---

**Testar token expirado (cenário de erro):**
```
Método: GET
URL:    {{base_url_carro}}/carros
Headers: Authorization: Bearer token_invalido_aqui
```
Esperado: `401 Unauthorized`

---

**Testar sem permissão de ADMIN (cenário de erro):**
```
// Primeiro faça login com um USER comum (não ADMIN)
// Então tente criar um carro:
Método: POST
URL:    {{base_url_carro}}/carros
Headers: Authorization: Bearer {{token_de_usuario_comum}}
Body: { ... }
```
Esperado: `403 Forbidden`

---

## 7. Boas Práticas Aplicadas

### ✅ Uso Correto dos Métodos HTTP

A API segue rigorosamente a semântica HTTP (RFC 7231):

| Método | Operação | Idempotente? | Seguro? | Usado em |
|--------|----------|:------------:|:-------:|----------|
| `GET` | Leitura | ✅ Sim | ✅ Sim | Listar e buscar carros |
| `POST` | Criação | ❌ Não | ❌ Não | Criar carro |
| `PUT` | Substituição total | ✅ Sim | ❌ Não | Atualizar carro |
| `DELETE` | Remoção | ✅ Sim | ❌ Não | Deletar carro |

**Idempotente** = executar N vezes produz o mesmo resultado. Um `DELETE /carros/1` executado 3 vezes tem o mesmo efeito final (carro deletado) que executado 1 vez.

### ✅ Padrão DTO (Data Transfer Object)

O projeto usa **Records do Java 14+** como DTOs, que são imutáveis por natureza:

```java
// CarroRequest.java — DTO de ENTRADA
public record CarroRequest(
    Long modeloId,
    @NotBlank String tipo,
    @NotNull List<VersaoRequest> versoes
) {}

// CarroResponse.java — DTO de SAÍDA
public record CarroResponse(
    Long carroId,
    String marca,      // ← não existe na entidade Carro!
    String modelo,     // ← vem de carro.getModelo().getNome()
    String tipo,
    List<VersaoResponse> versoes
) {}
```

**Por que Records?** São imutáveis (campos `final`), não permitem alteração acidental de dados, geram `equals()`, `hashCode()` e `toString()` automaticamente, e têm sintaxe mais concisa que classes tradicionais.

**Por que separar Request de Response?** O request precisa de validação (`@NotBlank`) e o response não. O response pode ter campos calculados (`marca`, `modelo`) que não existem no request. Isso segue o princípio da **segregação de interfaces**.

### ✅ Tratamento Centralizado de Erros

O `GlobalExceptionHandler` com `@RestControllerAdvice` intercepta exceções de qualquer controller:

```java
@ExceptionHandler(MethodArgumentNotValidException.class)
public ResponseEntity<?> validation(MethodArgumentNotValidException ex) {
    return ResponseEntity.badRequest().body(
        Map.of("error", "Dados inválidos")
    );
}

@ExceptionHandler(Exception.class)
public ResponseEntity<?> handle(Exception ex) {
    return ResponseEntity.status(500).body(
        Map.of("error", "Erro interno")
    );
}
```

**Por que isso é importante?** Sem esse handler, o Spring retornaria stack traces completos ao cliente, expondo detalhes da implementação interna — uma vulnerabilidade de segurança. Com o handler, o cliente recebe apenas uma mensagem controlada.

### ✅ Organização do Código

```
config/       → Configurações (Security, Swagger, RestTemplate)
controller/   → Endpoints REST (apenas recebe e delega)
dto/          → Contratos de entrada e saída
entity/       → Mapeamento do banco de dados
exception/    → Tratamento centralizado de erros
repository/   → Acesso a dados (sem lógica de negócio)
security/     → Filtros JWT e Rate Limiting
service/      → Lógica de negócio e orquestração
```

Essa estrutura segue o padrão **"package by layer"**, onde cada pacote agrupa classes pela sua responsabilidade técnica.

### ✅ Injeção de Dependência via Construtor

```java
@Service
public class CarroService {
    private final CarroRepository repository;

    // Injeção via construtor (não @Autowired em campo)
    public CarroService(CarroRepository repository, ...) {
        this.repository = repository;
    }
}
```

**Por que via construtor e não `@Autowired`?**
1. O campo é `final` — garante imutabilidade após a construção
2. Facilita testes unitários (pode passar um mock no construtor)
3. Torna as dependências explícitas e obrigatórias

---

## 8. Integração com Banco de Dados

### Configuração da Conexão

```properties
# application.properties
spring.datasource.url=jdbc:oracle:thin:@oracle.fiap.com.br:1521:orcl
spring.datasource.username=RM557538
spring.datasource.driver-class-name=oracle.jdbc.OracleDriver
```

O Spring Boot usa um **pool de conexões** (HikariCP por padrão) para reutilizar conexões ao banco. Em vez de abrir uma nova conexão TCP a cada requisição (operação cara), o pool mantém conexões abertas e as empresta às threads.

### Como o JPA/Hibernate Funciona

**JPA (Jakarta Persistence API)** é uma especificação (contrato). **Hibernate** é a implementação mais usada desse contrato. A relação é similar a `List` (especificação) vs `ArrayList` (implementação).

O fluxo é:
```
Código Java                JPA/Hibernate               Oracle DB
─────────                  ─────────────               ─────────
repository.save(carro) →   INSERT INTO CARROS...    →  Executa SQL
repository.findById(1) →   SELECT * FROM CARROS     →  Retorna dados
                           WHERE id = ?
```

### Geração Automática do Schema

```properties
spring.jpa.hibernate.ddl-auto=update
```

O `update` instrui o Hibernate a verificar o banco ao iniciar e:
- Criar tabelas que não existem
- Adicionar colunas novas que foram adicionadas às entidades
- **Nunca deletar** dados existentes

Outros modos: `create` (apaga tudo e recria), `create-drop` (apaga ao encerrar), `validate` (só valida, não altera), `none` (não faz nada — para produção).

### Como as Queries são Geradas

O Spring Data JPA gera queries a partir do nome dos métodos no Repository:

```java
// VersaoRepository.java
void deleteByCarroId(Long carroId);
// Gera: DELETE FROM VERSOES WHERE carro_id = ?

List<Versao> findByCarroId(Long carroId);
// Gera: SELECT * FROM VERSOES WHERE carro_id = ?
```

```java
// EspecificacaoRepository.java
Optional<Especificacao> findByVersaoId(Long versaoId);
// Gera: SELECT * FROM ESPECIFICACOES WHERE versao_id = ?

void deleteByVersaoId(Long versaoId);
// Gera: DELETE FROM ESPECIFICACOES WHERE versao_id = ?
```

Essa convenção de nomes (`findBy`, `deleteBy`, `existsBy`) elimina a necessidade de escrever SQL na maioria dos casos.

### Logs SQL no Console

A configuração abaixo imprime o SQL gerado pelo Hibernate e os valores dos parâmetros:

```properties
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
logging.level.org.hibernate.SQL=DEBUG
logging.level.org.hibernate.type.descriptor.sql.BasicBinder=TRACE
```

No console, você verá:
```sql
Hibernate:
    insert
    into
        CARROS (modelo_id, tipo)
    values
        (?, ?)

TRACE o.h.t.d.s.BasicBinder - binding parameter [1] as [BIGINT] - [1]
TRACE o.h.t.d.s.BasicBinder - binding parameter [2] as [VARCHAR] - [Sedan]
```

Esses logs são valiosos para desenvolvimento mas devem ser desabilitados em produção para performance e segurança.

### Cascata e Gerenciamento do Ciclo de Vida

```java
// Carro.java
@OneToMany(mappedBy = "carro", cascade = CascadeType.ALL, orphanRemoval = true)
private List<Versao> versoes = new ArrayList<>();
```

`CascadeType.ALL` propaga todas as operações (PERSIST, MERGE, REMOVE, REFRESH, DETACH) do `Carro` para suas `Versoes`. Quando você salva um `Carro`, o JPA automaticamente salva suas `Versoes` e `Especificacoes` associadas — sem precisar chamar `versaoRepository.save()` explicitamente (embora o código atual o faça de forma manual por mais controle).

---

## 9. Segurança

### Arquitetura de Segurança da CarroApi

A CarroApi tem uma abordagem de segurança **baseada em delegação**. Ela não valida tokens JWT internamente — delega para a AuthApi:

```
CarroApi recebe token JWT
    ↓
TokenValidationService.validate(token)
    ↓
Verifica cache local (TTL 5 minutos)
    ↓ (cache miss)
AuthClient.validateToken(token)
    ↓
POST http://localhost:8080/auth/validate
    ↓
AuthApi valida assinatura, expiração e existência do usuário
    ↓
{valid: true, email: "...", role: "ADMIN"}
    ↓
Resultado é cacheado por 5 minutos
    ↓
SecurityContext recebe autenticação
```

### Cache de Tokens (`TokenValidationService`)

Uma chamada HTTP a cada requisição para validar o token seria muito cara. O `TokenValidationService` implementa um cache em memória com TTL de 5 minutos:

```java
private static final long TTL_SECONDS = 300;  // 5 minutos

public TokenValidationResponse validate(String token) {
    CachedToken cached = cache.get(token);

    // Usa o cache se não expirou
    if (cached != null && !cached.isExpired()) {
        return cached.response;
    }

    // Cache miss: chama a AuthApi
    TokenValidationResponse response = authClient.validateToken(token);

    // Salva no cache
    if (response != null && response.valid()) {
        cache.put(token, new CachedToken(response));
    }

    return response;
}
```

**Trade-off do cache:** Se um usuário tiver seu acesso revogado na AuthApi, ele ainda poderá usar o token por até 5 minutos enquanto o cache não expirar. Em um sistema bancário isso seria inaceitável; para um catálogo de carros, 5 minutos é aceitável.

### Validação de Entrada

```java
// CarroRequest.java
public record CarroRequest(
    Long modeloId,
    @NotBlank String tipo,       // Não pode ser nulo ou em branco
    @NotNull List<VersaoRequest> versoes  // Não pode ser nulo
) {}
```

O `@Valid` no controller ativa a validação antes de chamar o service:
```java
public ResponseEntity<CarroResponse> create(@Valid @RequestBody CarroRequest request) {
```

Se a validação falhar, o `MethodArgumentNotValidException` é lançado e capturado pelo `GlobalExceptionHandler`, retornando 400 sem chegar ao banco.

### Rate Limiting (`RateLimitFilter`)

```java
// RateLimitFilter.java — 20 requisições por minuto por IP
Bucket.builder()
    .addLimit(Bandwidth.classic(20, Refill.greedy(20, Duration.ofMinutes(1))))
    .build();
```

O `ConcurrentHashMap<String, Bucket>` mantém um bucket por IP de cliente. O algoritmo **Token Bucket** é usado: o bucket começa com 20 tokens, cada requisição consome 1 token, e tokens são reabastecidos a 20 por minuto.

O cabeçalho `X-Forwarded-For` é considerado para identificar o IP real atrás de proxies e load balancers.

### RBAC via @PreAuthorize

```java
// Somente ADMIN pode criar
@PreAuthorize("hasRole('ADMIN')")
@PostMapping
public ResponseEntity<CarroResponse> create(...) { }

// Somente ADMIN pode atualizar
@PreAuthorize("hasRole('ADMIN')")
@PutMapping("/{id}")
public ResponseEntity<CarroResponse> update(...) { }

// Somente ADMIN pode deletar
@PreAuthorize("hasRole('ADMIN')")
@DeleteMapping("/{id}")
public ResponseEntity<Void> delete(...) { }

// Qualquer usuário autenticado pode ler
@GetMapping
public ResponseEntity<List<CarroResponse>> list() { }
```

### Possíveis Melhorias de Segurança

**HTTPS obrigatório em produção:**
```properties
server.ssl.key-store=classpath:keystore.p12
server.ssl.key-store-password=${SSL_KEYSTORE_PASSWORD}
server.port=8443
```
Sem HTTPS, o token JWT trafega em texto claro e pode ser interceptado.

**CORS configurado explicitamente:**
```java
@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(List.of("https://meu-frontend.com"));
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE"));
    return new UrlBasedCorsConfigurationSource().apply(config);
}
```

**Credenciais via variáveis de ambiente:**
```properties
# Nunca commitar senhas em texto puro!
spring.datasource.password=${DB_PASSWORD}
jwt.secret=${JWT_SECRET}
```

---

## 10. Como Executar o Projeto

### Pré-requisitos

| Software | Versão | Verificar |
|----------|--------|-----------|
| Java JDK | 21+ | `java -version` |
| Maven | 3.9+ | `mvn -version` |
| Oracle DB | 19c+ | Conexão FIAP |
| **AuthApi** | Rodando | `curl http://localhost:8080/swagger-ui/index.html` |

> ⚠️ **A CarroApi DEPENDE da AuthApi estar rodando na porta 8080.** Se a AuthApi não estiver ativa, nenhuma requisição à CarroApi poderá ser autenticada (todas retornarão 401).

### Ordem de Inicialização

```
1. Sobe o Oracle Database
2. Sobe a AuthApi (porta 8080)
3. Sobe a CarroApi (porta 8081)
```

### Passo 1 — Clonar o Repositório

```bash
git clone https://github.com/seu-usuario/CarroAPI-Challenge.git
cd CarroAPI-Challenge
```

### Passo 2 — Configurar o Banco de Dados

Edite `src/main/resources/application.properties`:

```properties
spring.datasource.url=jdbc:oracle:thin:@oracle.fiap.com.br:1521:orcl
spring.datasource.username=SEU_RM_AQUI
spring.datasource.password=SUA_SENHA_AQUI
```

### Passo 3 — Confirmar que a AuthApi está rodando

```bash
curl -s http://localhost:8080/swagger-ui/index.html | head -5
# Deve retornar HTML do Swagger
```

### Passo 4 — Compilar e Executar

```bash
# Via Maven Wrapper (recomendado)
./mvnw spring-boot:run

# Windows
mvnw.cmd spring-boot:run

# Ou compilar JAR e executar
./mvnw clean package -DskipTests
java -jar target/CarroApi-0.0.1-SNAPSHOT.jar
```

Você verá no console:
```
Started CarroApiApplication in 4.234 seconds (JVM running for 4.8)
```

### Passo 5 — Verificar que está rodando

```bash
curl http://localhost:8081/swagger-ui/index.html
# Deve retornar HTML
```

### Passo 6 — Cadastrar dados base (Marcas e Modelos)

A CarroApi gerencia `Carros`, mas `Marcas` e `Modelos` precisam existir previamente no banco. Insira dados de teste diretamente no Oracle:

```sql
-- Inserir marcas
INSERT INTO MARCAS (nome) VALUES ('Toyota');
INSERT INTO MARCAS (nome) VALUES ('Honda');
INSERT INTO MARCAS (nome) VALUES ('Volkswagen');

-- Inserir modelos (marca_id = 1 = Toyota)
INSERT INTO MODELOS (marca_id, nome) VALUES (1, 'Corolla');
INSERT INTO MODELOS (marca_id, nome) VALUES (1, 'Camry');
INSERT INTO MODELOS (marca_id, nome) VALUES (2, 'Civic');
INSERT INTO MODELOS (marca_id, nome) VALUES (3, 'Gol');

COMMIT;
```

### Passo 7 — Testar o Fluxo Completo

```bash
# 1. Obter token pela AuthApi (precisa de usuário ADMIN cadastrado)
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@api.com","senha":"admin123"}' \
  | grep -o '"token":"[^"]*"' | cut -d'"' -f4)

echo "Token: ${TOKEN:0:50}..."

# 2. Criar um carro
curl -X POST http://localhost:8081/carros \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "modeloId": 1,
    "tipo": "Sedan",
    "versoes": [{
      "nome": "XEi",
      "especificacao": {
        "motor": "2.0 Flex",
        "potencia": "177 cv",
        "torque": "21,4 kgfm",
        "transmissao": "Automático CVT",
        "carga": "Não informado",
        "reboque": "Não informado",
        "tanque": "55 litros"
      }
    }]
  }'

# 3. Listar todos os carros
curl http://localhost:8081/carros \
  -H "Authorization: Bearer $TOKEN"

# 4. Buscar o carro criado (substitua 1 pelo ID retornado)
curl http://localhost:8081/carros/1 \
  -H "Authorization: Bearer $TOKEN"

# 5. Deletar o carro
curl -X DELETE http://localhost:8081/carros/1 \
  -H "Authorization: Bearer $TOKEN"
# Esperado: 204 No Content
```

### Estrutura de Arquivos do Projeto

```
CarroAPI-Challenge-main/
├── pom.xml                              # Dependências Maven
├── logback-spring.xml                   # Config de logs estruturados (JSON)
└── src/
    └── main/
        ├── java/com/challenge/CarroApi/
        │   ├── CarroApiApplication.java          # Entry point
        │   ├── config/
        │   │   ├── RestConfig.java               # Bean do RestTemplate
        │   │   ├── SecurityConfig.java           # Filtros e políticas de acesso
        │   │   └── SwaggerConfig.java            # Configuração do Bearer no OpenAPI
        │   ├── controller/
        │   │   └── CarroController.java          # Endpoints REST /carros
        │   ├── dto/
        │   │   ├── CarroRequest.java             # DTO de entrada do carro
        │   │   ├── CarroResponse.java            # DTO de saída do carro
        │   │   ├── VersaoRequest.java            # DTO de entrada da versão
        │   │   ├── VersaoResponse.java           # DTO de saída da versão
        │   │   ├── EspecificacaoRequest.java     # DTO de entrada da especificação
        │   │   ├── EspecificacaoResponse.java    # DTO de saída da especificação
        │   │   └── TokenValidationResponse.java  # Resposta da AuthApi
        │   ├── entity/
        │   │   ├── Marca.java                    # Tabela MARCAS
        │   │   ├── Modelo.java                   # Tabela MODELOS
        │   │   ├── Carro.java                    # Tabela CARROS
        │   │   ├── Versao.java                   # Tabela VERSOES
        │   │   └── Especificacao.java            # Tabela ESPECIFICACOES
        │   ├── exception/
        │   │   └── GlobalExceptionHandler.java   # Tratamento centralizado de erros
        │   ├── repository/
        │   │   ├── CarroRepository.java          # CRUD de carros
        │   │   ├── ModeloRepository.java         # CRUD de modelos
        │   │   ├── VersaoRepository.java         # CRUD de versões + queries derivadas
        │   │   └── EspecificacaoRepository.java  # CRUD de especificações
        │   ├── security/
        │   │   ├── JwtFilter.java                # Intercepta e valida tokens
        │   │   ├── JwtService.java               # Lógica de validação JWT local
        │   │   ├── RateLimitFilter.java          # Rate limiting por IP (Bucket4j)
        │   │   └── TokenCacheService.java        # Cache de tokens validados
        │   └── service/
        │       ├── AuthClient.java               # HTTP client para a AuthApi
        │       ├── CarroService.java             # Regras de negócio dos carros
        │       └── TokenValidationService.java   # Orquestra validação + cache
        └── resources/
            └── application.properties            # Configurações da aplicação
```

---

## 11. Possíveis Melhorias

### 📄 Paginação

**Problema atual:** `GET /carros` retorna todos os registros de uma vez. Com 10.000 carros no banco, isso causa alto consumo de memória e lentidão.

**Solução:** Spring Data JPA suporta paginação nativa:

```java
// CarroRepository.java
public interface CarroRepository extends JpaRepository<Carro, Long> {
    // JpaRepository já herda Page<Carro> findAll(Pageable pageable)
}

// CarroController.java
@GetMapping
public ResponseEntity<Page<CarroResponse>> list(
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "20") int size) {

    Pageable pageable = PageRequest.of(page, size, Sort.by("id").descending());
    return ResponseEntity.ok(service.list(pageable));
}
```

Uso: `GET /carros?page=0&size=20`

Response incluirá metadados:
```json
{
  "content": [...],
  "totalElements": 1500,
  "totalPages": 75,
  "number": 0,
  "size": 20
}
```

### 🔍 Filtros e Busca

**Melhoria:** Permitir filtrar carros por marca, modelo ou tipo:

```java
// Usando Specifications do Spring Data JPA
@GetMapping
public ResponseEntity<List<CarroResponse>> list(
    @RequestParam(required = false) String marca,
    @RequestParam(required = false) String tipo) {
    return ResponseEntity.ok(service.listWithFilters(marca, tipo));
}
```

Uso: `GET /carros?marca=Toyota&tipo=Sedan`

### ⚡ Cache com Spring Cache

**Problema:** A cada `GET /carros`, o banco é consultado mesmo que os dados não tenham mudado.

**Solução:**
```java
@Cacheable("carros")
public List<CarroResponse> list() { ... }

@CacheEvict(value = "carros", allEntries = true)
public CarroResponse create(CarroRequest request) { ... }
```

### 🔎 Tratamento de Erros Mais Específico

**Problema identificado:** `RuntimeException("Carro não encontrado")` retorna 500. O correto seria 404.

**Solução:**
```java
// Criar exceção customizada
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}

// Handler específico no GlobalExceptionHandler
@ExceptionHandler(ResourceNotFoundException.class)
public ResponseEntity<?> notFound(ResourceNotFoundException ex) {
    return ResponseEntity.status(404).body(Map.of("error", ex.getMessage()));
}
```

### 📊 Logs Estruturados

O projeto já inclui `logstash-logback-encoder` no `pom.xml` e o `logback-spring.xml`. Isso permite logs em formato JSON, facilitando indexação em ferramentas como ELK Stack (Elasticsearch, Logstash, Kibana) ou Datadog:

```json
{
  "timestamp": "2025-04-30T14:32:10.123Z",
  "level": "INFO",
  "logger": "c.c.CarroApi.service.CarroService",
  "message": "Carro criado com ID: 5",
  "modeloId": 1,
  "tipo": "Sedan"
}
```

### 🔄 Endpoints para Marcas e Modelos

**Situação atual:** Marcas e Modelos precisam ser inseridos diretamente no banco SQL.

**Melhoria:** Criar controllers e services para `Marca` e `Modelo`, tornando a API completa e self-contained:
- `POST /marcas`
- `GET /marcas`
- `POST /marcas/{id}/modelos`
- `GET /marcas/{id}/modelos`

### 📝 Auditoria

**Melhoria:** Registrar quem criou/modificou cada carro e quando:

```java
// CarroAuditoria.java
@Entity
public class CarroAuditoria {
    private Long carroId;
    private String acao;      // CRIADO, ATUALIZADO, DELETADO
    private String usuario;   // email extraído do token
    private LocalDateTime quando;
}
```

---

## Stack Tecnológica Completa

| Tecnologia | Versão | Papel |
|------------|--------|-------|
| Java | 21 | Linguagem principal |
| Spring Boot | 4.0.6 | Framework web e IoC |
| Spring Security | (via Boot) | Filtros de segurança e RBAC |
| Spring Data JPA | (via Boot) | Abstração de acesso ao banco |
| Hibernate | (via Boot) | Implementação JPA + ORM |
| Oracle JDBC (ojdbc11) | (via Boot) | Driver do banco Oracle |
| JJWT | 0.11.5 | Parsing e validação de JWT local |
| Bucket4j | 8.10.1 | Rate limiting por Token Bucket |
| Springdoc OpenAPI | 3.0.2 | Documentação Swagger automática |
| Lombok | (via Boot) | Redução de boilerplate |
| Logstash Logback Encoder | 7.4 | Logs estruturados em JSON |
| Bean Validation (Jakarta) | (via Boot) | Validação declarativa de inputs |
| RestTemplate | (via Boot) | Cliente HTTP para comunicação com AuthApi |

---

*README gerado com base na análise completa do código-fonte do projeto CarroAPI-Challenge. Todas as explicações, exemplos e observações são baseados no código real do projeto.*
