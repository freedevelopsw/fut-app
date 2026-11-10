package pt.futapp.utilizador;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import pt.futapp.comum.OperacaoInvalidaException;

/**
 * Links de convite para registo, sem nada guardado na base de dados.
 *
 * <p>O token é o texto {@code v1|<expira em epoch segundos>|<username de quem gerou>} cifrado com AES-256-GCM.
 * O GCM garante confidencialidade e integridade: um token alterado, ou cifrado com outra chave, é recusado.
 * O mesmo link pode ser usado várias vezes até expirar. Mudar {@code futapp.convite.chave} invalida todos os links.
 */
@Service
public class ConviteService {

    private static final String VERSAO = "v1";
    private static final String ALGORITMO = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final byte[] CONTEXTO = "futapp-convite".getBytes(StandardCharsets.UTF_8);

    private final SecretKey chave;
    private final int validadeMaxDias;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public ConviteService(@Value("${futapp.convite.chave}") String segredo,
                          @Value("${futapp.convite.validade-max-dias:7}") int validadeMaxDias,
                          Clock clock) {
        if (segredo == null || segredo.isBlank()) {
            throw new IllegalStateException("Falta configurar futapp.convite.chave");
        }
        if (validadeMaxDias < 1) {
            throw new IllegalStateException("futapp.convite.validade-max-dias tem de ser pelo menos 1");
        }
        this.chave = derivarChave(segredo);
        this.validadeMaxDias = validadeMaxDias;
        this.clock = clock;
    }

    public int getValidadeMaxDias() {
        return validadeMaxDias;
    }

    /** Gera um token válido durante {@code dias} dias (entre 1 e o máximo configurado). */
    public Convite gerar(int dias, String criadoPor) {
        if (dias < 1 || dias > validadeMaxDias) {
            throw new OperacaoInvalidaException("A validade tem de ser entre 1 e " + validadeMaxDias + " dias.");
        }
        Instant expira = clock.instant().plus(Duration.ofDays(dias));
        String conteudo = VERSAO + "|" + expira.getEpochSecond() + "|" + criadoPor;
        return new Convite(cifrar(conteudo), LocalDateTime.ofInstant(expira, clock.getZone()), criadoPor);
    }

    /** Valida um token. Nunca lança exceções: um token mal formado ou adulterado é simplesmente inválido. */
    public Validacao validar(String token) {
        if (token == null || token.isBlank()) {
            return Validacao.de(EstadoConvite.AUSENTE);
        }
        String conteudo;
        try {
            conteudo = decifrar(token.trim());
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return Validacao.de(EstadoConvite.INVALIDO);
        }
        String[] partes = conteudo.split("\\|", 3);
        if (partes.length != 3 || !VERSAO.equals(partes[0])) {
            return Validacao.de(EstadoConvite.INVALIDO);
        }
        Instant expira;
        try {
            expira = Instant.ofEpochSecond(Long.parseLong(partes[1]));
        } catch (NumberFormatException e) {
            return Validacao.de(EstadoConvite.INVALIDO);
        }
        LocalDateTime expiraEm = LocalDateTime.ofInstant(expira, clock.getZone());
        EstadoConvite estado = clock.instant().isBefore(expira) ? EstadoConvite.VALIDO : EstadoConvite.EXPIRADO;
        return new Validacao(estado, partes[2], expiraEm);
    }

    // ---------------------------------------------------------------- cifra

    private String cifrar(String texto) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cifra = Cipher.getInstance(ALGORITMO);
            cifra.init(Cipher.ENCRYPT_MODE, chave, new GCMParameterSpec(TAG_BITS, iv));
            cifra.updateAAD(CONTEXTO);
            byte[] cifrado = cifra.doFinal(texto.getBytes(StandardCharsets.UTF_8));
            byte[] tudo = ByteBuffer.allocate(iv.length + cifrado.length).put(iv).put(cifrado).array();
            return Base64.getUrlEncoder().withoutPadding().encodeToString(tudo);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Não foi possível gerar o convite", e);
        }
    }

    private String decifrar(String token) throws GeneralSecurityException {
        byte[] tudo = Base64.getUrlDecoder().decode(token);
        if (tudo.length <= IV_BYTES) {
            throw new IllegalArgumentException("Token demasiado curto");
        }
        Cipher cifra = Cipher.getInstance(ALGORITMO);
        cifra.init(Cipher.DECRYPT_MODE, chave, new GCMParameterSpec(TAG_BITS, tudo, 0, IV_BYTES));
        cifra.updateAAD(CONTEXTO);
        byte[] texto = cifra.doFinal(tudo, IV_BYTES, tudo.length - IV_BYTES);
        return new String(texto, StandardCharsets.UTF_8);
    }

    private static SecretKey derivarChave(String segredo) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(segredo.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(bytes, "AES");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---------------------------------------------------------------- tipos

    public enum EstadoConvite {
        VALIDO, EXPIRADO, INVALIDO, AUSENTE
    }

    public record Convite(String token, LocalDateTime expiraEm, String criadoPor) {
    }

    public record Validacao(EstadoConvite estado, String criadoPor, LocalDateTime expiraEm) {

        static Validacao de(EstadoConvite estado) {
            return new Validacao(estado, null, null);
        }

        public boolean valido() {
            return estado == EstadoConvite.VALIDO;
        }

        /** Mensagem para mostrar a quem abriu um link que não serve. */
        public String mensagem() {
            return switch (estado) {
                case VALIDO -> "";
                case EXPIRADO -> "Este link de convite expirou. Pede um novo ao administrador.";
                case INVALIDO -> "Este link de convite não é válido. Confirma que o copiaste inteiro, ou pede um novo ao administrador.";
                case AUSENTE -> "Para criares conta precisas de um link de convite. Pede-o ao administrador do grupo.";
            };
        }
    }
}
