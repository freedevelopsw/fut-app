package pt.futapp.jogo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import pt.futapp.utilizador.Posicao;

/**
 * Divide os jogadores em duas equipas equilibradas.
 *
 * <ol>
 *   <li>Se houver pelo menos dois guarda-redes (posição principal), os dois melhores ficam um em cada equipa.</li>
 *   <li>Os restantes são distribuídos do melhor para o pior rating, sempre para a equipa com menos jogadores
 *       ou, em empate, com menos rating somado.</li>
 *   <li>Depois tenta trocas entre equipas enquanto o desequilíbrio diminuir: diferença de rating somado
 *       e diferença no número de defesas, médios e avançados.</li>
 * </ol>
 * Há um pouco de aleatoriedade na ordem, para "Gerar de novo" poder dar equipas diferentes com o mesmo equilíbrio.
 * Quem não tem rating conta como {@link #RATING_POR_OMISSAO}.
 */
final class GeradorEquipas {

    static final double RATING_POR_OMISSAO = 5.0;
    private static final double PESO_POSICAO = 0.5;

    record Jogador(Long id, Integer rating, Posicao posicao) {

        double valor() {
            return rating != null ? rating : RATING_POR_OMISSAO;
        }
    }

    private GeradorEquipas() {
    }

    static Map<Long, Equipa> gerar(List<Jogador> jogadores, Random random) {
        Map<Long, Equipa> resultado = new HashMap<>();
        if (jogadores.isEmpty()) {
            return resultado;
        }
        List<Jogador> restantes = new ArrayList<>(jogadores);
        Set<Long> fixos = new HashSet<>();

        // 1. Guarda-redes separados
        List<Jogador> guardaRedes = restantes.stream()
            .filter(j -> j.posicao() == Posicao.GUARDA_REDES)
            .sorted(Comparator.comparingDouble(Jogador::valor).reversed())
            .toList();
        if (guardaRedes.size() >= 2) {
            boolean coletesPrimeiro = random.nextBoolean();
            atribuir(resultado, guardaRedes.get(0), coletesPrimeiro ? Equipa.COLETES : Equipa.SEM_COLETES, fixos);
            atribuir(resultado, guardaRedes.get(1), coletesPrimeiro ? Equipa.SEM_COLETES : Equipa.COLETES, fixos);
            restantes.remove(guardaRedes.get(0));
            restantes.remove(guardaRedes.get(1));
        }

        // 2. Distribuição gulosa, do melhor para o pior (com um pequeno desvio aleatório na ordem)
        Collections.shuffle(restantes, random);
        Map<Long, Double> ordem = new HashMap<>();
        for (Jogador j : restantes) {
            ordem.put(j.id(), j.valor() + (random.nextDouble() - 0.5) * 1.5);
        }
        restantes.sort(Comparator.comparingDouble((Jogador j) -> ordem.get(j.id())).reversed());
        for (Jogador j : restantes) {
            long nColetes = contar(resultado, Equipa.COLETES);
            long nSem = contar(resultado, Equipa.SEM_COLETES);
            Equipa destino;
            if (nColetes != nSem) {
                destino = nColetes < nSem ? Equipa.COLETES : Equipa.SEM_COLETES;
            } else {
                double sColetes = soma(jogadores, resultado, Equipa.COLETES);
                double sSem = soma(jogadores, resultado, Equipa.SEM_COLETES);
                destino = sColetes == sSem ? (random.nextBoolean() ? Equipa.COLETES : Equipa.SEM_COLETES)
                    : (sColetes < sSem ? Equipa.COLETES : Equipa.SEM_COLETES);
            }
            resultado.put(j.id(), destino);
        }

        // 3. Trocas que reduzem o desequilíbrio
        for (int passagem = 0; passagem < 50; passagem++) {
            boolean melhorou = false;
            List<Jogador> ordemTrocas = new ArrayList<>(jogadores);
            Collections.shuffle(ordemTrocas, random);
            for (Jogador a : ordemTrocas) {
                for (Jogador b : ordemTrocas) {
                    if (fixos.contains(a.id()) || fixos.contains(b.id())
                            || resultado.get(a.id()) != Equipa.COLETES || resultado.get(b.id()) != Equipa.SEM_COLETES) {
                        continue;
                    }
                    double antes = custo(jogadores, resultado);
                    resultado.put(a.id(), Equipa.SEM_COLETES);
                    resultado.put(b.id(), Equipa.COLETES);
                    if (custo(jogadores, resultado) < antes - 1e-9) {
                        melhorou = true;
                    } else {
                        resultado.put(a.id(), Equipa.COLETES);
                        resultado.put(b.id(), Equipa.SEM_COLETES);
                    }
                }
            }
            if (!melhorou) {
                break;
            }
        }
        return resultado;
    }

    /** Desequilíbrio entre as duas equipas: diferença de rating somado + diferença de posições de campo. */
    static double custo(List<Jogador> jogadores, Map<Long, Equipa> equipas) {
        double custo = Math.abs(soma(jogadores, equipas, Equipa.COLETES) - soma(jogadores, equipas, Equipa.SEM_COLETES));
        for (Posicao p : new Posicao[] {Posicao.DEFESA, Posicao.MEDIO, Posicao.AVANCADO}) {
            long a = jogadores.stream().filter(j -> j.posicao() == p && equipas.get(j.id()) == Equipa.COLETES).count();
            long b = jogadores.stream().filter(j -> j.posicao() == p && equipas.get(j.id()) == Equipa.SEM_COLETES).count();
            custo += PESO_POSICAO * Math.abs(a - b);
        }
        return custo;
    }

    static double soma(List<Jogador> jogadores, Map<Long, Equipa> equipas, Equipa equipa) {
        return jogadores.stream().filter(j -> equipas.get(j.id()) == equipa).mapToDouble(Jogador::valor).sum();
    }

    private static long contar(Map<Long, Equipa> equipas, Equipa equipa) {
        return equipas.values().stream().filter(e -> e == equipa).count();
    }

    private static void atribuir(Map<Long, Equipa> equipas, Jogador j, Equipa equipa, Set<Long> fixos) {
        equipas.put(j.id(), equipa);
        fixos.add(j.id());
    }
}
