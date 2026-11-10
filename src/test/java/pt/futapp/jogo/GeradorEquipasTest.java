package pt.futapp.jogo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import pt.futapp.utilizador.Posicao;

/** Testes unitários do gerador de equipas (sem Spring). */
class GeradorEquipasTest {

    private static GeradorEquipas.Jogador j(long id, Integer rating, Posicao posicao) {
        return new GeradorEquipas.Jogador(id, rating, posicao);
    }

    private static long tamanho(Map<Long, Equipa> r, Equipa e) {
        return r.values().stream().filter(x -> x == e).count();
    }

    @RepeatedTest(20)
    void todosFicamNumaEquipaETamanhosDiferemNoMaximo1() {
        List<GeradorEquipas.Jogador> jogadores = new ArrayList<>();
        for (long i = 1; i <= 11; i++) {
            jogadores.add(j(i, (int) (i % 10) + 1, null));
        }

        Map<Long, Equipa> r = GeradorEquipas.gerar(jogadores, new Random());

        assertThat(r).hasSize(11);
        assertThat(Math.abs(tamanho(r, Equipa.COLETES) - tamanho(r, Equipa.SEM_COLETES))).isLessThanOrEqualTo(1);
    }

    @RepeatedTest(20)
    void osRatingsFicamEquilibrados() {
        List<GeradorEquipas.Jogador> jogadores = new ArrayList<>();
        int[] ratings = {10, 9, 9, 8, 7, 7, 6, 5, 5, 4, 3, 2};
        for (int i = 0; i < ratings.length; i++) {
            jogadores.add(j((long) i, ratings[i], null));
        }

        Map<Long, Equipa> r = GeradorEquipas.gerar(jogadores, new Random());

        double diferenca = Math.abs(GeradorEquipas.soma(jogadores, r, Equipa.COLETES)
            - GeradorEquipas.soma(jogadores, r, Equipa.SEM_COLETES));
        assertThat(diferenca).isLessThanOrEqualTo(1.0);
        assertThat(tamanho(r, Equipa.COLETES)).isEqualTo(6);
    }

    @RepeatedTest(20)
    void osDoisGuardaRedesFicamEmEquipasDiferentes() {
        List<GeradorEquipas.Jogador> jogadores = new ArrayList<>(List.of(
            j(1L, 8, Posicao.GUARDA_REDES), j(2L, 6, Posicao.GUARDA_REDES),
            j(3L, 7, Posicao.DEFESA), j(4L, 7, Posicao.DEFESA),
            j(5L, 8, Posicao.MEDIO), j(6L, 6, Posicao.MEDIO),
            j(7L, 9, Posicao.AVANCADO), j(8L, 5, Posicao.AVANCADO)));

        Map<Long, Equipa> r = GeradorEquipas.gerar(jogadores, new Random());

        assertThat(r.get(1L)).isNotEqualTo(r.get(2L));
    }

    @RepeatedTest(10)
    void asPosicoesTambemFicamRepartidas() {
        List<GeradorEquipas.Jogador> jogadores = new ArrayList<>(List.of(
            j(1L, 7, Posicao.DEFESA), j(2L, 7, Posicao.DEFESA), j(3L, 7, Posicao.DEFESA), j(4L, 7, Posicao.DEFESA),
            j(5L, 7, Posicao.AVANCADO), j(6L, 7, Posicao.AVANCADO), j(7L, 7, Posicao.AVANCADO), j(8L, 7, Posicao.AVANCADO)));

        Map<Long, Equipa> r = GeradorEquipas.gerar(jogadores, new Random());

        long defesasColetes = jogadores.stream().filter(x -> x.posicao() == Posicao.DEFESA && r.get(x.id()) == Equipa.COLETES).count();
        assertThat(defesasColetes).isEqualTo(2);
    }

    @Test
    void quemNaoTemRatingContaComo5() {
        List<GeradorEquipas.Jogador> jogadores = List.of(j(1L, null, null), j(2L, 5, null));

        Map<Long, Equipa> r = GeradorEquipas.gerar(jogadores, new Random(1));

        assertThat(GeradorEquipas.soma(jogadores, r, Equipa.COLETES)).isEqualTo(5.0);
        assertThat(GeradorEquipas.soma(jogadores, r, Equipa.SEM_COLETES)).isEqualTo(5.0);
    }

    @Test
    void gerarDeNovoPodeDarEquipasDiferentes() {
        List<GeradorEquipas.Jogador> jogadores = new ArrayList<>();
        for (long i = 1; i <= 10; i++) {
            jogadores.add(j(i, 6, null));
        }
        Set<Set<Long>> divisoes = new HashSet<>();
        for (int seed = 0; seed < 10; seed++) {
            Map<Long, Equipa> r = GeradorEquipas.gerar(jogadores, new Random(seed));
            Set<Long> coletes = new HashSet<>();
            r.forEach((id, e) -> { if (e == Equipa.COLETES) coletes.add(id); });
            divisoes.add(coletes);
        }

        assertThat(divisoes.size()).isGreaterThan(1);
    }
}
