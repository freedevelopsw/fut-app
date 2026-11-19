package pt.futapp.web;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.stereotype.Component;

/** Formatação de datas em português de Portugal. Nos templates: {@code ${@fmt.dia(...)}}. */
@Component("fmt")
public class Formatos {

    private static final Locale PT = Locale.forLanguageTag("pt-PT");
    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("EEEE, d MMM", PT);
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm", PT);
    private static final DateTimeFormatter CURTO = DateTimeFormatter.ofPattern("EEE HH:mm", PT);
    private static final DateTimeFormatter MES = DateTimeFormatter.ofPattern("LLLL", PT);
    private static final DateTimeFormatter COMPLETO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", PT);

    /** Por exemplo "Quinta-feira, 12 nov.". */
    public String dia(LocalDateTime data) {
        return data == null ? "" : maiuscula(DIA.format(data));
    }

    /** Por exemplo "Novembro". */
    public String mes(LocalDateTime data) {
        return data == null ? "" : maiuscula(MES.format(data));
    }

    public String hora(LocalDateTime data) {
        return data == null ? "" : HORA.format(data);
    }

    /** Por exemplo "seg. 18:02", para mostrar quando alguém se inscreveu. */
    public String curto(LocalDateTime data) {
        return data == null ? "" : CURTO.format(data);
    }

    public String dataHora(LocalDateTime data) {
        return data == null ? "" : COMPLETO.format(data);
    }

    private static String maiuscula(String texto) {
        return texto.isEmpty() ? texto : Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }
}
