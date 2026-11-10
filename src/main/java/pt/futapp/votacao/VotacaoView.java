package pt.futapp.votacao;

import java.util.List;

import pt.futapp.jogo.Equipa;
import pt.futapp.jogo.EstadoJogo;

/** A votação de um jogo para quem está a ver: boletim, quem já votou e (no fim) os resultados. */
public record VotacaoView(
    Long jogoId,
    String dia,
    String hora,
    EstadoJogo estado,
    boolean aberta,
    boolean fechada,
    boolean podeVotar,
    String motivoNaoVotar,
    boolean jaVotei,
    int eleitores,
    List<Eleitor> votaram,
    List<Candidato> candidatos,
    List<Categoria> categorias,
    List<Resultado> resultados,
    boolean gestor,
    boolean admin) {

    public boolean mostrar() {
        return aberta || fechada;
    }

    /** Resultado do MVP, ou null se ninguém votou. */
    public Resultado mvpResultado() {
        return resultados.stream().filter(Resultado::mvp).findFirst().orElse(null);
    }

    public List<Resultado> outrosPremios() {
        return resultados.stream().filter(r -> !r.mvp()).toList();
    }

    public record Eleitor(String nome, String iniciais) {
    }

    public record Candidato(Long inscricaoId, String nome, Equipa equipa, long golos, boolean convidado) {
    }

    /** Uma categoria do boletim: o MVP ({@code premioId} null) ou um prémio. {@code meuVoto} = inscrição em que votei. */
    public record Categoria(Long premioId, String nome, String descricao, Long meuVoto) {

        public boolean mvp() {
            return premioId == null;
        }

        /** Nome do parâmetro no formulário. */
        public String campo() {
            return premioId == null ? "voto_MVP" : "voto_" + premioId;
        }
    }

    /**
     * Resultado de uma categoria. Vários vencedores = prémio partilhado.
     * {@code desempatePorGolos}: no MVP, havia empate nos votos e ganhou quem marcou mais.
     */
    public record Resultado(String categoria, boolean mvp, List<String> vencedores, List<Long> inscricoes,
                            long votos, boolean desempatePorGolos) {

        public boolean partilhado() {
            return vencedores.size() > 1;
        }

        public String vencedoresTexto() {
            return String.join(" e ", vencedores);
        }
    }
}
