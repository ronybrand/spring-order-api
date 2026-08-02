# Instruções para agentes de código neste repositório

Antes de editar qualquer arquivo em `src/main/java` ou `src/test/java`, invoque a skill
`spring-feature` (`.claude/skills/spring-feature/SKILL.md`), que contém as convenções completas
de desenvolvimento deste projeto.

A skill é a fonte de referência para arquitetura, implementação, testes, segurança e persistência.
Este arquivo contém apenas o checklist resumido dos pontos que devem ser validados antes de uma
alteração.

A instrução vale tanto para novas features quanto para correções, refatorações e auditorias de
código existente.

## Checklist antes de escrever código

- [ ] Li o domínio de referência mais parecido neste projeto e vou espelhar sua estrutura e convenções, evitando criar um padrão novo sem necessidade justificada.
- [ ] Classifiquei **cada campo novo** (entidade + `RequestDto`) contra: PII (documento de identidade, e-mail, telefone, endereço...), categoria especial/LGPD art. 5º (saúde, biometria, dado racial/religioso/político), PCI (dado de cartão), dado financeiro (conta, salário, score), credenciais/segredos (senha, API key, token). Se o campo for sensível, utilize a infraestrutura existente (`@Sensitive` e mascaramento centralizado), nunca uma solução manual ad hoc. Nenhum campo sensível deve ser adicionado "para o caso de precisar depois" sem necessidade real da feature.
- [ ] Apliquei DRY (*Don't Repeat Yourself*) e eliminei duplicações de código, tanto em produção quanto nos testes (ex. reaproveitando helpers da classe base de teste `AbstractAuthIntegrationTest` e utilitários compartilhados).
- [ ] Avaliei se alguma parte da task se beneficia de processamento assíncrono/mensageria (fila, broker, listener) - ex. efeito colateral que depende de serviço externo lento/instável, ou que não deveria bloquear/reverter a resposta principal - versus resolver com uma chamada síncrona mais simples. Qualquer que seja a escolha, deixei o motivo explícito no plano ou no README, não uma decisão por padrão/hábito.

## TDD (red → green → refactor, não retroativo)

- [ ] Teste escrito e executado **falhando antes** do código de produção correspondente.
- [ ] `*ServiceTest` cobre todos os branches relevantes: caminho feliz, recurso inexistente, conflito de unicidade, regra de negócio violada, limites exatos de validação (`N` e `N+1`) e, quando aplicável, concorrência otimista (`@Version`).
- [ ] Todo domínio exposto via HTTP possui `*ControllerIT`, incluindo pelo menos um cenário de autorização negada (403) para cada endpoint protegido.
- [ ] Rate limiting, CORS ou qualquer outro guard de produção não foram alterados para facilitar testes; diferenças de configuração pertencem exclusivamente ao profile de teste.

## Entidade / persistência

- [ ] `UUID id` gerado na aplicação (`GenerationType.UUID`), nunca pelo banco.
- [ ] Soft delete (`deletedAt`/`deletedBy`) como padrão; serviços de produção nunca executam hard delete nem operações de exclusão em massa.
- [ ] `@Version` utilizado em entidades sujeitas a concorrência real; não é obrigatório em entidades de referência sem atualizações concorrentes relevantes.
- [ ] Toda validação de unicidade feita no service possui constraint equivalente no banco.
- [ ] Todo `BigDecimal` novo é construído a partir de `String`/`BigDecimal.valueOf(double)`, nunca do construtor de `double` puro; toda divisão de `BigDecimal` especifica escala e `RoundingMode` explícitos.
- [ ] `@Query` nativa: utilizar parâmetros nomeados (`:id`), nunca concatenação de strings; aplicar manualmente `AND deleted_at IS NULL`, pois `@SQLRestriction` não é aplicado a queries nativas.
- [ ] Listagens/paginação envolvendo coleções evitam N+1 através de estratégia explícita (`@BatchSize`, `EntityGraph` ou equivalente).
- [ ] `@ToString(exclude = {...})` em associações lazy; nunca utilizar `@Data` em entidades JPA; entidades com dados sensíveis delegam a representação textual para a infraestrutura centralizada de mascaramento.

## Service / controller / autorização

- [ ] Todo método de controller possui `@PreAuthorize` explícito; não existe acesso protegido implícito nesta aplicação.
- [ ] Recursos pertencentes a um usuário específico validam ownership na camada de serviço após o carregamento da entidade e seguem a estratégia de resposta definida pela arquitetura do projeto.
- [ ] Novos `ErrorCode` seguem a convenção de nomenclatura e numeração existente; nunca reutilizar um código para um significado diferente.
- [ ] `import` sempre no topo do arquivo; evitar nomes totalmente qualificados no corpo do código, salvo conflito entre classes homônimas.
- [ ] Chamadas intra-classe a métodos anotados com `@Transactional`, `@PreAuthorize`, `@Cacheable` ou outros aspectos Spring passam pelo bean gerenciado (proxy), nunca por `this`.
- [ ] Busca, filtro e paginação reutilizam a infraestrutura compartilhada do projeto; validações de campo/tipo devem ser resolvidas a partir do metamodel JPA, evitando duplicação manual de metadados por domínio.

## Baseline de segurança (não desativar por acidente)

- [ ] Security headers, rate limiting, limite de tamanho de request, validação de `aud` do JWT e CORS fail-fast em produção permanecem ativos; nenhuma dessas proteções foi desabilitada ou contornada para viabilizar uma implementação ou teste.
- [ ] Swagger/OpenAPI continua desabilitado fora do profile `dev`.
- [ ] Segredos, chaves, tokens e connection strings nunca são commitados em código, `application.yml` versionado ou migrations; utilizar exclusivamente variáveis de ambiente ou secret manager.
- [ ] Dados sensíveis nunca aparecem em `log.info`, `log.warn` ou `log.error` em texto claro (incluindo payloads de erro de validação) e não são expostos em `ResponseDto` sem necessidade real. Campos anotados com `@Sensitive` devem utilizar exclusivamente a infraestrutura centralizada de mascaramento (`SensitiveDataMasker`, `SensitiveFieldsModule`).

## Antes de considerar a mudança pronta

- [ ] `mvn pmd:check` executado sem violações (gate real do build; `mvn verify` falha em caso de infrações).
- [ ] Testes relevantes executados (`mvn test -Dtest=<Classe>`), não apenas compilados.
- [ ] Falhas conhecidas da infraestrutura de testes (Docker/Testcontainers, ambiente local etc.) foram descartadas antes de concluir que existe um defeito na implementação.
- [ ] Toda migration nova foi revisada manualmente. Ferramentas de autogeração definem apenas a estrutura; alterações em dados, índices, constraints e estratégia de rollback foram validadas explicitamente.
- [ ] Nenhuma classe/método/anotação novo utilizado está marcado `@Deprecated` (ou `deprecated since ... for removal`) na versão das dependências do projeto; havendo alternativa não depreciada, ela foi adotada em vez da API antiga.
- [ ] Checagens de nulo/vazio usam o método acessório positivo (`StringUtils.isBlank`/`isNotBlank`, `CollectionUtils.isEmpty`/`isNotEmpty`), nunca a negação do oposto (`!hasText`, `!isNotEmpty`) nem duas condições manuais (`x != null && !x.isEmpty()`). Para regras de negócio sem método pronto, a condição foi extraída num método nomeado afirmativamente (ex. `isCancelable()`) em vez de negação (`!`) repetida nos pontos de uso.

O detalhamento completo de cada item (motivação, exceções válidas, decisões arquiteturais e exemplos) está documentado na skill `spring-feature`. Este arquivo é apenas um checklist operacional utilizado antes de considerar uma tarefa concluída.
