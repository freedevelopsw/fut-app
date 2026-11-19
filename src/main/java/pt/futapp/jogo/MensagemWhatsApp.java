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

    static String texto(String dia, String hora, String local, int vagas,
                        List<Convocatoria.Linha> inscritos, List<Convocatoria.Linha> espera, String link) {
        StringBuilder sb = new StringBuilder();
        sb.append("Futebol — ").append(dia).append(", ").append(hora).append('\n');
        if (local != null) { // já normalizado por Jogo.getLocal()
            sb.append("Local: ").append(local).append('\n');
        }
        sb.append('\n');
        sb.append("Inscritos (").append(inscritos.size()).append('/').append(vagas).append("):\n");
        for (Convocatoria.Linha l : inscritos) {
            sb.append(l.posicao()).append(". ").append(l.nome()).append(l.convidado() ? " (convidado)" : "").append('\n');
        }
        if (!espera.isEmpty()) {
            sb.append("\nLista de espera:\n");
            for (Convocatoria.Linha l : espera) {
                sb.append(l.posicao()).append(". ").append(l.nome()).append('\n');
            }
        }
        int livres = vagas - inscritos.size();
        if (livres > 0) {
            sb.append('\n').append(livres == 1 ? "Falta 1 jogador!" : "Faltam " + livres + " jogadores!").append('\n');
        }
        sb.append("\nInscreve-te aqui: ").append(link);
        return sb.toString();
    }

    static String url(String texto) {
        String codificado = URLEncoder.encode(texto, StandardCharsets.UTF_8).replace("+", "%20");
        return "https://wa.me/?text=" + codificado;
    }
}
