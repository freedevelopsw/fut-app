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
- JPA/Hibernate com `ddl-auto=update`. H2 em ficheiro no perfil `dev`; PostgreSQL no perfil `prod`.

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
- Toda a regra nova vem com testes em `src/test/java` (`@SpringBootTest` + perfil `test`).

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

## Password
- Cada utilizador muda a própria password em `/perfil/password` (confirma a atual). Depois a sessão termina
  (`Sessoes.terminar`) e entra-se com a nova; os cookies remember-me dos outros dispositivos deixam de valer.

## Carta do jogador
- `Utilizador.alcunha` + `Caracterizacao` (@Embeddable): posição, posição secundária, pé, 5 atributos 1–10. Só o nome é obrigatório.
- Rating = média arredondada dos atributos preenchidos. Nas listas usa-se `getNomeVisivel()` (alcunha ou nome).
- `/jogadores/{id}`: carta visível a todos; `/jogadores/{id}/editar`: o próprio ou o Admin (`JogadorService`).
- Lista de jogadores ativos no ecrã Estatísticas; nomes nas convocatórias e nos membros ligam à carta.

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
- `/jogos/{id}/registo` (Organizador/Admin): "+ Golo" por equipa → escolher marcador (assistência opcional);
  apagar golos; "Introduzir só o resultado" cria/retira golos sem marcador; marcar faltas.
- Só com as equipas publicadas. A primeira ação passa o jogo para EM_REGISTO: a partir daí equipas e lista ficam fixas.
- Fechar o jogo → VOTACAO_ABERTA (não exige golos). Enquanto a votação está aberta, pode-se reabrir o registo.
- O resultado (placar + marcadores) aparece na convocatória de todos a partir de EM_REGISTO.

## Votação (Fase 4) — pacote `votacao`
- `Premio` (prémios brincadeira; 10 criados no 1.º arranque por `PremiosIniciais`); `/gestao/premios` para Organizador e Admin:
  criar, editar, ativar/desativar (não se apagam). O MVP é fixo (`Voto.premio` null).
- `Voto` (eleitor = utilizador, votado = inscrição, `anulado`). Votam os presentes com conta (não convidados, não faltas);
  ninguém vota em si. Boletim `/jogos/{id}/votar`: MVP obrigatório (radio), prémios opcionais (dropdowns); voltar a votar substitui.
- Fecha sozinha quando todos votaram, ou pelo Organizador/Admin; pode ser reaberta. Resultados só com o jogo FECHADO.
- Empate MVP: mais golos no jogo; se continuar, partilhado. Outros prémios: empate = partilhado.
- Admin: `/jogos/{id}/votos` vê quem votou em quem e anula votos.

## Roles
Cumulativas: `JOGADOR`, `ORGANIZADOR`, `ADMIN`. Convidado = inscrição sem conta (`Inscricao.nomeConvidado`).
- Mudam-se na carta do jogador, num dropdown de nível de acesso (`NivelAcesso`: Jogador, Organizador, Admin;
  cumulativos) via `POST /jogadores/{id}/nivel`, não na lista de Membros.
- Admin escolhe qualquer nível; Organizador só Jogador/Organizador e não mexe em Admins; Jogador não muda nada
  (`UtilizadorService.opcoesNivel`). O Admin não muda o próprio nível.

## Estado
- Fatia 1 (M0 + M1): admin no arranque, registo só por convite, bloqueio de membros, mudar password, carta do jogador, equipas (Fase 2), registo do jogo (Fase 3), votação (Fase 4), login, roles, criar jogo, convocatória (12 vagas, espera a partir do 13º, cancelamentos registados, fecho manual), partilha WhatsApp, auditoria com limpeza.
- Pendente: convidados passarem a utilizadores (ligar o histórico do convidado à nova conta).
- Seguinte: resumo do jogo (Fase 5) e estatísticas.
