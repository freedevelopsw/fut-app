package pt.futapp.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aceita o link de ligação ao PostgreSQL tal como o Neon (e outros serviços) o mostram,
 * {@code postgresql://utilizador:password@servidor/base?sslmode=require}, e converte-o para as propriedades JDBC
 * do Spring. Um link que já comece por {@code jdbc:} fica como está.
 */
public final class UrlBaseDados {

    private UrlBaseDados() {
    }

    /** Propriedades {@code spring.datasource.*} a partir do link; vazio se o link não precisar de conversão. */
    public static Map<String, String> propriedades(String url) {
        Map<String, String> props = new LinkedHashMap<>();
        if (url == null || url.isBlank()) {
            return props;
        }
        String limpo = url.trim();
        if (!limpo.startsWith("postgres://") && !limpo.startsWith("postgresql://")) {
            return props;
        }
        URI uri = URI.create(limpo.replaceFirst("^postgres(ql)?://", "http://"));
        String porta = uri.getPort() > 0 ? ":" + uri.getPort() : "";
        List<String> parametros = new ArrayList<>();
        if (uri.getRawQuery() != null) {
            for (String p : uri.getRawQuery().split("&")) {
                // channel_binding é do libpq; o driver JDBC não o conhece com este nome
                if (!p.isBlank() && !p.startsWith("channel_binding=")) {
                    parametros.add(p);
                }
            }
        }
        props.put("spring.datasource.url", "jdbc:postgresql://" + uri.getHost() + porta + uri.getRawPath()
            + (parametros.isEmpty() ? "" : "?" + String.join("&", parametros)));
        String info = uri.getRawUserInfo();
        if (info != null) {
            int doisPontos = info.indexOf(':');
            String utilizador = doisPontos >= 0 ? info.substring(0, doisPontos) : info;
            props.put("spring.datasource.username", URLDecoder.decode(utilizador, StandardCharsets.UTF_8));
            if (doisPontos >= 0) {
                props.put("spring.datasource.password", URLDecoder.decode(info.substring(doisPontos + 1), StandardCharsets.UTF_8));
            }
        }
        return props;
    }

    /** Aplica a conversão ao arranque, a partir da variável de ambiente {@code DATABASE_URL}. */
    public static void aplicar() {
        propriedades(System.getenv("DATABASE_URL")).forEach(System::setProperty);
    }
}
