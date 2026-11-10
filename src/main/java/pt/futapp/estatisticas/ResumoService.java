package pt.futapp.estatisticas;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pt.futapp.jogo.Convocatoria;
import pt.futapp.jogo.JogoService;
import pt.futapp.jogo.RegistoService;
import pt.futapp.jogo.RegistoView;
import pt.futapp.votacao.VotacaoService;
import pt.futapp.votacao.VotacaoView;

/** Fase 5: o resumo de um jogo fechado (resultado, MVP, prémios, presenças) e o texto para o WhatsApp. */
@Service
public class ResumoService {

    private final JogoService jogos;
    private final RegistoService registo;
    private final VotacaoService votacao;

    public ResumoService(JogoService jogos, RegistoService registo, VotacaoService votacao) {
        this.jogos = jogos;
        this.registo = registo;
        this.votacao = votacao;
    }

    public record ResumoView(int jogaram, int faltas, int votaram, int desistencias, String whatsappUrl) {
    }

    @Transactional(readOnly = true)
    public ResumoView resumo(Long jogoId, String username, String urlBase) {
        Convocatoria c = jogos.convocatoria(jogoId, username, urlBase);
        RegistoView r = registo.vista(jogoId, username);
        VotacaoView v = votacao.vista(jogoId, username);
        return new ResumoView(r.presentes(), r.faltas(), v.votaram().size(), c.desistencias().size(),
            "https://wa.me/?text=" + URLEncoder.encode(texto(c, r, v, urlBase + "/jogos/" + jogoId), StandardCharsets.UTF_8)
                .replace("+", "%20"));
    }

    static String texto(Convocatoria c, RegistoView r, VotacaoView v, String link) {
        StringBuilder sb = new StringBuilder();
        sb.append("Resumo do jogo — ").append(c.dia()).append(", ").append(c.hora()).append("\n\n");
        sb.append("Coletes ").append(r.golosColetes()).append(" – ").append(r.golosSemColetes()).append(" Sem coletes\n");
        String marcadores = marcadores(r.marcadoresColetes(), r.marcadoresSemColetes());
        if (!marcadores.isEmpty()) {
            sb.append("Golos: ").append(marcadores).append('\n');
        }
        VotacaoView.Resultado mvp = v.mvpResultado();
        if (mvp != null) {
            sb.append('\n').append(mvp.partilhado() ? "MVP partilhado: " : "MVP: ").append(mvp.vencedoresTexto())
                .append(" (").append(mvp.votos()).append(mvp.votos() == 1 ? " voto)" : " votos)").append('\n');
        }
        for (VotacaoView.Resultado p : v.outrosPremios()) {
            sb.append(p.categoria()).append(": ").append(p.vencedoresTexto()).append('\n');
        }
        sb.append('\n').append(r.presentes()).append(r.presentes() == 1 ? " jogou" : " jogaram");
        if (r.faltas() > 0) {
            sb.append(" · ").append(r.faltas()).append(r.faltas() == 1 ? " falta" : " faltas");
        }
        sb.append("\n\n").append(link);
        return sb.toString();
    }

    private static String marcadores(List<RegistoView.Marcador> coletes, List<RegistoView.Marcador> sem) {
        String a = coletes.stream().map(ResumoService::marcador).collect(Collectors.joining(", "));
        String b = sem.stream().map(ResumoService::marcador).collect(Collectors.joining(", "));
        if (a.isEmpty() || b.isEmpty()) {
            return a + b;
        }
        return a + " · " + b;
    }

    private static String marcador(RegistoView.Marcador m) {
        return m.golos() > 1 ? m.nome() + " " + m.golos() : m.nome();
    }
}
