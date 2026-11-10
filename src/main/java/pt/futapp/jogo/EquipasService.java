package pt.futapp.jogo;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pt.futapp.audit.Acao;
import pt.futapp.audit.AuditEvent;
import pt.futapp.comum.Nomes;
import pt.futapp.comum.OperacaoInvalidaException;
import pt.futapp.utilizador.Caracterizacao;
import pt.futapp.utilizador.Posicao;
import pt.futapp.utilizador.Utilizador;
import pt.futapp.utilizador.UtilizadorRepository;
import pt.futapp.web.Formatos;

/**
 * Fase 2 (Equipas): gerar equipas equilibradas, ajustar à mão e publicar.
 * As equipas ficam guardadas na própria inscrição ({@link Inscricao#getEquipa()}).
 * Os jogadores só as veem depois de publicadas; o Organizador e o Admin podem prepará-las antes.
 */
@Service
public class EquipasService {

    private final JogoRepository jogos;
    private final InscricaoRepository inscricoes;
    private final UtilizadorRepository utilizadores;
    private final ApplicationEventPublisher eventos;
    private final Formatos fmt;
    private final Random random = new Random();

    public EquipasService(JogoRepository jogos, InscricaoRepository inscricoes, UtilizadorRepository utilizadores,
                          ApplicationEventPublisher eventos, Formatos fmt) {
        this.jogos = jogos;
        this.inscricoes = inscricoes;
        this.utilizadores = utilizadores;
        this.eventos = eventos;
        this.fmt = fmt;
    }

    @Transactional(readOnly = true)
    public EquipasView vista(Long jogoId, String username, String urlBase) {
        Jogo jogo = carregarJogo(jogoId);
        Utilizador eu = carregarUtilizador(username);
        List<Inscricao> jogam = jogam(jogo);

        List<EquipasView.JogadorEquipa> coletes = new ArrayList<>();
        List<EquipasView.JogadorEquipa> sem = new ArrayList<>();
        List<EquipasView.JogadorEquipa> porAtribuir = new ArrayList<>();
        Map<Long, Inscricao> porId = new LinkedHashMap<>();
        for (Inscricao ins : jogam) {
            porId.put(ins.getId(), ins);
            EquipasView.JogadorEquipa j = jogador(ins);
            if (ins.getEquipa() == Equipa.COLETES) {
                coletes.add(j);
            } else if (ins.getEquipa() == Equipa.SEM_COLETES) {
                sem.add(j);
            } else {
                porAtribuir.add(j);
            }
        }
        boolean existem = !coletes.isEmpty() || !sem.isEmpty();
        boolean gestor = eu.ehGestor() && !eu.isBloqueado();

        String motivo = null;
        if (jogo.convocatoriaAberta()) {
            motivo = "Fecha primeiro a convocatória para publicares as equipas.";
        } else if (!jogo.permiteEquipas()) {
            motivo = "Já não é possível mudar as equipas deste jogo.";
        } else if (coletes.isEmpty() || sem.isEmpty()) {
            motivo = "Cada equipa precisa de pelo menos um jogador.";
        } else if (!porAtribuir.isEmpty()) {
            motivo = "Há jogadores sem equipa.";
        }

        EquipasView.EquipaView vColetes = equipa(Equipa.COLETES, coletes);
        EquipasView.EquipaView vSem = equipa(Equipa.SEM_COLETES, sem);
        String whatsapp = existem ? whatsapp(jogo, vColetes, vSem, urlBase + "/jogos/" + jogo.getId()) : null;
        return new EquipasView(jogo.getId(), fmt.dia(jogo.getDataHora()), fmt.hora(jogo.getDataHora()), jogo.getEstado(),
            jogo.equipasPublicadas(), gestor, gestor && jogo.permiteEquipas(), existem, vColetes, vSem, porAtribuir,
            gestor && motivo == null, motivo, whatsapp);
    }

    /** Gera (ou volta a gerar) equipas equilibradas com todos os inscritos. */
    @Transactional
    public void gerar(Long jogoId, String username) {
        Utilizador ator = exigirGestor(username);
        Jogo jogo = carregarJogo(jogoId);
        exigirPermiteEquipas(jogo);
        List<Inscricao> jogam = jogam(jogo);
        if (jogam.size() < 2) {
            throw new OperacaoInvalidaException("São precisos pelo menos 2 inscritos para fazer equipas.");
        }
        List<GeradorEquipas.Jogador> entrada = jogam.stream().map(EquipasService::paraGerador).toList();
        Map<Long, Equipa> resultado = GeradorEquipas.gerar(entrada, random);
        for (Inscricao ins : jogam) {
            ins.definirEquipa(resultado.get(ins.getId()));
        }
        long nColetes = resultado.values().stream().filter(e -> e == Equipa.COLETES).count();
        eventos.publishEvent(new AuditEvent(ator.getUsername(), Acao.GERACAO_EQUIPAS, jogo.getId(), "JOGO", jogo.getId(),
            "Equipas geradas: " + nColetes + " contra " + (jogam.size() - nColetes)));
    }

    /** Põe um jogador numa equipa; sem destino, passa-o para a outra equipa. */
    @Transactional
    public void mover(Long jogoId, Long inscricaoId, Equipa destino, String username) {
        Utilizador ator = exigirGestor(username);
        Jogo jogo = carregarJogo(jogoId);
        exigirPermiteEquipas(jogo);
        Inscricao ins = jogam(jogo).stream().filter(i -> i.getId().equals(inscricaoId)).findFirst()
            .orElseThrow(() -> new OperacaoInvalidaException("Esse jogador não está entre os inscritos."));
        Equipa nova = destino != null ? destino : (ins.getEquipa() == null ? Equipa.COLETES : ins.getEquipa().outra());
        if (nova == ins.getEquipa()) {
            return;
        }
        ins.definirEquipa(nova);
        eventos.publishEvent(new AuditEvent(ator.getUsername(), Acao.MUDANCA_EQUIPA, jogo.getId(), "INSCRICAO", ins.getId(),
            ins.getNomeVisivel() + " passou para " + nova.getDescricao()));
    }

    /** Publica as equipas: os jogadores passam a vê-las. Pode voltar a publicar depois de ajustes. */
    @Transactional
    public void publicar(Long jogoId, String username, String urlBase) {
        Utilizador ator = exigirGestor(username);
        Jogo jogo = carregarJogo(jogoId);
        EquipasView v = vista(jogoId, username, urlBase);
        if (!v.podePublicar()) {
            throw new OperacaoInvalidaException(v.motivoNaoPublicar());
        }
        boolean primeira = !jogo.equipasPublicadas();
        jogo.publicarEquipas();
        eventos.publishEvent(new AuditEvent(ator.getUsername(), Acao.PUBLICACAO_EQUIPAS, jogo.getId(), "JOGO", jogo.getId(),
            (primeira ? "Equipas publicadas: " : "Equipas atualizadas: ")
                + v.coletes().tamanho() + " contra " + v.semColetes().tamanho()));
    }

    // ---------------------------------------------------------------- apoio

    /** Quem joga: as primeiras {@code vagas} inscrições ativas (a lista de espera fica de fora). */
    private List<Inscricao> jogam(Jogo jogo) {
        List<Inscricao> ativas = inscricoes.findByJogoAndEstadoOrderByInscritoEmAscIdAsc(jogo, EstadoInscricao.ATIVA);
        return ativas.subList(0, Math.min(jogo.getVagas(), ativas.size()));
    }

    private static GeradorEquipas.Jogador paraGerador(Inscricao ins) {
        if (ins.getJogador() == null) {
            return new GeradorEquipas.Jogador(ins.getId(), null, null);
        }
        Caracterizacao c = ins.getJogador().getCaracterizacao();
        return new GeradorEquipas.Jogador(ins.getId(), c.rating(), c.getPosicao());
    }

    private static EquipasView.JogadorEquipa jogador(Inscricao ins) {
        Caracterizacao c = ins.getJogador() != null ? ins.getJogador().getCaracterizacao() : new Caracterizacao();
        return new EquipasView.JogadorEquipa(ins.getId(), ins.getJogador() != null ? ins.getJogador().getId() : null,
            ins.getNomeVisivel(), Nomes.iniciais(ins.getNomeVisivel()),
            c.getPosicao() != null ? c.getPosicao().getSigla() : null, c.rating(), ins.isConvidado());
    }

    private static EquipasView.EquipaView equipa(Equipa equipa, List<EquipasView.JogadorEquipa> jogadores) {
        var ratings = jogadores.stream().map(EquipasView.JogadorEquipa::rating).filter(r -> r != null).toList();
        Integer media = ratings.isEmpty() ? null
            : (int) Math.round(ratings.stream().mapToInt(Integer::intValue).average().orElse(0));
        Map<String, Long> contagem = jogadores.stream().filter(j -> j.posicaoSigla() != null)
            .collect(Collectors.groupingBy(EquipasView.JogadorEquipa::posicaoSigla, LinkedHashMap::new, Collectors.counting()));
        String posicoes = java.util.Arrays.stream(Posicao.values())
            .filter(p -> contagem.containsKey(p.getSigla()))
            .map(p -> contagem.get(p.getSigla()) + " " + p.getSigla())
            .collect(Collectors.joining(" · "));
        return new EquipasView.EquipaView(equipa, equipa.getDescricao(), jogadores, media, posicoes);
    }

    private String whatsapp(Jogo jogo, EquipasView.EquipaView coletes, EquipasView.EquipaView sem, String link) {
        StringBuilder sb = new StringBuilder();
        sb.append("Equipas — ").append(fmt.dia(jogo.getDataHora())).append(", ").append(fmt.hora(jogo.getDataHora())).append('\n');
        for (EquipasView.EquipaView e : List.of(coletes, sem)) {
            sb.append('\n').append(e.nome()).append(" (").append(e.tamanho()).append("):\n");
            for (EquipasView.JogadorEquipa j : e.jogadores()) {
                sb.append("- ").append(j.nome()).append('\n');
            }
        }
        sb.append('\n').append(link);
        return "https://wa.me/?text=" + URLEncoder.encode(sb.toString(), StandardCharsets.UTF_8).replace("+", "%20");
    }

    private void exigirPermiteEquipas(Jogo jogo) {
        if (!jogo.permiteEquipas()) {
            throw new OperacaoInvalidaException("Já não é possível mudar as equipas deste jogo.");
        }
    }

    private Utilizador exigirGestor(String username) {
        Utilizador u = carregarUtilizador(username);
        if (!u.ehGestor() || u.isBloqueado()) {
            throw new OperacaoInvalidaException("Só o Organizador ou o Admin podem fazer as equipas.");
        }
        return u;
    }

    private Jogo carregarJogo(Long id) {
        return jogos.findById(id).orElseThrow(() -> new OperacaoInvalidaException("Jogo não encontrado."));
    }

    private Utilizador carregarUtilizador(String username) {
        return utilizadores.findByUsernameIgnoreCase(username)
            .orElseThrow(() -> new OperacaoInvalidaException("Utilizador desconhecido."));
    }
}
