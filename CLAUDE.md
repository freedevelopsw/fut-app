# FutApp — instruções para o Claude

App para organizar os jogos de futebol de um grupo amador de amigos (grupo de WhatsApp).
O dono do projeto não escreve código: todo o código é gerado, testado automaticamente e validado usando a app.

## Fonte de verdade
- Especificação funcional: https://claude.ai/artifact/2xb6UxpZTjYgadaUPGXSx4
- Mockups mobile (aprovados): https://claude.ai/artifact/V6XnWKMDouhEinJTDXu3mu
- Quando uma regra muda, muda primeiro na especificação e só depois no código.

## Stack
- Java 17, Spring Boot 4.0.x, Maven (uma só aplicação, um só deploy).
- Frontend: Thymeleaf + htmx (`hx-boost` no `<body>`) + Bootstrap 5 por CDN. Sem build de frontend, sem SPA.
- Spring Security: login com utilizador e password, sessão persistente (remember-me, `alwaysRemember`).
- JPA/Hibernate com `ddl-auto=validate`: o esquema é versionado com Liquibase (ver "Base de dados").
  H2 em ficheiro no perfil `dev`; PostgreSQL no perfil `prod`; H2 em memória nos testes (também com o Liquibase).

## Ambiente local (Windows)
- JDK 17: `D:\projects\env\jdk-17.0.12`
- Maven: `D:\projects\env\apache-maven-3.9.9`
- Build e testes: `mvn clean verify`
- Arrancar: `mvn spring-boot:run` e abrir http://localhost:8080. Em dev entra-se com `admin` / `admin123`.
- Em produção, definir `FUTAPP_ADMIN_PASSWORD`, `FUTAPP_REMEMBER_KEY` e `FUTAPP_CONVITE_KEY` (textos longos e aleatórios).

## Convenções
- Código e nomes de domínio em português (Jogo, Inscricao, Utilizador, Convocatoria). Texto da interface em português de Portugal.
- Pacotes por funcionalidade: `utilizador`, `jogo`, `audit`, `web`, `config`, `comum`.
- Regras de negócio nos serviços (`*Service`), nunca nos controllers nem nos templates. Os serviços validam também as permissões.
- Os templates recebem objetos de vista (records), não entidades JPA com relações lazy.
- Cada ação relevante publica um `AuditEvent`; é gravado depois do commit por `AuditListener`.
- Dados do negócio (inscrições, desistências, golos, votos) nunca dependem do audit, que é apagado ao fim de `futapp.audit.retencao-dias`.
- Mobile-first: cada ecrã é desenhado para 360–430 px; botões de ação grandes (≥ 44 px) na parte de baixo.
- Ajuda (`/ajuda`, a partir de Perfil → Sobre): versão da app e explicação curta de cada parte, em texto fixo no
  template `ajuda.html`. Quando uma regra visível muda (vagas, golos, votação, roles…), atualizar também esse texto.
- Indicador de carregamento global (`static/js/carregando.js`, incluído no `head` do layout): uma bola a saltar ao
  centro do ecrã em todos os pedidos htmx com mais de 250 ms, até à resposta. O elemento que fez o pedido fica
  bloqueado (`.htmx-request`), o que evita cliques duplos. Páginas novas não precisam de nada: basta usarem o `head` do layout.
- Cache dos ficheiros estáticos (`static/`): cadeia de recursos do Spring com impressão digital do conteúdo
  (`/css/app-<md5>.css`) e `Cache-Control: max-age=7 dias, public`; cada deploy muda os endereços dos ficheiros alterados, por isso nunca se vê uma versão antiga; as páginas continuam sem cache (`no-store`).
  Nos templates, ligar sempre com `@{/css/...}` (nunca `href="/css/..."` à mão), para o endereço mudar quando o
  ficheiro muda. O `manifest.webmanifest` aponta para `/img/icone.svg` sem impressão: se o ícone mudar, mudar o
  nome do ficheiro. Em `dev` a cache está desligada. Endereços com impressão no `SecurityConfig` (`/manifest*.webmanifest`).
  Teste: `CacheEstaticosTest` (servidor a sério, porque é o filtro do servidor que reescreve os endereços).
- Toda a regra nova vem com testes em `src/test/java` (`@SpringBootTest` + perfil `test`).
- Enums em entidades: nunca `@Enumerated`; usar `@Convert(converter = XConverter.class)` (`comum/conversores`,
  `EnumTextoConverter`). Na base de dados são VARCHAR sem restrições CHECK: um valor novo de enum não precisa de changeset.

## Base de dados (Liquibase)
- `src/main/resources/db/changelog/db.changelog-master.yaml` lista os ficheiros de `alteracoes/`, por ordem.
  Corre no arranque, antes do Hibernate; o histórico fica na tabela `DATABASECHANGELOG`.
- Qualquer mudança ao modelo (entidade nova, coluna nova, coluna que muda) = um ficheiro NOVO em `alteracoes/`
  (`NNN-aaaa-mm-dd-descricao.yaml`), acrescentado no fim do master, ao mesmo tempo que a mudança na entidade.
  Nunca editar um changeSet já aplicado (o Liquibase guarda o checksum e a app deixa de arrancar).
- Tipos portáveis (BIGINT, INT, VARCHAR(n), BOOLEAN, TIMESTAMP): tem de funcionar em H2 e em PostgreSQL.
  Colunas NOT NULL novas levam valor por omissão (as linhas existentes ficam com ele).
- Se o Hibernate já pode ter criado a coluna nalguma base de dados, usar pré-condição `columnExists` + `MARK_RAN`.
- `001-esquema-inicial`: o esquema de produção a 17/11/2026 (commit 52d483a); nas bases existentes fica `MARK_RAN`.
  `002-enums-como-texto` (`config/migracoes/EnumsComoTexto`): apaga as restrições CHECK que o Hibernate criava nos
  enums e converte colunas ENUM antigas do H2. `003-…`: resistência, removido, local opcional.
- `MigracoesTest`: base nova, base antiga criada pelo Hibernate (`src/test/resources/db/esquema-hibernate-2026-11-17-h2.sql`,
  com dados) e repetição sem efeito. A migração foi também testada à mão em PostgreSQL 16 com o esquema do Neon.

## Registo
- O Admin é criado no arranque (`AdminInicial`) se não existir nenhum: `futapp.admin.username|nome|password`.
  Sem password configurada é gerada uma aleatória e escrita no log.
- Não há registo livre nem links/menus para registo: só por link de convite gerado pelo Admin (`/admin/convites`).
- Link de convite: token AES-256-GCM com `v1|expiraEpoch|admin`, nada guardado na base de dados, reutilizável até expirar.
  Validade máxima `futapp.convite.validade-max-dias` (7 por omissão). Mudar `futapp.convite.chave` invalida todos os links.

## Bloqueio
- O Admin bloqueia/desbloqueia membros em `/admin/membros`. Bloqueado = login recusado (`disabled`), sessão terminada
  no pedido seguinte (`ContaBloqueadaFilter`), sai das convocatórias abertas. O histórico mantém-se.
- Ninguém se bloqueia a si próprio; um Admin bloqueado perde os poderes.

## Remover membros (`RemocaoService`) — sem volta atrás
- Só o Admin, em `/admin/membros` (ícone do caixote, ativos e bloqueados); ninguém se remove a si próprio.
- Primeiro sai dos jogos ainda por jogar (lista editável: convocatória aberta/fechada, equipas publicadas;
  `JogoService.retirarDosJogosPorJogar`): desistência registada, a espera sobe, e essas inscrições são apagadas.
  Assim não fica um "jogador fantasma" a ocupar uma vaga.
- Sem mais nada ligado à pessoa (inscrições noutros jogos, votos dados/anulados, golos registados, jogos criados/
  cancelados, inscrições que cancelou) → a conta é apagada de vez.
- Senão, soft delete (`Utilizador.marcarRemovido`): bloqueada para sempre, username `removido-{id}` (ou
  `removido-{id}-2`… se já existir), desaparece de Membros, da lista de jogadores e da carta.
- As inscrições continuam ligadas à conta. `Inscricao.isConvidado()` é true para convidados E para contas removidas
  (mostradas como convidado, sem link para a carta: `Inscricao.getConta()` devolve null). As estatísticas agregam por
  conta, por isso um removido nunca se junta a um convidado ou a outro removido com o mesmo nome.
- Uma conta removida não volta, num só ponto: `UtilizadorRepository.findMembro(id)` (usar sempre, nunca `findById`,
  para mostrar ou agir sobre alguém) e o próprio `Utilizador` recusa desbloquear, `definirRoles`, mudar dados ou
  password (`exigirNaoRemovido`). O login recusa (`disabled`).
- O prefixo `removido-` está reservado: o registo recusa usernames começados por ele.
- Nas votações abertas dos jogos em que jogou, se todos os outros já votaram, a votação fecha
  (`VotacaoService.fecharSeTodosVotaram`, também chamado ao bloquear).
- `UtilizadorService.estaBloqueado` devolve true para um username que já não existe: a sessão aberta termina.
- Auditoria: `REMOCAO_UTILIZADOR`.

## Password
- Cada utilizador muda a própria password em `/perfil/password` (confirma a atual). Depois a sessão termina
  (`Sessoes.terminar`) e entra-se com a nova; os cookies remember-me dos outros dispositivos deixam de valer.
- Password esquecida: o Admin gera na carta do jogador um link (`POST /jogadores/{id}/link-password`, só ADMIN) e
  envia-o (copiar/WhatsApp). A página pública `/redefinir-password?token=` pede a nova password duas vezes.
  Token AES-256-GCM (`CifraToken`, contexto próprio: um convite não serve aqui) com
  `v1|userId|expiraEpoch|impressão do hash|admin`; nada guardado na base de dados. Uso único: quando a password
  muda, a impressão deixa de corresponder. Validade `futapp.password.link-validade-horas` (24). Não para o próprio
  Admin nem para contas bloqueadas. Auditoria: `GERACAO_LINK_PASSWORD`, `REDEFINICAO_PASSWORD`.

## Carta do jogador
- `Utilizador.alcunha` + `Caracterizacao` (@Embeddable): posição, posição secundária, pé, 6 atributos 1–10
  (velocidade, técnica, remate, passe, defesa, resistência). Só o nome é obrigatório.
- Rating = média arredondada dos atributos preenchidos. Nas listas usa-se `getNomeVisivel()` (alcunha ou nome).
- `/jogadores/{id}`: carta visível a todos; `/jogadores/{id}/editar`: o próprio ou o Admin (`JogadorService`).
- Lista de jogadores ativos no ecrã Estatísticas; nomes nas convocatórias e nos membros ligam à carta.

## Avaliação das cartas pelos colegas (pacote `avaliacao`, 23/11/2026)
- A carta tem duas avaliações: autoavaliação (a de sempre, `Caracterizacao`) e média dos colegas. Separadores
  Eu · Colegas em `/jogadores/{id}?vista=eu|colegas` (omissão: eu). Sem separador "Jogos" (decisão do dono, 23/11;
  a avaliação pelos jogos fica só como ideia na especificação).
- `AvaliacaoColega` (tabela `avaliacao_colega`, changeset `004`): uma por par avaliador/avaliado, as mesmas 6 notas
  1–10, todas opcionais. Voltar a avaliar substitui; tudo em branco apaga. Ninguém se avalia a si próprio nem avalia
  um bloqueado. `/jogadores/{id}/avaliar` (GET/POST). Auditoria: `AVALIACAO_COLEGA` (sem as notas).
- `MediaColegas`: cada atributo = média das notas dadas a esse atributo; rating = média dos atributos com nota;
  uma casa decimal ("7,3"). Aparece logo ao primeiro voto e mostra-se sempre só quantos votaram (nunca "X de Y").
- Ecrã Estatísticas → Cartas: tabela com Auto e Colegas, ordenada por Auto (omissão) ou Colegas tocando no cabeçalho
  (`OrdemCartas`, `/estatisticas?cartas=auto|colegas`, troca só a secção com htmx, como V/G).
- Quem avaliou e como (`/jogadores/{id}/avaliacoes`): o Admin vê sempre; os outros só com o interruptor ligado.
- TEMPORÁRIO: `InterruptorDetalhesAvaliacoes` (um só interruptor do Admin para todas as cartas, no topo das Cartas,
  guardado na tabela `configuracao`, chave `avaliacoes.detalhes-visiveis`). Depois de ligado, numa versão futura sai:
  apagar a classe, o endpoint `/admin/avaliacoes/visibilidade`, o bloco `id="interruptor-avaliacoes"` em
  `estatisticas.html` e pôr `AvaliacaoService.podeVerDetalhes` a devolver true. Auditoria: `ALTERACAO_VISIBILIDADE_AVALIACOES`.
- As equipas (`GeradorEquipas`) continuam a usar a autoavaliação.
- Remover um membro apaga as avaliações que deu e recebeu (`RemocaoService`); não contam como histórico.

## Equipas (Fase 2)
- A equipa fica na própria inscrição (`Inscricao.equipa`: COLETES / SEM_COLETES); jogam as primeiras `vagas` inscrições ativas.
- `GeradorEquipas`: GR separados, distribuição gulosa por rating, trocas que reduzem a diferença de rating e de posições;
  sem rating conta 5; aleatoriedade leve para "Gerar de novo".
- `/jogos/{id}/equipas` (Organizador/Admin): gerar, tocar para mudar de equipa, publicar (só com a convocatória fechada).
  Publicar muda o estado para EQUIPAS_PUBLICADAS; depois disso os ajustes ficam logo visíveis.
- Quem sobe da lista de espera fica "sem equipa" e impede publicar até ser atribuído.
- Não usar `eq`, `ne`, `gt`, `lt`, `ge`, `le`, `and`, `or`, `not` como nomes de variáveis no Thymeleaf (são operadores).

## Registo do jogo (Fase 3)
- `Golo` (equipa, marcador e assistência = inscrições, ambos opcionais) e `Inscricao.faltou`. `RegistoService`.
- `/jogos/{id}/registo`: qualquer membro marca golos ("+ Golo" por equipa → marcador, assistência opcional) e apaga
  qualquer golo (`POST /jogos/{id}/registo/golos[/{g}/apagar]`); na convocatória aparece o botão "Marcar golos" a quem
  não é Organizador/Admin. Só o Organizador/Admin (`/gestao/registo/**`): "Introduzir só o resultado", faltas, fechar
  e reabrir. `RegistoView.podeMarcarGolos` (membro) vs `podeRegistar` (gestor).
- Só com as equipas publicadas. A primeira ação passa o jogo para EM_REGISTO: a partir daí equipas e lista ficam fixas.
- Fechar o jogo → VOTACAO_ABERTA (não exige golos). Enquanto a votação está aberta, pode-se reabrir o registo.
- O resultado (placar + marcadores) aparece na convocatória de todos a partir de EM_REGISTO.

## Votação (Fase 4) — pacote `votacao`
- `Premio` (prémios brincadeira; 10 criados no 1.º arranque por `PremiosIniciais`); `/gestao/premios` para Organizador e Admin:
  criar, editar, ativar/desativar (não se apagam). O MVP é fixo (`Voto.premio` null).
- No boletim aparece o nome verdadeiro e a alcunha: na lista do MVP, "Nome (alcunha)" numa linha; se a alcunha não
  couber, fica escondida (`.nome-voto` no CSS). Nos dropdowns e na página de votos do Admin, `Nomes.nomeComAlcunha`
  ("Nome (alcunha)" até 30 caracteres, senão só o nome).
- `Voto` (eleitor = utilizador, votado = inscrição, `anulado`). Votam os presentes com conta (não convidados, não faltas);
  ninguém vota em si. Boletim `/jogos/{id}/votar`: MVP obrigatório (radio), prémios opcionais (dropdowns); voltar a votar substitui.
- Fecha sozinha quando todos votaram, ou pelo Organizador/Admin; pode ser reaberta. Resultados só com o jogo FECHADO.
- Empate MVP: mais golos no jogo; se continuar, partilhado. Outros prémios: empate = partilhado.
- Admin: `/jogos/{id}/votos` vê quem votou em quem e anula votos.

## Fecho do jogo e estatísticas (Fase 5) — pacote `estatisticas`
- Jogo FECHADO: a página do jogo mostra MVP e prémios, resultado, linha de presenças e "Partilhar resumo no WhatsApp"
  (`ResumoService`); a gestão e as vagas deixam de aparecer. Os jogos anteriores mostram o resultado no início.
- `EstatisticasService` calcula tudo a partir dos jogos FECHADO (nada guardado à parte): jogos, V/E/D, golos,
  assistências, MVPs, prémios, faltas. Convidados contam (agregados pelo nome, sem carta); bloqueados contam. Rankings com no máximo 5 nomes.
- Ecrã Estatísticas: três secções colapsáveis (`<details class="colapsavel">`, sem JavaScript), todas fechadas ao
  entrar: Rankings, Todos os jogadores, Cartas dos jogadores. A carta mostra os números do jogador.
  Ordenação (pedido do dono, 19/11/2026), sempre com os convidados (e removidos) no fundo:
  - Rankings: do valor maior para o menor; em empate, membros antes de convidados, depois o nome. Um convidado com
    mais do que um membro fica à frente dele (não se troca a ordem dos valores).
  - Todos os jogadores: por vitórias (omissão) ou por golos (`OrdemTabela`, `/estatisticas?ordem=golos|vitorias`);
    o outro critério desempata, depois jogos e nome. Convidados sempre no fim da tabela. Sem coluna de faltas
    (as faltas continuam no ranking "Mais faltas" e na carta). Ordena-se tocando nos cabeçalhos V e G (links, com seta no ativo); trocam só a secção da tabela
    (htmx `hx-select`/`hx-target="#todos-jogadores"`), por isso as outras secções ficam abertas/fechadas como estavam;
    com `ordem` no endereço a tabela vem aberta.
  - Cartas dos jogadores: pela autoavaliação (omissão) ou pela média dos colegas (ver "Avaliação das cartas").

## Lista de jogos e histórico
- Ecrã Jogos: os próximos aparecem sempre (todos os jogos ainda não fechados nem cancelados, mesmo do mês seguinte);
  os anteriores só os do mês atual (fechados/cancelados com data a partir do dia 1; inclui cancelados com data futura).
- `/jogos/historico` (Organizador/Admin): jogos fechados e cancelados do ano corrente (de 1 de janeiro a 31 de dezembro;
  um cancelado do ano seguinte não entra), agrupados por mês. Botão "Histórico de jogos" no ecrã Jogos só para eles.
  Qualquer jogo continua a abrir pelo link.
- As listas de jogos usam `JogoService.resumos`: contagens de inscritos e golos em duas consultas para todos os jogos
  (nunca uma por jogo). `Jogo.criadoPor`/`canceladoPor` são LAZY.

## Convocatória
- Mensagem do WhatsApp (`MensagemWhatsApp`, modelo do dono de 22/11/2026): inscritos numerados até às vagas, com as
  vagas livres como linhas vazias ("9."); "Lista de espera:" só quando existe, numerada a partir de 1 (" 1. Nome");
  no fim o link de inscrição. Depois de inscrever, cancelar ou mudar a lista aparece o cartão "Avisa o grupo" com o
  botão "Avisar no grupo" (abre o WhatsApp com a mensagem; a pessoa escolhe o grupo). A app não publica sozinha:
  a API oficial de grupos do WhatsApp exige conta empresarial oficial e no máximo 8 participantes.
- O local é opcional. A regra vive só em `Jogo.getLocal()`: devolve o local sem espaços à volta, ou null quando está
  vazio ou só com espaços (também em linhas antigas). Vistas e WhatsApp usam esse valor: null = não mostrar
  (changeset `003-jogo-local-opcional`).

## Jogo cancelado
- Estado CANCELADO (Organizador/Admin, qualquer estado exceto FECHADO), com motivo obrigatório
  (`JogoService.MOTIVO_CANCELAMENTO_OMISSAO` = "Falta de jogadores"), quem e quando.
- Sem volta atrás: um jogo cancelado não se reativa (a reativação foi retirada; o valor de auditoria `REATIVACAO_JOGO`
  fica só por causa dos registos antigos). Cancelado sai dos próximos (aparece nos anteriores) e não conta nas
  estatísticas.
- Mensagem de cancelamento para o WhatsApp na página do jogo.

## Roles
Cumulativas: `JOGADOR`, `ORGANIZADOR`, `ADMIN`. Convidado = inscrição sem conta (`Inscricao.nomeConvidado`).
- Mudam-se na carta do jogador, num dropdown de nível de acesso (`NivelAcesso`: Jogador, Organizador, Admin;
  cumulativos) via `POST /jogadores/{id}/nivel`, não na lista de Membros.
- Admin escolhe qualquer nível; Organizador só Jogador/Organizador e não mexe em Admins; Jogador não muda nada
  (`UtilizadorService.opcoesNivel`). O Admin não muda o próprio nível.

## Publicar uma versão (produção)
- A app está em produção com dados reais. Um push para `main` faz deploy automático (Cloud Build → Cloud Run).
- Versão semântica no `pom.xml` (MAJOR.MINOR.PATCH, sem `-SNAPSHOT`): PATCH = correções, MINOR = funcionalidades,
  MAJOR = mudanças grandes. Aparece no Perfil ("FutApp · versão x.y.z") e no registo do arranque ("FutApp versão x.y.z"),
  lida do `build-info` do Maven (`web/Versao`). Primeira versão: 1.0.0 (inclui o Liquibase).
- Passos de cada publicação:
  1. `mvn clean verify` a passar e funcionalidades testadas localmente pelo dono.
  2. Mudar a versão no `pom.xml`.
  3. Cópia de segurança antes do push: no Neon, Branches → Create branch a partir da principal, com o nome `antes-x.y.z`.
  4. Commit e push para `main` → deploy.
  5. Nos registos do Cloud Run: as linhas do Liquibase (`Running Changeset` / `Marking ChangeSet … as ran`),
     `FutApp versão x.y.z` e `Started FutappApplication`. Se a revisão nova não arrancar (por exemplo, o Hibernate
     `validate` falha), o Cloud Run continua a servir a revisão anterior.
  6. Abrir a app e confirmar a versão no Perfil.
  7. Criar a tag `version/x.y.z` nesse commit e enviá-la para o GitHub.
- Os changeSets têm de ser compatíveis com a versão anterior, que continua a correr durante o deploy: colunas novas
  opcionais ou com valor por omissão; não apagar nem renomear colunas na mesma versão em que o código deixa de as usar.

## Publicação (Google Cloud Run + Neon)
- `Dockerfile` multi-stage (Maven + Temurin 17 JRE), `SPRING_PROFILES_ACTIVE=prod`, `JAVA_TOOL_OPTIONS` com
  `MaxRAMPercentage=75`, SerialGC e `TieredStopAtLevel=1` (arranque rápido quando a app acorda).
- `application-prod.properties`: porta `${PORT:8080}`, `forward-headers-strategy=framework` (links em https atrás do
  proxy), cookies de sessão `secure`, Hikari com no máximo 3 ligações e `minimum-idle=0` (o Neon pode adormecer).
- Produção é sempre PostgreSQL (Neon) e nada em produção tem ligação ao H2 (decisão do dono, 19/11/2026):
  `DATABASE_URL` é obrigatória e sem valor por omissão (sem ela a app não arranca e o Cloud Run mantém a revisão
  anterior; `BaseDadosProducao` dá a mensagem clara e exige `jdbc:postgresql:`); o H2 tem `scope test` no pom, por isso não vai no jar de produção. O `mvn spring-boot:run` local usa o
  classpath de teste (`useTestClasspath`) para ter o H2 em desenvolvimento.
- `UrlBaseDados` (chamado no `main`): aceita `DATABASE_URL` no formato do Neon (`postgresql://user:pass@host/db?...`)
  e converte para `spring.datasource.*`; um URL `jdbc:` fica como está.
- Cloud Run: europe-west1, faturação por pedido, 1 GiB, 1 CPU, min 0 / max 1 instâncias, startup CPU boost,
  acesso público, deploy contínuo a partir do GitHub (ramo main) com o Dockerfile.
- Variáveis: `FUTAPP_ADMIN_PASSWORD`, `FUTAPP_REMEMBER_KEY`, `FUTAPP_CONVITE_KEY` e, para PostgreSQL, `DATABASE_URL`.
- Com a instância desligada, o job das 4h não corre; a limpeza da auditoria corre também no arranque.

## Estado
- Fatia 1 (M0 + M1): admin no arranque, registo só por convite, bloqueio de membros, mudar password, carta do jogador, equipas (Fase 2), registo do jogo (Fase 3), votação (Fase 4), fecho do jogo e estatísticas (Fase 5), login, roles, criar jogo, convocatória (12 vagas, espera a partir do 13º, cancelamentos registados, fecho manual), partilha WhatsApp, auditoria com limpeza.
- Pendente: convidados passarem a utilizadores (ligar o histórico do convidado à nova conta).
- Jogo cancelado e convidados nas estatísticas.
- Redefinição de password por link gerado pelo Admin.
- Próxima milestone (ver a especificação): jogos por temporada (Admin, dropdown), temporadas, convidados → utilizadores,
  imagem do resumo, revogar convites, publicar num alojamento, M4, M5.
