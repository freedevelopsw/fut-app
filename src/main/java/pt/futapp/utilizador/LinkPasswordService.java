package pt.futapp.utilizador;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Links para redefinir a password, gerados pelo Admin para um jogador. Nada fica guardado na base de dados.
 *
 * <p>O token é o texto {@code v1|<id do jogador>|<expira em epoch segundos>|<impressão da password>|<admin>}
 * cifrado com AES-256-GCM (mesma chave dos convites, mas outro contexto: um convite não serve aqui).
 * A impressão é um resumo da password atual: quando a password muda, o link deixa de valer. É isso que o torna
 * de uso único, e também o invalida se o próprio jogador mudar a password entretanto.
 */
@Service
public class LinkPasswordService {

    private static final String VERSAO = "v1";

    private final CifraToken cifra;
    private final int validadeHoras;
    private final Clock clock;

    public LinkPasswordService(@Value("${futapp.convite.chave}") String segredo,
                               @Value("${futapp.password.link-validade-horas:24}") int validadeHoras,
                               Clock clock) {
        if (validadeHoras < 1) {
            throw new IllegalStateException("futapp.password.link-validade-horas tem de ser pelo menos 1");
        }
        this.cifra = new CifraToken(segredo, "futapp-redefinir-password");
        this.validadeHoras = validadeHoras;
        this.clock = clock;
    }

    public int getValidadeHoras() {
        return validadeHoras;
    }

    public Link gerar(Long utilizadorId, String passwordHash, String criadoPor) {
        Instant expira = clock.instant().plus(Duration.ofHours(validadeHoras));
        String conteudo = String.join("|", VERSAO, String.valueOf(utilizadorId),
            String.valueOf(expira.getEpochSecond()), impressao(passwordHash), criadoPor);
        return new Link(cifra.cifrar(conteudo), LocalDateTime.ofInstant(expira, clock.getZone()));
    }

    /** Lê um token. Nunca lança exceções: um token mal formado ou adulterado é simplesmente inválido. */
    public Leitura ler(String token) {
        if (token == null || token.isBlank()) {
            return Leitura.de(Estado.INVALIDO);
        }
        String conteudo;
        try {
            conteudo = cifra.decifrar(token.trim());
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return Leitura.de(Estado.INVALIDO);
        }
        String[] partes = conteudo.split("\\|", 5);
        if (partes.length != 5 || !VERSAO.equals(partes[0])) {
            return Leitura.de(Estado.INVALIDO);
        }
        long id;
        Instant expira;
        try {
            id = Long.parseLong(partes[1]);
            expira = Instant.ofEpochSecond(Long.parseLong(partes[2]));
        } catch (NumberFormatException e) {
            return Leitura.de(Estado.INVALIDO);
        }
        Estado estado = clock.instant().isBefore(expira) ? Estado.VALIDO : Estado.EXPIRADO;
        return new Leitura(estado, id, partes[3], partes[4]);
    }

    /** Resumo curto do hash da password. Muda sempre que a password muda. */
    static String impressao(String passwordHash) {
        try {
            byte[] resumo = MessageDigest.getInstance("SHA-256").digest(passwordHash.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(resumo).substring(0, 16);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---------------------------------------------------------------- tipos

    public enum Estado {
        VALIDO, EXPIRADO, INVALIDO
    }

    public record Link(String token, LocalDateTime expiraEm) {
    }

    public record Leitura(Estado estado, Long utilizadorId, String impressao, String criadoPor) {

        static Leitura de(Estado estado) {
            return new Leitura(estado, null, null, null);
        }

        /** O link ainda corresponde a esta password (não foi usado nem a password mudou entretanto). */
        public boolean correspondeA(String passwordHash) {
            return impressao != null && impressao.equals(LinkPasswordService.impressao(passwordHash));
        }
    }
}
