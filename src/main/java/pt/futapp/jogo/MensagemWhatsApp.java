package pt.futapp.jogo;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Constrói o link que abre o WhatsApp com a mensagem da convocatória já escrita.
 * A app não publica sozinha no grupo: a pessoa escolhe o grupo e envia.
 */
final class MensagemWhatsApp {

    private MensagemWhatsApp() {
    }

    /**
     * Modelo pedido pelo dono (22/11/2026): os inscritos numerados até ao número de vagas, com as linhas das vagas
     * livres vazias ("9."), para se ver quantas faltam; a lista de espera só quando existe, numerada a partir de 1;
     * no fim, o link para a inscrição.
     */
    static String texto(String dia, String hora, String local, int vagas,
                        List<Convocatoria.Linha> inscritos, List<Convocatoria.Linha> espera, String link) {
        StringBuilder sb = new StringBuilder();
        sb.append("Futebol — ").append(dia).append(", ").append(hora).append('\n');
        if (local != null) { // já normalizado por Jogo.getLocal()
            sb.append("Local: ").append(local).append('\n');
        }
        sb.append('\n');
        sb.append("Inscritos (").append(inscritos.size()).append('/').append(vagas).append("):\n");
        for (int i = 0; i < Math.max(vagas, inscritos.size()); i++) {
            sb.append(i + 1).append('.');
            if (i < inscritos.size()) {
                Convocatoria.Linha l = inscritos.get(i);
                sb.append(' ').append(l.nome()).append(l.convidado() ? " (convidado)" : "");
            }
            sb.append('\n');
        }
        if (!espera.isEmpty()) {
            sb.append("\nLista de espera:\n");
            for (int i = 0; i < espera.size(); i++) {
                Convocatoria.Linha l = espera.get(i);
                sb.append(' ').append(i + 1).append(". ").append(l.nome()).append(l.convidado() ? " (convidado)" : "")
                    .append('\n');
            }
        }
        sb.append("\nInscreve-te aqui: ").append(link);
        return sb.toString();
    }

    static String url(String texto) {
        String codificado = URLEncoder.encode(texto, StandardCharsets.UTF_8).replace("+", "%20");
        return "https://wa.me/?text=" + codificado;
    }
}
