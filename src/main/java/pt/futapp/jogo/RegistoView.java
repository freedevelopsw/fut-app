package pt.futapp.jogo;

import java.util.List;

/** O registo de um jogo: resultado, golos e presenças. */
public record RegistoView(
    Long jogoId,
    String dia,
    String hora,
    EstadoJogo estado,
    boolean gestor,
    boolean podeRegistar,
    boolean podeReabrir,
    boolean mostrarResultado,
    int golosColetes,
    int golosSemColetes,
    List<GoloView> golos,
    List<Marcador> marcadoresColetes,
    List<Marcador> marcadoresSemColetes,
    List<Presenca> coletes,
    List<Presenca> semColetes,
    int faltas,
    String motivoSemRegisto) {

    public List<Presenca> jogadoresDe(Equipa equipa) {
        return equipa == Equipa.COLETES ? coletes : semColetes;
    }

    public int presentes() {
        return coletes.size() + semColetes.size() - faltas;
    }

    /** Golos pela ordem inversa (o mais recente primeiro), com o resultado nesse momento. */
    public record GoloView(Long id, Equipa equipa, String marcador, String assistencia, String parcial) {
    }

    public record Marcador(String nome, long golos) {
    }

    public record Presenca(Long inscricaoId, String nome, Equipa equipa, boolean faltou, boolean convidado) {
    }
}
