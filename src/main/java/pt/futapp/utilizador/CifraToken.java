package pt.futapp.utilizador;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Cifra AES-256-GCM para os tokens dos links (convites e redefinição de password).
 *
 * <p>O {@code contexto} entra como dados autenticados: um token gerado para um contexto (por exemplo, um convite)
 * é recusado noutro (por exemplo, a redefinição de password), mesmo com a mesma chave.
 */
final class CifraToken {

    private static final String ALGORITMO = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey chave;
    private final byte[] contexto;
    private final SecureRandom random = new SecureRandom();

    CifraToken(String segredo, String contexto) {
        if (segredo == null || segredo.isBlank()) {
            throw new IllegalStateException("Falta configurar futapp.convite.chave");
        }
        this.chave = derivarChave(segredo);
        this.contexto = contexto.getBytes(StandardCharsets.UTF_8);
    }

    String cifrar(String texto) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cifra = Cipher.getInstance(ALGORITMO);
            cifra.init(Cipher.ENCRYPT_MODE, chave, new GCMParameterSpec(TAG_BITS, iv));
            cifra.updateAAD(contexto);
            byte[] cifrado = cifra.doFinal(texto.getBytes(StandardCharsets.UTF_8));
            byte[] tudo = ByteBuffer.allocate(iv.length + cifrado.length).put(iv).put(cifrado).array();
            return Base64.getUrlEncoder().withoutPadding().encodeToString(tudo);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Não foi possível gerar o link", e);
        }
    }

    /** Decifra um token. Lança exceção se estiver mal formado, adulterado ou for de outro contexto. */
    String decifrar(String token) throws GeneralSecurityException {
        byte[] tudo = Base64.getUrlDecoder().decode(token);
        if (tudo.length <= IV_BYTES) {
            throw new IllegalArgumentException("Token demasiado curto");
        }
        Cipher cifra = Cipher.getInstance(ALGORITMO);
        cifra.init(Cipher.DECRYPT_MODE, chave, new GCMParameterSpec(TAG_BITS, tudo, 0, IV_BYTES));
        cifra.updateAAD(contexto);
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
}
