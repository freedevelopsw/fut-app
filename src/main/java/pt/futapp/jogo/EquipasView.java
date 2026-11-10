package pt.futapp.jogo;

import java.util.List;

/** As equipas de um jogo, para o ecrã de equipas (gestão) e para a secção da convocatória (todos). */
public record EquipasView(
    Long jogoId,
    String dia,
    String hora,
    EstadoJogo estado,
    boolean publicadas,
    boolean gestor,
    boolean podeGerir,
    boolean existem,
    EquipaView coletes,
    EquipaView semColetes,
    List<JogadorEquipa> porAtribuir,
    boolean podePublicar,
    String motivoNaoPublicar,
    String whatsappUrl) {

    /** As duas equipas, para percorrer nos templates. */
    public List<EquipaView> lista() {
        return List.of(coletes, semColetes);
    }

    /** Há inscritos sem equipa (por exemplo, alguém subiu da lista de espera depois de as equipas estarem feitas). */
    public boolean desatualizadas() {
        return existem && !porAtribuir.isEmpty();
    }

    public record EquipaView(Equipa equipa, String nome, List<JogadorEquipa> jogadores, Integer media, String posicoes) {

        public int tamanho() {
            return jogadores.size();
        }
    }

    public record JogadorEquipa(Long inscricaoId, Long jogadorId, String nome, String iniciais,
                                String posicaoSigla, Integer rating, boolean convidado) {
    }
}
