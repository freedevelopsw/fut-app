package pt.futapp.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

/**
 * Ficheiros estáticos com impressão digital no endereço e cache de 7 dias; páginas sem cache.
 * Usa o servidor a sério (porta aleatória), porque é o filtro do servidor que reescreve os endereços.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CacheEstaticosTest {

    @LocalServerPort
    int porta;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> pedir(String caminho) throws Exception {
        var pedido = HttpRequest.newBuilder(URI.create("http://localhost:" + porta + caminho)).GET().build();
        return http.send(pedido, HttpResponse.BodyHandlers.ofString());
    }

    private static String endereco(String pagina, String regex) {
        Matcher m = Pattern.compile(regex).matcher(pagina);
        assertThat(m.find()).as("endereço %s na página", regex).isTrue();
        return m.group(1);
    }

    @Test
    void estaticosTemImpressaoDigitalECacheLonga() throws Exception {
        var login = pedir("/login");
        assertThat(login.statusCode()).isEqualTo(200);
        assertThat(login.headers().firstValue("Cache-Control").orElse("")).contains("no-store");

        String pagina = login.body();
        String[] enderecos = {
            endereco(pagina, "\"(/css/app-[0-9a-f]{32}\\.css)\""),
            endereco(pagina, "\"(/js/carregando-[0-9a-f]{32}\\.js)\""),
            endereco(pagina, "\"(/manifest-[0-9a-f]{32}\\.webmanifest)\""),
        };
        for (String e : enderecos) {
            var r = pedir(e);
            assertThat(r.statusCode()).as(e).isEqualTo(200);
            assertThat(r.headers().firstValue("Cache-Control").orElse(""))
                    .as(e).contains("max-age=604800").contains("public");
        }
    }

    @Test
    void impressaoErradaNaoServeConteudoAntigo() throws Exception {
        assertThat(pedir("/css/app-00000000000000000000000000000000.css").statusCode()).isEqualTo(404);
    }
}
