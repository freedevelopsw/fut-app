package pt.futapp.jogo;

import java.util.List;

/**
 * Vista da convocatória de um jogo para o utilizador autenticado.
 * Nos templates, aceder aos campos com chamada de método, por exemplo {@code conv.inscritos()}.
 */
public record Convocatoria(
    Long jogoId,
    String dia,
    String hora,
    String local,
    int vagas,
    EstadoJogo estado,
    List<Linha> inscritos,
    List<Linha> espera,
    List<Desistencia> desistencias,
    Integer minhaPosicao,
    boolean podeInscrever,
    boolean podeCancelar,
    boolean gestor,
    boolean listaEditavel,
    List<MembroOpcao> membrosDisponiveis,
    String whatsappUrl) {

    public boolean aberta() {
        return estado == EstadoJogo.CONVOCATORIA_ABERTA;
    }

    public int numInscritos() {
        return inscritos.size();
    }

    public int vagasLivres() {
        return Math.max(0, vagas - inscritos.size());
    }

    public int percentagem() {
        return vagas == 0 ? 0 : Math.min(100, inscritos.size() * 100 / vagas);
    }

    public boolean inscrito() {
        return minhaPosicao != null;
    }

    public boolean emEspera() {
        return minhaPosicao != null && minhaPosicao > vagas;
    }

    public int posicaoNaEspera() {
        return minhaPosicao == null ? 0 : minhaPosicao - vagas;
    }

    /** Uma pessoa na lista: {@code posicao} conta a partir de 1 sobre todas as inscrições ativas. */
    public record Linha(Long inscricaoId, Long jogadorId, int posicao, String nome, String iniciais, boolean convidado,
                        String quando, boolean eu) {
    }

    public record Desistencia(String nome, String quando, String registadaPor) {
    }

    public record MembroOpcao(Long id, String nome) {
    }
}
